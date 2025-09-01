package com.deefacto.ai_service.common.scheduler;

import com.deefacto.ai_service.Recommendation.Service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationScheduler {
    private final RecommendationService recommendationService;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Scheduled(cron = "0 0 0 1 * *")
    public void runMonthlyRecommendation() {
        log.info("매월 임계치 추천 스케쥴 시작");

        // 스케쥴링 시작 날짜로 지난달 추출
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);

        // 지난 달의 시작일과 끝일 추출
        String start = lastMonth.withDayOfMonth(1).format(formatter);
        String end = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()).format(formatter);

        // zoneId 리스트 생성
        List<String> zones = List.of("a","b","c");

        // 각 zone에 대해 임계치 추천 서비스 호출
        for(String zoneId : zones) {
            log.info("임계치 추천 호출: zone={}, start={}, end={}",zoneId, start, end);
            recommendationService.recommendThreshold(zoneId, start, end);
        }
    }
}
