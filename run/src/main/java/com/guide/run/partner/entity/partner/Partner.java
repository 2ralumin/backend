package com.guide.run.partner.entity.partner;

import com.guide.run.global.converter.LongListConverter;
import com.guide.run.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;


@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(PartnerId.class)
public class Partner extends BaseEntity {
    @Id
    private String viId;
    @Id
    private String guideId;

    @Convert(converter = LongListConverter.class)
    private List<Long> contestIds = new ArrayList<>();
    @Convert(converter = LongListConverter.class)
    private List<Long> trainingIds = new ArrayList<>();

    public void addContest(Long eventId){
        this.contestIds.add(eventId);
    }

    public void addTraining(Long eventId){
        this.trainingIds.add(eventId);
    }

    public void removeContest(Long eventId){
        this.contestIds.remove(eventId);
    }

    public void removeTraining(Long eventId){
        this.trainingIds.remove(eventId);
    }

}
