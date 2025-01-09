package com.guide.run.temp.member.controller;

import com.guide.run.temp.member.service.EventResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/temp/")
public class EventResultController {
    private final EventResultService eventResultService;

    @PostMapping("/event-record/{startEventId}/{endEventId}")
    public ResponseEntity<String> recordEventResult(@PathVariable long startEventId, @PathVariable long endEventId) {
        //시작 이벤트와 끝 이벤트를 정해서 그 값들을 돌리기...
        for(long start = startEventId; start <= endEventId; start++) {
            eventResultService.setEventResult(start);
        }
        return ResponseEntity.ok("");
    }
}
