package com.deefacto.ai_service.Report.controller;

import com.deefacto.ai_service.Report.Entity.Report;
import com.deefacto.ai_service.Report.Service.ReportService;
import com.deefacto.ai_service.common.dto.ApiResponseDto;
import com.deefacto.ai_service.common.exception.ErrorCode;
import com.deefacto.ai_service.common.service.LambdaTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
@Slf4j
public class ReportController {
    private final ReportService reportService;
    private final LambdaTestService lambdaTestService;

    // 리포트 조회
    @GetMapping("/list")
    public ApiResponseDto<Page<Report>> getReportsList(
            @RequestHeader("X-Employee-Id") String employeeId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDate endDate,
            Pageable pageable
            ) {
        List<String> scopes = reportService.makeScopes(employeeId);

        // LocalDate → LocalDateTime 변환
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end = (endDate != null) ? endDate.atTime(23, 59, 59) : null;

        // 조건 검색
        if((type != null && !type.trim().isEmpty()) || startDate != null || endDate != null) {
            return ApiResponseDto.createOk(
                    reportService.serchReports(employeeId,scopes,type,start,end,pageable),
                    "리포트 조회 요청을 성공하였습니다."
            );
        }
        // 전체 조회
        else {
            Page<Report> reportList = reportService.getReportsByRoleAndEmployeeId(scopes, employeeId, pageable);
            return ApiResponseDto.createOk(reportList, "전체 리포트 조회 요청을 성공하였습니디.");
        }
    }


