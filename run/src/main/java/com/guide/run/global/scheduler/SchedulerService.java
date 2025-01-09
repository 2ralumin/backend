package com.guide.run.global.scheduler;

import com.guide.run.event.entity.Event;
import com.guide.run.event.entity.repository.EventRepository;
import com.guide.run.event.entity.type.EventRecruitStatus;
import com.guide.run.event.entity.type.EventStatus;
import com.guide.run.event.entity.type.EventType;
import com.guide.run.global.scheduler.entity.Schedule;
import com.guide.run.global.scheduler.entity.ScheduleRepository;
import com.guide.run.global.scheduler.entity.ScheduleStatus;
import com.guide.run.partner.entity.matching.Matching;
import com.guide.run.partner.entity.matching.repository.MatchingRepository;
import com.guide.run.partner.entity.partner.Partner;
import com.guide.run.partner.entity.partner.repository.PartnerRepository;
import com.guide.run.temp.member.entity.Attendance;
import com.guide.run.temp.member.repository.AttendanceRepository;
import com.guide.run.user.entity.type.UserType;
import com.guide.run.user.entity.user.User;
import com.guide.run.user.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    @Qualifier("taskScheduler")
    private final ThreadPoolTaskScheduler taskScheduler;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PartnerRepository partnerRepository;
    private final MatchingRepository matchingRepository;

    private final ScheduleRepository scheduleRepository;

    private final AttendanceRepository attendanceRepository;

    private final LockService lockService;

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    @EventListener(ApplicationReadyEvent.class)
    public void initSchedule(){ //스케줄러를 불러옴
        log.info("import Schedule");
        List<Schedule> schedules = scheduleRepository.findAll();
        schedules.forEach(this::addSchedule);
    }

    public void createSchedule(Long eventId){
        //스케줄 생성 코드
        log.info("create Schedule");
        Schedule schedule = scheduleRepository.findByEventId(eventId);
        Event event = eventRepository.findById(eventId).orElse(null);

        ScheduleStatus recruitStatus = ScheduleStatus.PENDING;
        ScheduleStatus eventStatus = ScheduleStatus.PENDING;

        //이벤트 모집 상태, 시작 상태에 따라 변경.
        if(event.getRecruitStatus().equals(EventRecruitStatus.RECRUIT_CLOSE)){
            recruitStatus = ScheduleStatus.END;
        }

        if(event.getRecruitStatus().equals(EventRecruitStatus.RECRUIT_OPEN)){
            recruitStatus = ScheduleStatus.OPEN;
        }

        if(event.getStatus().equals(EventStatus.EVENT_OPEN)){
            recruitStatus = ScheduleStatus.END;
            eventStatus = ScheduleStatus.OPEN;
        }

        if(schedule!=null){
            schedule.changeTime(
                    event.getStartTime(),
                    event.getEndTime(),
                    event.getRecruitStartDate(),
                    event.getRecruitEndDate().plusDays(1),
                    recruitStatus,
                    eventStatus
            );
        }else{
            schedule = Schedule.builder()
                    .eventId(event.getId())
                    .eventStart(event.getStartTime())
                    .eventEnd(event.getEndTime())
                    .recruitStart(event.getRecruitStartDate())
                    //모집 종료는 하루 더해야 함.
                    .recruitEnd(event.getRecruitEndDate().plusDays(1))
                    .eventStatus(eventStatus)
                    .recruitStatus(recruitStatus)
                    .build();
        }

        scheduleRepository.save(schedule);
        addSchedule(schedule);

    }

    //스케줄러 삭제 코드
    @Transactional
    public void deleteSchedule(Long eventId){
        log.info("delete schedule");
        scheduleRepository.deleteAllByEventId(eventId);
    }

    public void addSchedule(Schedule schedule){
        //log.info("add schedule");
        //스케줄 추가 코드
        //스케줄 상태에 따라 다르게 적용

        if(schedule.getRecruitStatus().equals(ScheduleStatus.PENDING)){
            addRecruitStartTask(schedule);
        }
        else if(schedule.getEventStatus().equals(ScheduleStatus.PENDING)){
            addEventStartTask(schedule);
        }

        if(schedule.getRecruitStatus().equals(ScheduleStatus.OPEN)){
            addRecruitEndTask(schedule);
        }

        if(schedule.getEventStatus().equals(ScheduleStatus.OPEN)){
            addEventEndTask(schedule);
        }

        //이벤트 종료 후 파트너, 출석 등 반영
        if(schedule.getEventStatus().equals(ScheduleStatus.END)){
            addEventEndReportTask(schedule);
        }
    }

    private void addRecruitStartTask(Schedule schedule){
        Runnable recruitStartTask = () -> setRecruitStart(schedule);
        taskScheduler.schedule(recruitStartTask, toInstantDate(schedule.getRecruitStart()));
    }

    private void addRecruitEndTask(Schedule schedule){
        Runnable recruitEndTask = () -> setRecruitEnd(schedule);
        taskScheduler.schedule(recruitEndTask, toInstantDate(schedule.getRecruitEnd()));

    }

    private void addEventStartTask(Schedule schedule){
        Runnable eventStartTask = () -> setEventStart(schedule);
        taskScheduler.schedule(eventStartTask, toInstant(schedule.getEventStart()));
    }

    private void addEventEndTask(Schedule schedule){
        Runnable eventEndTask = () -> setEventEnd(schedule);
        taskScheduler.schedule(eventEndTask, toInstant(schedule.getEventEnd()));
    }

    private void addEventEndReportTask(Schedule schedule){
        Runnable eventEndReportTask = () -> setEventEndReport(schedule);
        //이벤트 마감 다음날에 파트너, 출석 반영.
        taskScheduler.schedule(eventEndReportTask, toInstantDate(schedule.getEventEnd().toLocalDate().plusDays(1)));
    }


    @Transactional
    @Async
    public void setEventStart(Schedule schedule){
            log.info("setEventStart");
            String lockName = "eventStartLock-" + schedule.getEventId();
            schedule = scheduleRepository.findById(schedule.getId()).orElse(null);
            if ( schedule!=null && !lockService.isLockActive(lockName)
                    && lockService.acquireLock(lockName, LocalDateTime.now().plusSeconds(59))) {
                try {
                    Event e = eventRepository.findById(schedule.getEventId()).orElse(null);
                    LocalDateTime now = LocalDateTime.now();
                    log.info("lockName="+lockName+", "+e.getStartTime());
                    if(e==null){
                        scheduleRepository.delete(schedule);
                    }
                    else if (e.getStartTime().isBefore(now) || e.getStartTime().isEqual(now)) {
                        //시작시간과 현재 시간 비교해서 시간 같거나 지났으면 진행중/ 이벤트 모집 종료로 변경.
                        e.changeRecruit(EventRecruitStatus.RECRUIT_CLOSE);
                        e.changeStatus(EventStatus.EVENT_OPEN);
                        eventRepository.save(e);
                        schedule.changeEventStatus(ScheduleStatus.OPEN);
                        schedule.changeRecruitStatus(ScheduleStatus.END);
                        addEventEndTask(scheduleRepository.save(schedule));
                    }
                }finally {
                        lockService.releaseLock(lockName);
                }
            }else{
                log.info(lockName);
            }
    }

    @Transactional
    @Async
    public void setEventEnd(Schedule schedule){
        log.info("setEventEnd");
        String lockName = "eventEndLock-" + schedule.getEventId();
        schedule = scheduleRepository.findById(schedule.getId()).orElse(null);
        if ( schedule!=null && !lockService.isLockActive(lockName)
                && lockService.acquireLock(lockName, LocalDateTime.now().plusSeconds(59))) {
            try{
                Event e = eventRepository.findById(schedule.getEventId()).orElse(null);
                LocalDateTime now = LocalDateTime.now();
                log.info("lockName="+lockName+", "+e.getEndTime());
                if(e==null){
                    scheduleRepository.delete(schedule);
                }
                else if ( (e.getEndTime().isBefore(now)  || e.getEndTime().isEqual(now) )) {
                    //종료시간과 현재 시간 비교해서 시간 같거나 지났으면 종료로 변경. + 종료 시 이벤트 모집 상태도 종료로 바꿔줌.
                    e.changeStatus(EventStatus.EVENT_END);
                    e.changeRecruit(EventRecruitStatus.RECRUIT_END);

                    eventRepository.save(e);
                    schedule.changeEventStatus(ScheduleStatus.END);
                    schedule.changeRecruitStatus(ScheduleStatus.END);
                    //파트너, 출석 반영 태스크 등록
                    addEventEndReportTask(scheduleRepository.save(schedule));

                }
            }finally {
                lockService.releaseLock(lockName);
            }
        }else{
            log.info(lockName);
        }
    }

    @Transactional
    @Async
    public void setEventEndReport(Schedule schedule){
        log.info("setEventEndReport");
        String lockName = "eventEndLockReport-" + schedule.getEventId();
        schedule = scheduleRepository.findById(schedule.getId()).orElse(null);
        if ( schedule!=null && !lockService.isLockActive(lockName)
                && lockService.acquireLock(lockName, LocalDateTime.now().plusSeconds(59))) {
            try{
                Event e = eventRepository.findById(schedule.getEventId()).orElse(null);
                LocalDateTime now = LocalDateTime.now();
                log.info("lockName="+lockName+", "+e.getEndTime());
                if(e==null){
                    scheduleRepository.delete(schedule);
                }
                else if ( (e.getEndTime().isBefore(now)  || e.getEndTime().isEqual(now) )) {
                    //종료시간과 현재 시간 비교해서 시간 같거나 지났으면 종료로 변경. + 종료 시 이벤트 모집 상태도 종료로 바꿔줌.
                    e.changeStatus(EventStatus.EVENT_END);
                    e.changeRecruit(EventRecruitStatus.RECRUIT_END);

                    eventRepository.save(e);
                    schedule.changeEventStatus(ScheduleStatus.END);
                    schedule.changeRecruitStatus(ScheduleStatus.END);
                    //파트너, 출석 반영
                    //setEventResult(e);
                    scheduleRepository.delete(schedule);//이벤트 종료 후 스케줄 삭제
                }
            }finally {
                lockService.releaseLock(lockName);
            }
        }else{
            log.info(lockName);
        }
    }

    @Transactional
    @Async
    public void setRecruitStart(Schedule schedule){
        log.info("setRecruitStart");
        String lockName = "recruitStartLock-" + schedule.getEventId();

        schedule = scheduleRepository.findById(schedule.getId()).orElse(null);
        if ( schedule!=null && !lockService.isLockActive(lockName)
                && lockService.acquireLock(lockName, LocalDateTime.now().plusSeconds(59))) {
            try {
                Event e = eventRepository.findById(schedule.getEventId()).orElse(null);
                LocalDate today = LocalDate.now();
                log.info("lockName="+lockName+", "+e.getRecruitStartDate());
                if(e==null){
                    scheduleRepository.delete(schedule);
                }
                else if ( e.getRecruitStartDate().isBefore(today) || e.getRecruitStartDate().isEqual(today)) {
                    //모집 시작일과 현재 일자 비교해서 시간 같거나 지났으면 진행중으로 변경.
                    e.changeRecruit(EventRecruitStatus.RECRUIT_OPEN);
                    schedule.changeRecruitStatus(ScheduleStatus.OPEN);
                    addRecruitEndTask(scheduleRepository.save(schedule));
                }
                eventRepository.save(e);
            } finally {
                lockService.releaseLock(lockName);
            }
        }else{
            log.info(lockName);
        }
    }

    @Transactional
    @Async
    public void setRecruitEnd(Schedule schedule){
        log.info("setRecruitEnd");
        String lockName = "recruitStartLock-" + schedule.getEventId();
        schedule = scheduleRepository.findById(schedule.getId()).orElse(null);
        if (schedule!=null && !lockService.isLockActive(lockName)
                && lockService.acquireLock(lockName, LocalDateTime.now().plusSeconds(59))) {
            try {
                Event e = eventRepository.findById(schedule.getEventId()).orElse(null);
                LocalDate today = LocalDate.now();
                log.info("lockName="+lockName+", "+e.getRecruitEndDate());
                if(e==null){
                    scheduleRepository.delete(schedule);
                }
                else if (e.getRecruitEndDate().isBefore(today)) {
                    //모집 종료일과 현재 일자 비교해서 시간이 지났으면 종료로 변경.
                    e.changeRecruit(EventRecruitStatus.RECRUIT_CLOSE);
                    schedule.changeRecruitStatus(ScheduleStatus.END);
                }
                addEventStartTask(scheduleRepository.save(schedule));
                eventRepository.save(e);
            }finally {
                lockService.releaseLock(lockName);
            }
        }else{
            log.info(lockName);
        }
    }

