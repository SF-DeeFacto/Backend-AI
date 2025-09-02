package com.deefacto.ai_service.common.scheduler;

import com.deefacto.ai_service.Report.Service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private final ReportService reportService;

    /**
     * 매월 1일 오전 8시에 실행되는 스케줄러
     * cron 표현식: 초(0) 분(0) 시(8) 일(1) 월(*) 요일(*)
     */
//    @Scheduled(cron = "0 0 8 1 * *")
    public void generateMonthlyReport() {
        log.info("월간 리포트 생성 스케줄러 시작: {}", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        try {
            // 여기서 월간 리포트 생성 로직을 호출
            reportService.generateMonthlyReport();
            log.info("월간 리포트 생성 완료");
        } catch (Exception e) {
            log.error("월간 리포트 생성 중 오류 발생", e);
        }
    }
}
