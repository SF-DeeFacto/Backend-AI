package com.deefacto.ai_service.Recommendation.Service;

import com.deefacto.ai_service.Recommendation.domain.AbsoluteThreshold;
import com.deefacto.ai_service.Recommendation.domain.SensorThresholdUpdateRequestDto;
import com.deefacto.ai_service.Recommendation.remote.Service.RecommendThresholdProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {
    final private RecommendThresholdProducer recommendThresholdProducer;

    // 임계치 추천 bedrock 호출
    public void recommendateThreshold() {
        log.info("임계치 추천 작업 시작");


    }

    // 추천 임계치와 절대 임계치 비교 로직
    public static boolean validateThresholds(
            List<SensorThresholdUpdateRequestDto> recommendedThresholds,
            Map<String, AbsoluteThreshold> absoluteThresholds
    ) {
        for (SensorThresholdUpdateRequestDto dto : recommendedThresholds) {
            AbsoluteThreshold abs = absoluteThresholds.get(dto.getSensorType());

            if (abs == null) {
                // 절대 임계치 정의가 없는 센서 타입 → 스킵하거나 에러 처리
                continue;
            }

            // 각 값 비교 (null 체크 포함)
            if (!isWithinRange(dto.getWarningLow(), abs.getWarningLow(), abs.getWarningHigh())) return false;
            if (!isWithinRange(dto.getWarningHigh(), abs.getWarningLow(), abs.getWarningHigh())) return false;
            if (!isWithinRange(dto.getAlertLow(), abs.getAlertLow(), abs.getAlertHigh())) return false;
            if (!isWithinRange(dto.getAlertHigh(), abs.getAlertLow(), abs.getAlertHigh())) return false;
        }
        return true;
    }

    // 임계치 값이 범위 내에 있는지 확인
    private static boolean isWithinRange(Double value, Double min, Double max) {
        if (value == null) return true;        // 추천값 없으면 통과
        if (min != null && value < min) return false;
        if (max != null && value > max) return false;
        return true;
    }
}
