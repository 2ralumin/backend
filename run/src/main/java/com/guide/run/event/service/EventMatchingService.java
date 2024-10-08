package com.guide.run.event.service;

import com.guide.run.event.entity.dto.response.match.*;
import com.guide.run.event.entity.repository.EventRepository;
import com.guide.run.global.exception.event.resource.NotExistEventException;
import com.guide.run.global.exception.user.resource.NotExistUserException;
import com.guide.run.partner.entity.matching.Matching;
import com.guide.run.partner.entity.matching.UnMatching;
import com.guide.run.partner.entity.matching.repository.MatchingRepository;
import com.guide.run.partner.entity.matching.repository.UnMatchingRepository;
import com.guide.run.user.entity.type.UserType;
import com.guide.run.user.entity.user.User;
import com.guide.run.user.repository.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventMatchingService {
    private final UnMatchingRepository unMatchingRepository;
    private final MatchingRepository matchingRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    @Transactional
    public void matchUser(Long eventId, String viId, String userId) {
        eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);
        User vi = userRepository.findUserByUserId(viId).orElseThrow(NotExistUserException::new);
        User guide = userRepository.findUserByUserId(userId).orElseThrow(NotExistUserException::new);
        Matching existGuide = matchingRepository.findByEventIdAndGuideId(eventId, guide.getPrivateId());
        if(existGuide!=null){
            matchingRepository.delete(existGuide);
            long countMatchedGuideForVi = matchingRepository.countByEventIdAndViId(eventId, existGuide.getViId());
            if(countMatchedGuideForVi==0){
                User unMatchingVi = userRepository.findUserByPrivateId(existGuide.getViId()).orElseThrow(NotExistUserException::new);
                unMatchingRepository.save(
                        UnMatching.builder()
                                .eventId(eventId)
                                .privateId(unMatchingVi.getPrivateId())
                                .build()
                );
            }
        }
        matchingRepository.save(
                Matching.builder()
                        .eventId(eventId)
                        .guideId(guide.getPrivateId())
                        .viId(vi.getPrivateId())
                        .viRecord(vi.getRecordDegree())
                        .guideRecord(guide.getRecordDegree())
                        .build()
        );
        unMatchingRepository.delete(
                UnMatching.builder()
                        .eventId(eventId)
                        .privateId(guide.getPrivateId())
                        .build()
        );
        Optional<UnMatching> findVi = unMatchingRepository.findByPrivateIdAndEventId(vi.getPrivateId(),eventId);
        if(!findVi.isEmpty()){
            unMatchingRepository.delete(findVi.get());
        }

    }

    @Transactional
    public void deleteMatchUser(Long eventId, String userId) {
        eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);
        User user = userRepository.findUserByUserId(userId).orElseThrow(NotExistUserException::new);
        String privateId = user.getPrivateId();
        if(user.getType()==UserType.VI){
            List<Matching> allMatching = matchingRepository.findAllByEventIdAndViId(eventId, privateId);
            for(Matching m : allMatching){
                matchingRepository.delete(m);
                unMatchingRepository.save(
                        UnMatching.builder()
                                .privateId(m.getGuideId())
                                .eventId(eventId)
                                .build()
                );
            }

            unMatchingRepository.save(
                    UnMatching.builder()
                            .privateId(user.getPrivateId())
                            .eventId(eventId)
                            .build()
            );
        }else{
            Matching m = matchingRepository.findByEventIdAndGuideId(eventId, privateId);
            matchingRepository.delete(m);
            unMatchingRepository.save(
                    UnMatching.builder()
                            .privateId(m.getGuideId())
                            .eventId(eventId)
                            .build()
            );
            matchingRepository.flush();

            if(matchingRepository.findAllByEventIdAndViId(eventId,m.getViId()).size()==0){
                unMatchingRepository.save(
                        UnMatching.builder()
                                .eventId(eventId)
                                .privateId(m.getViId())
                                .build()
                );
            }
        }
    }

    public UserTypeCount getUserTypeCount(Long eventId) {
        eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);
        return UserTypeCount.builder()
                .vi(unMatchingRepository.getUserTypeCount(eventId, UserType.VI))
                .guide(unMatchingRepository.getUserTypeCount(eventId,UserType.GUIDE))
                .build();
    }

    public List<NotMatchUserInfo> getNotMatchList(Long eventId) {
        eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);
        return unMatchingRepository.findNotMatchUserInfos(eventId);
    }

    public MatchedGuideCount getMatchedGuideCount(Long eventId, String viId) {
        eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);
        User vi = userRepository.findUserByUserId(viId).orElseThrow(NotExistUserException::new);
        return MatchedGuideCount.builder()
                .guide(matchingRepository.findAllByEventIdAndViId(eventId,vi.getPrivateId()).size()).build();
    }

    public MatchedGuideList getMatchedGuideList(Long eventId, String viId) {
        eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);
        User vi = userRepository.findUserByUserId(viId).orElseThrow(NotExistUserException::new);
        return MatchedGuideList.builder()
                .guide(matchingRepository.findAllMatchedGuideByEventIdAndViId(eventId,vi.getPrivateId()))
                .build();
    }

    public MatchedViCount getMatchedViCount(Long eventId) {
        eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);
        return MatchedViCount.builder()
                .vi(matchingRepository.findAllMatchedViByEventIdAndUserType(eventId,UserType.VI).size())
                .build();
    }

    public MatchedViList getMatchedViList(Long eventId) {
        eventRepository.findById(eventId).orElseThrow(NotExistEventException::new);
        return MatchedViList.builder()
                .vi(matchingRepository.findAllMatchedViByEventIdAndUserType(eventId,UserType.VI))
                .build();
    }
}