    // 리포트 다운로드
    @GetMapping("/download/{fileName}")
    public ResponseEntity<?> downloadPdf(
            @PathVariable String fileName,
            @RequestHeader("X-Employee-Id") String employeeId
    ) throws IOException {

        List<String> roles = reportService.makeScopes(employeeId);

        boolean isAdmin = reportService.isAdmin(employeeId);

//        if(!isAdmin) {
//            if(!reportService.isDownloadAllowed(roles, employeeId, fileName)) {
//                ApiResponseDto<String> errorBody = ApiResponseDto.createError(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
//                return ResponseEntity.badRequest()
//                        .body(errorBody);
//            }
//        }

        // 검증 통과 시 다운로드 처리
        InputStream fileStream = reportService.downloadFile(fileName);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(fileStream));
    }

    // 리포트 삭제
    @DeleteMapping(value = "/{fileId}")
    public ApiResponseDto<String> deleteFile(
            @RequestHeader("X-Employee-Id") String employeeId,
            @PathVariable Long fileId
    ) {
        reportService.deleteFile(employeeId, fileId);
        return ApiResponseDto.defaultOk();
    }

    // 테스트 리포트 생성 API
    @PostMapping("/test/generate")
    public ApiResponseDto<String> generateTestReport(
            @RequestHeader("X-Employee-Id") String employeeId,
            @RequestBody(required = false) Map<String, Object> requestData
    ) {
        reportService.generateTestReport(requestData);
        return ApiResponseDto.createOk(null, "테스트 리포트 생성이 완료되었습니다.");
    }

    // 월간 리포트 생성 API (테스트용)
    @PostMapping("/monthly/generate")
    public ApiResponseDto<String> generateMonthlyReport(
            @RequestHeader("X-Employee-Id") String employeeId
    ) {
        reportService.generateMonthlyReport();
        return ApiResponseDto.createOk(null, "월간 리포트 생성이 완료되었습니다.");
    }

    // =========================== PDF 생성 API ===========================
    
    /**
     * Bedrock 응답을 PDF로 생성
     */
    @PostMapping("/pdf/generate")
    public ResponseEntity<ApiResponseDto<Report>> generatePdfReport(
            @RequestHeader("X-Employee-Id") String employeeId,
            @RequestParam(defaultValue = "테스트") String reportType
    ) {
        try {
            log.info("PDF 리포트 생성 요청 - 사용자: {}, 타입: {}", employeeId, reportType);
            
            Report pdfReport = reportService.generatePdfReport(employeeId, reportType);
            
            return ResponseEntity.ok(ApiResponseDto.createOk(pdfReport, "PDF 리포트 생성이 완료되었습니다."));
            
        } catch (Exception e) {
            log.error("PDF 리포트 생성 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.createError("PDF_GENERATION_FAILED", "PDF 리포트 생성 실패: " + e.getMessage(), null));
        }
    }
    
    /**
     * 기존 리포트를 PDF로 다운로드
     */
    @GetMapping("/pdf/download/{reportId}")
    public ResponseEntity<?> downloadPdfReport(
            @RequestHeader("X-Employee-Id") String employeeId,
            @PathVariable Long reportId
    ) {
        try {
            log.info("PDF 다운로드 요청 - 사용자: {}, 리포트ID: {}", employeeId, reportId);
            
            byte[] pdfBytes = reportService.convertExistingReportToPdf(reportId, employeeId);
            String fileName = "report_" + reportId + "_" + 
                             java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
                    
        } catch (Exception e) {
            log.error("PDF 다운로드 실패", e);
            ApiResponseDto<String> errorBody = ApiResponseDto.createError("PDF_DOWNLOAD_FAILED", "PDF 다운로드 실패: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
        }
    }

    // =========================== Lambda 테스트 API ===========================
    
    /**
     * Lambda 함수 동기 호출 테스트
     */
    @PostMapping("/lambda/test/sync")
    public ResponseEntity<ApiResponseDto<String>> testLambdaSync(
            @RequestHeader("X-Employee-Id") String employeeId,
            @RequestParam String functionName,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        try {
            log.info("Lambda 동기 호출 테스트 - 사용자: {}, 함수: {}", employeeId, functionName);
            
            if (payload == null) {
                payload = Map.of(
                    "test", true,
                    "message", "Hello from " + employeeId,
                    "timestamp", System.currentTimeMillis()
                );
            }
            
            String result = lambdaTestService.invokeLambdaSync(functionName, payload);
            
            return ResponseEntity.ok(ApiResponseDto.createOk(result, "Lambda 동기 호출 성공"));
            
        } catch (Exception e) {
            log.error("Lambda 동기 호출 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.createError("LAMBDA_SYNC_FAILED", "Lambda 동기 호출 실패: " + e.getMessage()));
        }
    }
    
    /**
     * Lambda 함수 비동기 호출 테스트
     */
    @PostMapping("/lambda/test/async")
    public ResponseEntity<ApiResponseDto<String>> testLambdaAsync(
            @RequestHeader("X-Employee-Id") String employeeId,
            @RequestParam String functionName,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        try {
            log.info("Lambda 비동기 호출 테스트 - 사용자: {}, 함수: {}", employeeId, functionName);
            
            if (payload == null) {
                payload = Map.of(
                    "test", true,
                    "message", "Async call from " + employeeId,
                    "timestamp", System.currentTimeMillis()
                );
            }
            
            lambdaTestService.invokeLambdaAsync(functionName, payload);
            
            return ResponseEntity.ok(ApiResponseDto.createOk("비동기 호출이 완료되었습니다.", "Lambda 비동기 호출 성공"));
            
        } catch (Exception e) {
            log.error("Lambda 비동기 호출 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.createError("LAMBDA_ASYNC_FAILED", "Lambda 비동기 호출 실패: " + e.getMessage()));
        }
    }
    
    /**
     * Lambda 함수 정보 조회
     */
    @GetMapping("/lambda/info/{functionName}")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getLambdaInfo(
            @RequestHeader("X-Employee-Id") String employeeId,
            @PathVariable String functionName
    ) {
        try {
            log.info("Lambda 함수 정보 조회 - 사용자: {}, 함수: {}", employeeId, functionName);
            
            Map<String, Object> info = lambdaTestService.getLambdaFunctionInfo(functionName);
            
            return ResponseEntity.ok(ApiResponseDto.createOk(info, "Lambda 함수 정보 조회 성공"));
            
        } catch (Exception e) {
            log.error("Lambda 함수 정보 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.createError("LAMBDA_INFO_FAILED", "Lambda 함수 정보 조회 실패: " + e.getMessage(), null));
        }
    }
    
    /**
     * Lambda 함수 목록 조회
     */
    @GetMapping("/lambda/list")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> listLambdaFunctions(
            @RequestHeader("X-Employee-Id") String employeeId
    ) {
        try {
            log.info("Lambda 함수 목록 조회 - 사용자: {}", employeeId);
            
            Map<String, Object> functions = lambdaTestService.listLambdaFunctions();
            
            return ResponseEntity.ok(ApiResponseDto.createOk(functions, "Lambda 함수 목록 조회 성공"));
            
        } catch (Exception e) {
            log.error("Lambda 함수 목록 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.createError("LAMBDA_LIST_FAILED", "Lambda 함수 목록 조회 실패: " + e.getMessage(), null));
        }
    }
    
    /**
     * 간단한 Lambda 함수 테스트 (기본 페이로드)
     */
    @PostMapping("/lambda/test/simple/{functionName}")
    public ResponseEntity<ApiResponseDto<String>> testLambdaSimple(
            @RequestHeader("X-Employee-Id") String employeeId,
            @PathVariable String functionName
    ) {
        try {
            log.info("Lambda 간단 테스트 - 사용자: {}, 함수: {}", employeeId, functionName);
            
            String result = lambdaTestService.testLambdaFunction(functionName);
            
            return ResponseEntity.ok(ApiResponseDto.createOk(result, "Lambda 간단 테스트 성공"));
            
        } catch (Exception e) {
            log.error("Lambda 간단 테스트 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.createError("LAMBDA_SIMPLE_TEST_FAILED", "Lambda 간단 테스트 실패: " + e.getMessage()));
        }
    }
}
