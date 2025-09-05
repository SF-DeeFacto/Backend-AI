package com.deefacto.ai_service.Recommendation.Service;

import com.deefacto.ai_service.Recommendation.domain.AbsoluteThreshold;
import com.deefacto.ai_service.Recommendation.domain.RecommendThresholdDto;
import com.deefacto.ai_service.remote.Service.RecommendThresholdProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {
    final private RecommendThresholdProducer recommendThresholdProducer;
    final private ThresholdBedrockService thresholdBedrockService;
    final private ObjectMapper objectMapper;
    private final Map<String, AbsoluteThreshold> absoluteThresholds = AbsoluteThreshold.defaultThresholds();


    // 임계치 추천 bedrock 호출
    public void recommendThreshold(
            String zoneId
    ) {
        log.info("임계치 추천 작업 시작: zone={}, start={}, end={}", zoneId);
        final int MAX_RETRIES = 2; // 최대 재시도 횟수
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                // 1. Bedrock 호출
                Map<String, Object> sessionAttributes = new HashMap<>();
                sessionAttributes.put("timestamp", LocalDateTime.now().toString());

                String responseJson = thresholdBedrockService.invokeBedrockAgent(zoneId, sessionAttributes);

                if (responseJson.isEmpty()) {
                    log.warn("Bedrock 응답이 비어있습니다. zone={}", zoneId);
                    return;
                }

                // 2. 응답 파싱 및 DTO 변환
                String cleanedJson = responseJson.replaceAll("\\r", "").replaceAll("\\n", "");
                JsonNode rootNode = objectMapper.readTree(cleanedJson);

                JsonNode textNode = rootNode.path("text");
                String rawText = (textNode.isArray()) ?
                        StreamSupport.stream(textNode.spliterator(), false).map(JsonNode::asText).collect(Collectors.joining("\n")) :
                        textNode.asText();

                log.info("Bedrock text 추출 완료: {}", rawText);

                JsonNode dataNode = rootNode.path("data");
                Map<String, Map<String, String>> reasons = thresholdBedrockService.extractReasonBySensorKorean(rawText);
                List<RecommendThresholdDto> resultList = thresholdBedrockService.convertToDto(reasons, dataNode);

                // 3. 임계치 검증
                boolean isValid = validateThresholds(resultList, absoluteThresholds);

                if (isValid) {
                    // 4. 검증 성공 시 Kafka 전송
                    recommendThresholdProducer.requestRecommenThreshold(zoneId, resultList);
                    log.info("Kafka 전송 요청 완료.");
                    return; // 성공했으므로 메서드 종료
                } else {
                    // 5. 검증 실패 시 재시도
                    log.warn("추천 임계치가 절대 임계치 기준을 벗어났습니다. 재시도 횟수: {}/{}", retryCount + 1, MAX_RETRIES);
                    retryCount++;
                    // 재시도 전에 Bedrock 호출 로직에 필요한 추가적인 파라미터가 있다면 여기에 추가
                }
            } catch (Exception e) {
                log.error("데이터 처리 중 오류 발생 (재시도 {}): {}", retryCount + 1, e.getMessage());
                retryCount++;
            }
        }

        // 최대 재시도 횟수 초과 시
        log.error("최대 재시도 횟수({}) 초과. 임계치 추천 실패.", MAX_RETRIES);
    }

    // 추천 임계치와 절대 임계치 비교 로직
    public static boolean validateThresholds(
            List<RecommendThresholdDto> recommendedThresholds,
            Map<String, AbsoluteThreshold> absoluteThresholds
    ) {
        for (RecommendThresholdDto dto : recommendedThresholds) {
            AbsoluteThreshold abs = absoluteThresholds.get(dto.getSensorType());

            if (abs == null) {
                // 절대 임계치 정의 없는 센서 → 스킵 or 실패 처리
                continue;
            }

            Double absAlertLow = abs.getAlertLow();
            Double absAlertHigh = abs.getAlertHigh();

            Double alertLow = dto.getAlertLow();
            Double alertHigh = dto.getAlertHigh();
            Double warningLow = dto.getWarningLow();
            Double warningHigh = dto.getWarningHigh();

            String sensorType = dto.getSensorType();

            // 1️⃣ ESD, Particle 계열은 "High 값"만 체크
            if ("electrostatic".equals(sensorType) || sensorType.startsWith("particle")) {
                if (alertHigh == null) return false;

                // 절대 alertHigh 범위 내에 있어야 함
                if (absAlertHigh != null && alertHigh >= absAlertHigh) return false;

                // warningHigh도 있으면 alertHigh보다 작아야 함
                if (warningHigh != null && warningHigh >= alertHigh) return false;

                continue; // 다음 센서로 넘어감
            }

            // 2️⃣ 일반 센서 (온도, 습도, 풍향 등) → full 순서 검증
            if (alertLow == null || alertHigh == null) return false;

            // alert 값이 절대 alert 범위 내에 있어야 함
            if (absAlertLow != null && alertLow <= absAlertLow) return false;
            if (absAlertHigh != null && alertHigh >= absAlertHigh) return false;

            // warning 값이 반드시 존재해야 하며 정렬 순서 검증
            if (warningLow == null || warningHigh == null) return false;
            if (!(absAlertLow < alertLow &&
                    alertLow < warningLow &&
                    warningLow < warningHigh &&
                    warningHigh < alertHigh &&
                    alertHigh < absAlertHigh)) {
                return false;
            }
        }
        return true;
    }
}
