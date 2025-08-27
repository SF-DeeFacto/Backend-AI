package com.deefacto.ai_service.Report.controller;

import com.deefacto.ai_service.Report.Entity.Report;
import com.deefacto.ai_service.Report.Service.ReportService;
import com.deefacto.ai_service.common.dto.ApiResponseDto;
import com.deefacto.ai_service.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {
    private final ReportService reportService;

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

        if(!isAdmin) {
            if(!reportService.isDownloadAllowed(roles, employeeId, fileName)) {
                ApiResponseDto<String> errorBody = ApiResponseDto.createError(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
                return ResponseEntity.badRequest()
                        .body(errorBody);
            }
        }

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
            @RequestHeader("X-Employee-Id") String employeeId
    ) {
        reportService.generateTestReport();
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
}