/*
    //이벤트 파트너, 참여인원 반영
    @Transactional
    public void setEventResult(Event e){
        log.info("setEventResult");
        int viCnt = 0;
        int guideCnt = 0;

        //출석 리스트
        List<Attendance> attendances = attendanceRepository.getAttendanceTrue(e.getId(), true);

        for(Attendance a : attendances){

            //출석한 유저 찾기
            User user = userRepository.findById(a.getPrivateId()).orElse(null);

            if(user!=null) {
                if (user.getType().equals(UserType.VI)) {
                    //참여 vi 수 증가
                    viCnt += 1;
                    setPartnerList(e, user);//파트너 정보 반영

                } else if (user.getType().equals(UserType.GUIDE)) {
                    guideCnt += 1;
                }

                //참여 이벤트 개수 반영
                if (e.getType().equals(EventType.TRAINING)) {
                    user.addTrainingCnt(1);
                } else if (e.getType().equals(EventType.COMPETITION)) {
                    user.addContestCnt(1);
                }

                userRepository.save(user);
            }
        }

        //참여 인원 반영
        e.setCnt(viCnt, guideCnt);
        eventRepository.save(e);
    }


    private void setPartnerList(Event e, User vi){
        log.info("setPartnerList");
        //매칭은 어차피 vi만 찾아서 반영하면 됨.
        List<Matching> matchingList = matchingRepository.findAllByEventIdAndViId(e.getId(), vi.getPrivateId());

        for(Matching m : matchingList){
            User guide = userRepository.findUserByPrivateId(m.getGuideId()).orElse(null);
            if(guide!=null){
                Partner partner = partnerRepository.findByViIdAndGuideId(vi.getPrivateId(),guide.getPrivateId()).orElse(null);
                if(partner !=null){//파트너 정보가 이미 있을 때
                    //log.info("기존 파트너 있음");
                    if(e.getType().equals(EventType.TRAINING)){
                        //log.info("트레이닝 파트너 추가");
                        partner.addTraining(e.getId());
                    }else if(e.getType().equals(EventType.COMPETITION)){
                        //log.info("대회 파트너 추가");
                        partner.addContest(e.getId());
                    }
                    partnerRepository.save(partner);

                }else{//파트너 정보가 없을 때
                    List<Long> contestIds = new ArrayList<>();
                    List<Long> trainingIds = new ArrayList<>();

                    if(e.getType().equals(EventType.COMPETITION)){
                        //log.info("대회 파트너 추가");
                        contestIds.add(e.getId());
                    }else if(e.getType().equals(EventType.TRAINING)){
                        //log.info("트레이닝 파트너 추가");
                        trainingIds.add(e.getId());
                    }
                    partnerRepository.save(
                            Partner.builder()
                                    .viId(vi.getPrivateId())
                                    .guideId(guide.getPrivateId())
                                    .contestIds(contestIds)
                                    .trainingIds(trainingIds)
                                    .build()
                    );
                }
            }

        }
    }

 */

    private Instant toInstant(LocalDateTime localDateTime){
        return localDateTime.atZone(KOREA).toInstant();
    }

    private Instant toInstantDate(LocalDate localDate){
        return localDate.atStartOfDay(KOREA).toInstant();
    }

}
