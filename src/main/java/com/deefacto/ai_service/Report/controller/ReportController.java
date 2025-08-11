package com.deefacto.ai_service.Report.controller;

import com.deefacto.ai_service.Report.Service.S3Service;
import com.deefacto.ai_service.common.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/report")
public class ReportController {
    private final S3Service s3Service;



    @GetMapping("/download/{fileName}")
    public ResponseEntity<InputStreamResource> downloadPdf(
            @PathVariable String fileName
    ) throws IOException {
        System.out.println("start controller");
        System.out.println("AWS_ACCESS_KEY_ID: " + System.getenv("AWS_ACCESS_KEY_ID"));
        System.out.println("AWS_REGION: " + System.getenv("AWS_REGION"));
        InputStream fileStream = s3Service.downloadFile(fileName);

        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("\\+","%20");

        return  ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+encodedFileName+"\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(fileStream));
    }
}
