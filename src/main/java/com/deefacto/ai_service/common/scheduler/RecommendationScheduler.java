package com.deefacto.ai_service.common.scheduler;

import com.deefacto.ai_service.Recommendation.Service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationScheduler {
    private final RecommendationService recommendationService;

//    @Scheduled(cron = "0 0 0 1 * *")
    public void recommendateThreshold() {

    }
}
