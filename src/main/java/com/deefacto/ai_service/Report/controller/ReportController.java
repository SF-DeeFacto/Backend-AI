package com.deefacto.ai_service.Report.controller;

import com.deefacto.ai_service.Report.Service.S3Service;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {
    private final S3Service s3Service;



    @GetMapping("/download/{fileName}")
    public ResponseEntity<?> downloadPdf(
            @PathVariable String fileName,
            @RequestHeader("X-Role") String role
    ) throws IOException {

        String roleKey = role.substring(role.lastIndexOf('_') + 1);

        if (!fileName.contains(roleKey)) {
            ApiResponseDto<String> errorBody = ApiResponseDto.createError(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody);
        }

        // 검증 통과 시 다운로드 처리
        InputStream fileStream = reportService.downloadFile(fileName);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(fileStream));
    }
}
