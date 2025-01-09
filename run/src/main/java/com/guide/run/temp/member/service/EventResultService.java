package com.guide.run.temp.member.service;

import com.guide.run.event.entity.Event;
import com.guide.run.event.entity.repository.EventRepository;
import com.guide.run.event.entity.type.EventType;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventResultService {

    private final AttendanceRepository attendanceRepository;
    private final PartnerRepository partnerRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final MatchingRepository matchingRepository;

    //이벤트 파트너, 참여인원 반영
    @Transactional
    public void setEventResult(long eventId){ //이벤트 참여 인원 반영
        log.info("setEventResult");
        int viCnt = 0;
        int guideCnt = 0;

        Event e = eventRepository.findById(eventId).orElse(null);

        if(e != null){
            //출석 리스트
            List<Attendance> attendances = attendanceRepository.getAttendanceTrue(e.getId(), true);

            for(Attendance a : attendances){

                //출석한 유저 찾기
                User user = userRepository.findById(a.getPrivateId()).orElse(null);
                if (user != null) {
                    setUserEventCnt(user.getPrivateId());
                }

                if(user!=null) {
                    if (user.getType().equals(UserType.VI)) {
                        //참여 vi 수 증가
                        viCnt += 1;
                        setPartnerList(e, user);//파트너 정보 반영

                    } else if (user.getType().equals(UserType.GUIDE)) {
                        guideCnt += 1;
                    }

                }
            }

            //참여 인원 반영
            e.setCnt(viCnt, guideCnt);
            eventRepository.save(e);

        }


    }

    //혹시 이전 출석 기록이 있을 것을 대비해서 수 중복을 막기 위해 해당 유저의 출석 수를 따로 반영
    private void setUserEventCnt(String privateId){
        User user = userRepository.findById(privateId).orElse(null);

        List<Attendance> attendances = attendanceRepository.findAllByPrivateId(privateId);
        int trainingCnt = 0;
        int competitionCnt = 0;
        for(Attendance a : attendances){
            Event event = eventRepository.findById(a.getEventId()).orElse(null);

            if(event != null) {
                if(event.getType().equals(EventType.COMPETITION)){
                    competitionCnt += 1;
                }else if(event.getType().equals(EventType.TRAINING)){
                    trainingCnt += 1;
                }
            }

        }

        if (user != null) {
            user.editUserCnt(trainingCnt, competitionCnt);
        }
        userRepository.save(user);
    }


    private void setPartnerList(Event e, User vi){ //이벤트 파트너 정보 반영
        log.info("setPartnerList");
        //매칭은 어차피 vi만 찾아서 반영하면 됨.
        List<Matching> matchingList = matchingRepository.findAllByEventIdAndViId(e.getId(), vi.getPrivateId());

        for(Matching m : matchingList) {
            User guide = userRepository.findUserByPrivateId(m.getGuideId()).orElse(null);
            if (guide != null) {
                Partner partner = partnerRepository.findByViIdAndGuideId(vi.getPrivateId(), guide.getPrivateId()).orElse(null);
                if (partner != null) {//파트너 정보가 이미 있을 때
                    //log.info("기존 파트너 있음");
                    if (e.getType().equals(EventType.TRAINING) && !partner.getTrainingIds().contains(e.getId())) {
                        //log.info("트레이닝 파트너 추가");
                        partner.addTraining(e.getId());
                    } else if (e.getType().equals(EventType.COMPETITION) && !partner.getContestIds().contains(e.getId())) {
                        //log.info("대회 파트너 추가");
                        partner.addContest(e.getId());
                    }
                    partnerRepository.save(partner);

                } else {//파트너 정보가 없을 때
                    List<Long> contestIds = new ArrayList<>();
                    List<Long> trainingIds = new ArrayList<>();

                    if (e.getType().equals(EventType.COMPETITION)) {
                        //log.info("대회 파트너 추가");
                        contestIds.add(e.getId());
                    } else if (e.getType().equals(EventType.TRAINING)) {
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
}

