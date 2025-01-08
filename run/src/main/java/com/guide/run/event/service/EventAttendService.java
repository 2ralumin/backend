package com.guide.run.event.service;

import com.guide.run.event.entity.Event;
import com.guide.run.event.entity.dto.response.attend.AttendCount;
import com.guide.run.event.entity.dto.response.attend.ParticipationCount;
import com.guide.run.event.entity.dto.response.attend.ParticipationInfos;
import com.guide.run.event.entity.repository.EventRepository;
import com.guide.run.event.entity.type.EventType;
import com.guide.run.global.exception.event.resource.NotExistEventException;
import com.guide.run.global.exception.user.resource.NotExistUserException;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventAttendService {
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final MatchingRepository matchingRepository;
    private final PartnerRepository partnerRepository;

    public void requestAttend(Long eventId, String userId) {
        User user = userRepository.findUserByUserId(userId).orElseThrow(NotExistUserException::new);
        Attendance attendance = attendanceRepository.findByEventIdAndPrivateId(eventId, user.getPrivateId());
        if(attendance.isAttend()){
            setAbsenceResult(eventId,user);
            attendanceRepository.save(
                    Attendance.builder()
                            .eventId(eventId)
                            .privateId(user.getPrivateId())
                            .isAttend(false)
                            .date(null)
                            .build()
            );

        }
        else{

            //이벤트 참여 기록 등록
            setAttendResult(eventId, user);
            attendanceRepository.save(
                    Attendance.builder()
                            .eventId(eventId)
                            .privateId(user.getPrivateId())
                            .isAttend(true)
                            .date(LocalDateTime.now())
                            .build()
            );


        }

    }

    public AttendCount getAttendCount(Long eventId) {
        return AttendCount.builder()
                .attend(attendanceRepository.countByIsAttendAndEventId(true,eventId))
                .notAttend(attendanceRepository.countByIsAttendAndEventId(false,eventId))
                .build();
    }

    public ParticipationCount getParticipationCount(Long eventId) {
        Long guideNum = attendanceRepository.countUserType(eventId, UserType.GUIDE);
        Long ViNum = attendanceRepository.countUserType(eventId, UserType.VI);
        return ParticipationCount.builder()
                .count(guideNum+ViNum)
                .vi(ViNum)
                .guide(guideNum)
                .build();
    }

    public ParticipationInfos getParticipationInfos(Long eventId) {
        return ParticipationInfos.builder()
                .attend(attendanceRepository.getParticipationInfo(eventId,true))
                .notAttend(attendanceRepository.getParticipationInfo(eventId,false))
                .build();
    }

    //출석일 때 이벤트 참여인원 반영
    public void setAttendResult(long eventId, User user){
        log.info("이벤트 참여 인원 반영");
        Event e = eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);

        if(user!=null) {
            if (user.getType().equals(UserType.VI)) {
                //참여 vi 수 증가
                e.setViCnt(1);
                setAttendPartner(e, user);//파트너 정보 반영

            } else if (user.getType().equals(UserType.GUIDE)) {
                e.setGuideCnt(1);
            }

            //참여 이벤트 개수 반영
            if (e.getType().equals(EventType.TRAINING)) {
                user.addTrainingCnt(1);
            } else if (e.getType().equals(EventType.COMPETITION)) {
                user.addContestCnt(1);
            }

            userRepository.save(user);
        }
        eventRepository.save(e);
    }


    //참여일 때 이벤트 파트너 리스트 반영
    private void setAttendPartner(Event e, User vi){
        log.info("출석 파트너 리스트 반영");
        //매칭은 vi만 찾아서 반영하면 됨.
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


    private void setAbsenceResult(long eventId, User user){
        log.info("이벤트 미참여 인원 반영");
        Event e = eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);

        if(user!=null) {
            if (user.getType().equals(UserType.VI)) {
                //참여 vi 수 감소
                e.setViCnt(-1);
                setAbsencePartner(e, user);//파트너 정보 반영

            } else if (user.getType().equals(UserType.GUIDE)) {
                e.setGuideCnt(-1);
            }

            //참여 이벤트 개수 반영
            if (e.getType().equals(EventType.TRAINING)) {
                user.addTrainingCnt(-1);
            } else if (e.getType().equals(EventType.COMPETITION)) {
                user.addContestCnt(-1);
            }

            userRepository.save(user);
        }
        eventRepository.save(e);
    }

    //결석으로 수정 시 파트너 반영 내역 삭제
    private void setAbsencePartner(Event e, User vi){
        log.info("setPartnerList");
        //매칭은 vi만 찾아서 반영하면 됨.
        List<Matching> matchingList = matchingRepository.findAllByEventIdAndViId(e.getId(), vi.getPrivateId());

        for(Matching m : matchingList){
            User guide = userRepository.findUserByPrivateId(m.getGuideId()).orElse(null);
            if(guide!=null){
                Partner partner = partnerRepository.findByViIdAndGuideId(vi.getPrivateId(),guide.getPrivateId()).orElse(null);
                if(partner !=null){//파트너 정보가 이미 있을 때
                    //log.info("기존 파트너 있음");
                    if(e.getType().equals(EventType.TRAINING)&&partner.getTrainingIds().contains(e.getId())){
                        //log.info("트레이닝 파트너 추가");
                        partner.removeTraining(e.getId());
                    }else if(e.getType().equals(EventType.COMPETITION) &&partner.getContestIds().contains(e.getId())){
                        //log.info("대회 파트너 추가");
                        partner.removeContest(e.getId());
                    }
                    partnerRepository.save(partner);

                }
            }

        }
    }

}
