package com.deefacto.ai_service.Recommendation.controller;

import com.deefacto.ai_service.Recommendation.domain.SensorThresholdUpdateRequestDto;
import com.deefacto.ai_service.Recommendation.remote.Service.RecommendThresholdProducer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@Slf4j
public class testController {
    private final RecommendThresholdProducer producer;

    @GetMapping(value = "/test")
    public void test() {
        String zoneId = "A01";
        SensorThresholdUpdateRequestDto dto1 = new SensorThresholdUpdateRequestDto();
        dto1.setZoneId("zone-1");
        dto1.setSensorType("temperature");
        dto1.setWarningLow(10.0);
        dto1.setWarningHigh(30.0);
        dto1.setAlertLow(5.0);
        dto1.setAlertHigh(40.0);

        SensorThresholdUpdateRequestDto dto2 = new SensorThresholdUpdateRequestDto();
        dto2.setZoneId("zone-1");
        dto2.setSensorType("humidity");
        dto2.setWarningLow(20.0);
        dto2.setWarningHigh(60.0);
        dto2.setAlertLow(10.0);
        dto2.setAlertHigh(80.0);

        List<SensorThresholdUpdateRequestDto> list = List.of(dto1, dto2);

        // 발행 테스트
        producer.requestRecommenThreshold(zoneId, list);

        log.info("Kafka 발행 요청 완료");
    }
}
