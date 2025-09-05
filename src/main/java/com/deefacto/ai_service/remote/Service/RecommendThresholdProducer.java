package com.deefacto.ai_service.remote.Service;

import com.deefacto.ai_service.Recommendation.domain.RecommendThresholdDto;
import com.deefacto.ai_service.remote.dto.RecommendThresholdMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendThresholdProducer {
    private final KafkaTemplate<String, RecommendThresholdMessage> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void requestRecommenThreshold(
            String zoneId,
            List<RecommendThresholdDto> dto
    ) {
        log.info("kafka request 메소드 실행: "+zoneId);

        RecommendThresholdMessage message = new RecommendThresholdMessage();
        message.setZoneId(zoneId);
        message.setRecommendThresholdDto(dto);
        message.setRecommendedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));


        kafkaTemplate.send("recommend-threshold",zoneId, message);
        log.info("kafka 메시지 전송 성공: {}", message);

    }

}
