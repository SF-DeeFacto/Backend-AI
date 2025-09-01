package com.deefacto.ai_service.remote.Service;


import com.deefacto.ai_service.remote.dto.ReportGeneratedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class ReportProducer {

    private final KafkaTemplate<String, ReportGeneratedEvent> kafkaTemplate;

    public void requestAlimForStore(String zoneId) {
        System.out.println("Report 정기 리포트 생성 후, 저장 로직 시작");
        ReportGeneratedEvent message = new ReportGeneratedEvent();
        message.setZoneId(zoneId);
        message.setTimestamp(OffsetDateTime.now());
        try {
            kafkaTemplate.send("report.generated.v1", message).get();
            System.out.println("Kafka message sent successfully.");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            // 여기서 예외 로그를 확인하고, 원인 분석 가능
        }
    }
}
