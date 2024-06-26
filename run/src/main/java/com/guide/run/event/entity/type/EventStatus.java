package com.guide.run.event.entity.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum EventStatus {
    EVENT_UPCOMING("이벤트 시작 전"),
    EVENT_OPEN("이벤트 진행중"),
    EVENT_END("이벤트종료");
    private final String value;
}
