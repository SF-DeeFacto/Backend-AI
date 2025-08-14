package com.deefacto.ai_service.Report.controller;

import com.deefacto.ai_service.Report.Entity.Report;
import com.deefacto.ai_service.Report.Service.ReportService;
import com.deefacto.ai_service.common.dto.ApiResponseDto;
import com.deefacto.ai_service.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {
    private final ReportService reportService;

    // 리포트 조회
    @GetMapping("/list")
    public ApiResponseDto<Page<Report>> getReportsList(
            @RequestHeader("X-Role") String role,
            @RequestHeader("X-Employee-Id") String employeeId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDate endDate,
            Pageable pageable
            ) {
        List<String> roles = reportService.makeRoles(role);

        // LocalDate → LocalDateTime 변환
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end = (endDate != null) ? endDate.atTime(23, 59, 59) : null;

        // 조건 검색
        if((type != null && !type.trim().isEmpty()) || startDate != null || endDate != null) {
            return ApiResponseDto.createOk(
                    reportService.serchReports(employeeId,roles,type,start,end,pageable)
            );
        }
        // 전체 조회
        else {
            Page<Report> reportList = reportService.getReportsByRoleAndEmployeeId(roles, employeeId, pageable);
            return ApiResponseDto.createOk(reportList);
        }
    }


    @GetMapping("/download/{fileName}")
    public ResponseEntity<?> downloadPdf(
            @PathVariable String fileName,
            @RequestHeader("X-Role") String role,
            @RequestHeader("X-Employee-Id") String employeeId
    ) throws IOException {

        List<String> roles = reportService.makeRoles(role);

        boolean isAdmin = reportService.isAdmin(roles);

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
}
