package com.guide.run.event.entity.dto.response.attend;

import com.guide.run.user.entity.type.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ParticipationInfo {
    private String userId;
    private UserType type;
    private String applyRecord;
    private String recordDegree;
    private String name;
}
