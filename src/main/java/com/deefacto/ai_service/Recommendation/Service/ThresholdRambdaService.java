package com.deefacto.ai_service.Recommendation.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ThresholdRambdaService {
    private final RecommendationService recommendationService;

    // 초 분 시 일 월 요일
    @Scheduled(cron = "0 0 0  1 * ")
    public void recommendateThreshold() {
        log.info("임계치 추천 스케쥴링 시작: {}", LocalDateTime.now());

    }
}
