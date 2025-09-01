package com.deefacto.ai_service.remote.Service;

import com.deefacto.ai_service.Recommendation.domain.SensorThresholdUpdateRequestDto;
import com.deefacto.ai_service.remote.dto.RecommendThresholdMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendThresholdProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void requestRecommenThreshold(
            String zoneId,
            List<SensorThresholdUpdateRequestDto> dto
    ) {
        log.info("kafka request 메소드 실행: "+zoneId);

        RecommendThresholdMessage message = new RecommendThresholdMessage();
        message.setZoneId(zoneId);
        message.setSensorThresholdUpdateRequestDto(dto);
        message.setRecommendedAt(LocalDateTime.now());


        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send("recommend-threshold",zoneId, payload);
            log.info("kafka 메시지 전송 성공: {}", payload);
        } catch (JsonProcessingException e) {
            log.error("kafka 메시지 직렬화 실패", e);
        }

    }

}
