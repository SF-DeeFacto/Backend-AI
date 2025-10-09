package com.deefacto.ai_service.Recommendation.controller;

import com.deefacto.ai_service.Recommendation.Service.RecommendationService;
import com.deefacto.ai_service.Recommendation.Service.ThresholdBedrockService;
import com.deefacto.ai_service.Recommendation.domain.RecommendThresholdDto;
import com.deefacto.ai_service.remote.Service.RecommendThresholdProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/test/")
public class testController {
    private final RecommendThresholdProducer producer;
    private final RecommendationService recommendationService;
    private final ThresholdBedrockService thresholdBedrockService;
    private ObjectMapper objectMapper;

    @GetMapping(value = "/test")
    public void test() {
        String zoneId = "A01";
        RecommendThresholdDto dto1 = new RecommendThresholdDto();
        dto1.setZoneId("zone-1");
        dto1.setSensorType("temperature");
        dto1.setWarningLow(10.0);
        dto1.setWarningHigh(30.0);
        dto1.setAlertLow(5.0);
        dto1.setAlertHigh(40.0);

        RecommendThresholdDto dto2 = new RecommendThresholdDto();
        dto2.setZoneId("zone-1");
        dto2.setSensorType("humidity");
        dto2.setWarningLow(20.0);
        dto2.setWarningHigh(60.0);
        dto2.setAlertLow(10.0);
        dto2.setAlertHigh(80.0);

        List<RecommendThresholdDto> list = List.of(dto1, dto2);

        // 발행 테스트
        producer.requestRecommenThreshold(zoneId, list);

        log.info("Kafka 발행 요청 완료");
    }

    // bedrock 응답부터 kafka 전송까지 테스트
    @PostMapping(value = "/testBedrockToKafka")
    public void testTotal(
            @RequestBody(required = false) Map<String, Object> request
    ) {
        String zoneId = null;
        if (request != null && request.get("zone") != null) {
            zoneId = String.valueOf(request.get("zone"));
        }

        recommendationService.recommendThreshold(zoneId);
    }

    // 받아둔 답변으로 파싱, 카프카 전송 로직만 테스트
    @PostMapping(value = "/call")
    public void testBedrock(
//            @RequestBody(required = false) Map<String, Object> requestData,
            @RequestBody(required = false) String jsonResponse
    ) {
        // 베드락 호출 시 주석 해제
        // sessionAttribute 생성
//        Map<String, Object> sessionAttributes = new HashMap<>();
//        sessionAttributes.put("timestamp", LocalDateTime.now().toString());
//
//        String zoneId = null;
//        if (requestData != null && requestData.get("zone") != null) {
//            zoneId = String.valueOf(requestData.get("zone"));
//
//            // 베드락 호출 및 답변 확인
//            thresholdBedrockService.invokeBedrockAgent(zoneId, sessionAttributes);

            try {
                if (jsonResponse == null || jsonResponse.isEmpty()) {
                    log.warn("JSON response body is empty or null.");
                    return;
                }

                String cleanedJson = jsonResponse.replaceAll("\\r", "").replaceAll("\\n", "");
                JsonNode rootNode = objectMapper.readTree(cleanedJson);

                JsonNode textNode = rootNode.path("text");
                String rawText;
                if (textNode.isArray()) {
                    rawText = StreamSupport.stream(textNode.spliterator(), false)
                            .map(JsonNode::asText)
                            .collect(Collectors.joining("\n"));
                } else {
                    rawText = textNode.asText();
                }

                log.info("Bedrock text 추출 완료: {}", rawText);

                JsonNode dataNode = rootNode.path("data");
//                Map<String, Map<String, String>> reasons = thresholdBedrockService.extractReasonBySensorEnglish(rawText);
                Map<String, Map<String, String>> reasons = thresholdBedrockService.extractReasonBySensorKorean(textNode);
                log.info("reasons: " + reasons);

                List<RecommendThresholdDto> resultList = thresholdBedrockService.convertToDto(reasons, dataNode);

                // ⚠️ DTO 변환 결과를 개별적으로 로깅하여 값 확인
                log.info("--- DTO 변환 결과 상세 ---");
                for (RecommendThresholdDto dto : resultList) {
                    log.info("DTO 상세: {}", dto.toString());
                }
                log.info("--------------------------");

//                // ⚠️ Kafka 프로듀서 호출
                producer.requestRecommenThreshold("a", resultList);
                log.info("Kafka 전송 요청 완료.");

            } catch (JsonProcessingException e) {
                log.error("JSON 파싱 실패: " + e.getMessage());
            } catch (Exception e) {
                log.error("데이터 처리 중 예기치 않은 오류 발생", e);
            }
        }
    }

