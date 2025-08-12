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

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/list")
    public ApiResponseDto<List<Report>> getReportsList(
            @RequestHeader("X-Role") String role,
            @RequestHeader("X-Employee-Id") String employeeId
    ) {
        List<Report> reportList = reportService.getReportsByRoleAndEmployeeId(role, employeeId);
        return ApiResponseDto.createOk(reportList);
    }

    @GetMapping("/download/{fileName}")
    public ResponseEntity<?> downloadPdf(
            @PathVariable String fileName,
            @RequestHeader("X-Role") String role
    ) throws IOException {

        List<String> roles = Arrays.asList(role.split(","));

        boolean isAdmin = roles.contains("zone_A")&&roles.contains("zone_B")&&roles.contains("zone_C");

        if(!isAdmin) {
            boolean match = roles.stream().anyMatch(r -> fileName.contains(r.replace("zone_","")));
            if(!match) {
                ApiResponseDto<String> errorBody = ApiResponseDto.createError(ErrorCode.FORBIDDEN.getCode(),ErrorCode.FORBIDDEN.getMessage());

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody);
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
