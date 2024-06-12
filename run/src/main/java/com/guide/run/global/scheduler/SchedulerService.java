package com.guide.run.global.scheduler;

import com.guide.run.event.entity.Event;
import com.guide.run.event.entity.repository.EventRepository;
import com.guide.run.event.entity.type.EventRecruitStatus;
import com.guide.run.event.entity.type.EventStatus;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;



@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT59M", defaultLockAtLeastFor = "PT59M")
@Service
@RequiredArgsConstructor
public class SchedulerService {
    private final EventRepository eventRepository;

    //1시간마다 실행
    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "eventShedLockJob", lockAtLeastFor = "PT59M", lockAtMostFor = "PT59M")
    public void setEventStatus(){
        //이벤트 불러오기
        List<Event> eventList = eventRepository.getSchedulerEvent();
        LocalDateTime now = LocalDateTime.now();
        for(Event e : eventList){
            if(e.getStartTime().isBefore(now)||e.getStartTime().isEqual(now)){
                //시작시간과 현재 시간 비교해서 시간 같거나 지났으면 진행중/ 이벤트 모집 종료로 변경.
                e.changeRecruit(EventRecruitStatus.RECRUIT_CLOSE);
                e.changeStatus(EventStatus.EVENT_OPEN);
            }
            if(e.getEndTime().isBefore(now)||e.getEndTime().isEqual(now)){
                //종료시간과 현재 시간 비교해서 시간 같거나 지났으면 종료로 변경. + 종료 시 이벤트 모집 상태도 종료로 바꿔줌.
                e.changeStatus(EventStatus.EVENT_END);
                e.changeRecruit(EventRecruitStatus.RECRUIT_END);
            }
            eventRepository.save(e);
        }

        //끝난 이벤트의 매칭 정보도 파트너로 등록해야 함. 또 뭐 등록해야 하더라...

    }

    //자정마다 실행
    @Scheduled(cron="0 0 0 * * *")
    @SchedulerLock(name = "recruitShedLockJob", lockAtLeastFor = "PT59M", lockAtMostFor = "PT59M")
    public void setEventRecruitStatus(){
        //모집 종료/이벤트 종료 빼고 이벤트 불러오기.
        List<Event> eventList = eventRepository.getSchedulerRecruit();
        LocalDate today = LocalDate.now();

        for(Event e : eventList){
            if(e.getRecruitStartDate().isBefore(today)||e.getRecruitStartDate().isEqual(today)){
                //모집 시작일과 현재 일자 비교해서 시간 같거나 지났으면 진행중으로 변경.
                e.changeRecruit(EventRecruitStatus.RECRUIT_OPEN);
            }

            if(e.getRecruitEndDate().isBefore(today)){
                //모집 종료일과 현재 일자 비교해서 시간이 지났으면 종료로 변경.
                e.changeRecruit(EventRecruitStatus.RECRUIT_CLOSE);
            }

            eventRepository.save(e);

        }

    }
}
