package com.deefacto.ai_service.Report.Service;

import com.deefacto.ai_service.Report.Entity.Report;
import com.deefacto.ai_service.Report.Repository.ReportRepository;
import com.deefacto.ai_service.Report.Repository.ReportSpecs;
import com.deefacto.ai_service.common.exception.CustomException;
import com.deefacto.ai_service.common.exception.ErrorCode;
import com.deefacto.ai_service.common.service.BedrockService;
import com.deefacto.ai_service.common.service.PdfGeneratorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    private final S3Client s3Client;
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final BedrockService bedrockService;
    private final PdfGeneratorService pdfGeneratorService;


    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    // 리포트 조회 - 전체 조회
    @Transactional(readOnly = true)
    public Page<Report> getReportsByRoleAndEmployeeId(List<String> roles, String employeeId, Pageable pageable) {

        boolean isAdmin = isAdmin(employeeId);
        System.out.println(isAdmin);

        if(isAdmin) {
            return reportRepository.findAllReports(pageable);
        } else {

            Specification<Report> spec = (root, query, cb) -> null;
            // 정기/비정기에 따라 role과 작성자 검사 추가
            Specification<Report> regularSpec = ReportSpecs.hasType("정기").and(ReportSpecs.hasRole(roles));
            Specification<Report> irregularSpec = ReportSpecs.hasType("비정기").and(ReportSpecs.hasAuthor(employeeId));

            spec = spec.and(regularSpec.or(irregularSpec));

            // 4) DB 조회 + 페이징
            return reportRepository.findAll(spec, pageable);
        }

    }

    // 리포트 조회 - 검색 필터링
    @Transactional(readOnly = true)
    public Page<Report> serchReports(
            String employeeId,
            List<String> roles,
            String type,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        boolean isAdmin = isAdmin(employeeId);

        // 빈 검색 조건 생성
        Specification<Report> spec = (root, query, cb) -> null;

        // 타입, 기간 조건 추가
        spec = spec.and(ReportSpecs.hasType(type))
                .and(ReportSpecs.createdBetween(startDate,endDate));

        if(isAdmin) {
            // 관리자면 별다른 조건 추가 없음
        } else {

            // 일반 사용자 - 정기 리포트 - 권한 확인
            if("정기".equals(type)) {
                spec = spec.and(ReportSpecs.hasRole(roles));
            }
            // 일반 사용자 - 비정기 리포트 - 작성자 확인
            else if ("비정기".equals(type)) {
                spec = spec.and(ReportSpecs.hasAuthor(employeeId));
            }
        }

        return reportRepository.findAll(spec, pageable);
    }

    // S3에서 해당하는 파일 다운로드
    public InputStream downloadFile(String fileName) throws  IOException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key("reports/pdf/"+fileName)
                .build();

        ResponseInputStream<?> s3ObjectInputStream = s3Client.getObject(getObjectRequest);
        return s3ObjectInputStream;
    }

    // 리포트 삭제
    @Transactional
    public void deleteFile(String employeeId, Long fileId) {
        log.info("fileId:"+fileId);
        // 해당 파일 찾기
        Report report = reportRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // ]삭제 권한 검증 (조회된 정보를 사용)
        if (!"비정기".equals(report.getType()) || !Objects.equals(employeeId, report.getEmployeeId())) {
            // 권한이 없으면 예외 발생 -> @ControllerAdvice가 처리
            log.info("권한이 없습니다");
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        String fileName = report.getFileName();
        log.info("fileName:"+fileName);

        try {
            // S3에서 report 삭제
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

            // report 메타 데이터 삭제
            reportRepository.deleteById(fileId);

            log.info(fileName+"이 성공적으로 삭제되었습니다.");

        } catch (NoSuchKeyException e) {
            // S3에 파일이 이미 없더라도 DB 데이터는 삭제
            reportRepository.delete(report);
            log.warn("S3에 파일이 존재하지 않지만 DB 메타데이터는 삭제했습니다. fileId: {}", fileId);
        } catch (S3Exception e) {
            log.error("S3 파일 삭제 중 오류 발생. AWS 에러 코드: {}", e.awsErrorDetails().errorCode(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public boolean exists(String key) {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        try{
            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false; // 객체 없음
        } catch (Exception e) {
            return false; // S3 에러 시 false 처리
        }
    }

    // Redis 정보 추출 - 관리자 여부 확인
    public Boolean isAdmin(String employeeId) {
        String key = "user:"+ employeeId;
        String value = redisTemplate.opsForValue().get(key).block();
        if(value == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        try {
            JsonNode node = objectMapper.readTree(value);
            String role = node.path("role").asText();
            return role.contains("ROOT") || role.contains("ADMIN");
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    // Redis 정보 추출 - Scopes List 생성
    public List<String> makeScopes(String employeeId) {
        String key = "user:" + employeeId;
        String value = redisTemplate.opsForValue().get(key).block();
        if(value == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        try {
            JsonNode node = objectMapper.readTree(value);
            String scopeStr = node.path("scope").asText();
            return Arrays.stream(scopeStr.split(","))
                    .map(String::trim)
                    .toList();
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    // 다운로드 시도 시 검증
    public boolean isDownloadAllowed(
            List<String> scopes,
            String employeeId,
            String fileName
    ) {
        Optional<Report> reportOpt = reportRepository.findByFileName(fileName);

        if (reportOpt.isEmpty()) {
            return false; // 파일 자체가 존재하지 않음
        }

        Report report = reportOpt.get();

        if("정기".equals(report.getType())) {
            return scopes.contains(report.getRole());
        } else if("비정기".equals(report.getType())) {
            return employeeId.equals(report.getEmployeeId());
        }

        return false;
    }

    // 스케줄러에서 호출할 메서드들
    
    /**
     * 월간 리포트 생성
     */
    @Transactional
    public void generateMonthlyReport() {
        log.info("월간 리포트 생성 작업 시작");
        
        try {
            // 월간 리포트 생성 로직
            LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
            
            // AWS Bedrock을 사용하여 AI 리포트 생성
            String aiGeneratedReport = bedrockService.generateReportSummary("월간");
            log.info("AI 생성 월간 리포트: {}", aiGeneratedReport);
            
            // 실제 리포트 생성 로직을 여기에 구현
            // 예: DB에 저장, 파일 생성 등
            
            log.info("월간 리포트 생성 완료: {}", lastMonth.toLocalDate());
        } catch (Exception e) {
            log.error("월간 리포트 생성 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 테스트 리포트 생성 (AI 기반 - PDF 저장)
     */
    @Transactional
    public void generateTestReport(Map<String, Object> requestData) {
        log.info("테스트 리포트 생성 작업 시작 (PDF 저장) - 요청 데이터: {}", requestData);
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // AWS Bedrock을 사용하여 AI 테스트 리포트 생성
            String aiGeneratedReport = bedrockService.generateReportSummary("정기", requestData);
            log.info("AI 생성 테스트 리포트 길이: {}", aiGeneratedReport.length());
            log.info("AI 생성 테스트 리포트 내용: {}", 
                aiGeneratedReport.length() > 500 ? aiGeneratedReport.substring(0, 500) + "..." : aiGeneratedReport);
            
            // AI 응답이 유효한 경우 PDF 리포트로 저장
            if (aiGeneratedReport != null && !aiGeneratedReport.trim().isEmpty() 
                && !aiGeneratedReport.contains("에이전트 응답을 파싱할 수 없습니다") 
                && !aiGeneratedReport.contains("파싱 실패")) {
                
                // PDF 파일명 생성 (현재 시각 기반)
                String fileName = "Report_" + requestData.get("zone")+" "+now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pdf";
                
                // PDF 생성
                byte[] pdfBytes = pdfGeneratorService.generatePdfFromBedrockResponse(
                    aiGeneratedReport, 
                    "Cleanroom Environment Regular Report",
                    "123"
                );
                
                // S3에 PDF 업로드
                uploadPdfToS3(pdfBytes, fileName);
                
                // 로컬에도 PDF 파일 저장
                pdfGeneratorService.savePdfToFile(pdfBytes, fileName);
                
                // Report 엔티티 생성
                Report testReport = Report.builder()
                    .fileName(fileName)
                    .role("ADMIN") // 관리자 권한
                    .type("정기")
                    .employeeId("123")
                    .createdAt(now)
                    .build();
                
                // 데이터베이스에 저장
                Report savedReport = reportRepository.save(testReport);
                log.info("AI 테스트 리포트 PDF 저장 완료 - ID: {}, 파일명: {}", 
                    savedReport.getId(), savedReport.getFileName());
                log.info("PDF 파일 크기: {} bytes", pdfBytes.length);
                
            } else {
                log.warn("AI 응답이 유효하지 않아 리포트를 저장하지 않습니다: {}", aiGeneratedReport);
            }
            
            log.info("테스트 리포트 PDF 생성 완료: {}", now.toLocalDate());
            
        } catch (Exception e) {
            log.error("테스트 리포트 PDF 생성 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * Bedrock 응답을 PDF로 생성하여 저장
     */
    @Transactional
    public Report generatePdfReport(String employeeId, String reportType) {
        try {
            log.info("PDF 리포트 생성 시작 - 사용자: {}, 타입: {}", employeeId, reportType);
            LocalDateTime now = LocalDateTime.now();

            // Bedrock AI로부터 리포트 내용 생성
            String aiResponse = bedrockService.generateReportSummary(reportType);

            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                throw new RuntimeException("AI 응답이 비어있습니다.");
            }

            // PDF 생성
            String title = String.format("%s 리포트", reportType);
            byte[] pdfBytes = pdfGeneratorService.generatePdfFromBedrockResponse(aiResponse, title, employeeId);

            // PDF 파일명 생성
            String fileName = pdfGeneratorService.generatePdfFileName(reportType, employeeId);

            // S3에 PDF 업로드 (선택사항)
            uploadPdfToS3(pdfBytes, fileName);

            // 로컬에 PDF 저장
            pdfGeneratorService.savePdfToFile(pdfBytes, fileName);

            // Report 엔티티 생성 및 저장
            Report pdfReport = Report.builder()
                    .fileName(fileName)
                    .role(determineUserRole(employeeId))
                    .type(reportType + "_PDF")
                    .employeeId(employeeId)
                    .createdAt(now)
                    .build();

            Report savedReport = reportRepository.save(pdfReport);
            log.info("PDF 리포트 저장 완료 - ID: {}, 파일: {}", savedReport.getId(), fileName);

            return savedReport;

        } catch (Exception e) {
            log.error("PDF 리포트 생성 실패", e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 리포트 타입에 따른 프롬프트 생성
     */
    /**
     * S3에 PDF 업로드
     */
    private void uploadPdfToS3(byte[] pdfBytes, String fileName) {
        try {
            if (bucketName != null && !bucketName.trim().isEmpty()) {
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key("reports/pdf/" + fileName)
                        .contentType("application/pdf")
                        .build();

                s3Client.putObject(putRequest, 
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(pdfBytes));
                
                log.info("PDF 파일 S3 업로드 완료: {}", fileName);
            }
        } catch (Exception e) {
            log.warn("S3 업로드 실패 (로컬 저장은 성공): {}", e.getMessage());
        }
    }

    /**
     * 기존 텍스트 리포트를 PDF로 변환
     */
    public byte[] convertExistingReportToPdf(Long reportId, String employeeId) {
        try {
            log.info("기존 리포트 PDF 변환 시작 - ID: {}, 사용자: {}", reportId, employeeId);

            // 리포트 조회
            Report report = reportRepository.findById(reportId)
                    .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

            // 권한 확인
            if (!hasReportAccess(report, employeeId)) {
                throw new CustomException(ErrorCode.UNAUTHORIZED);
            }

            // 리포트 내용 가져오기 (실제로는 S3나 다른 저장소에서 가져와야 함)
            String content = "기존 리포트 내용입니다.\n\n" + 
                           "파일명: " + report.getFileName() + "\n" +
                           "생성일: " + report.getCreatedAt() + "\n" +
                           "타입: " + report.getType() + "\n\n" +
                           "※ 실제 구현에서는 S3나 파일 시스템에서 내용을 읽어와야 합니다.";

            // PDF 생성
            String title = "리포트 - " + report.getFileName();
            return pdfGeneratorService.generateSimplePdf(content, title, employeeId);

        } catch (Exception e) {
            log.error("기존 리포트 PDF 변환 실패", e);
            throw new RuntimeException("PDF 변환 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 리포트 접근 권한 확인
     */
    private boolean hasReportAccess(Report report, String employeeId) {
        // 관리자이거나 본인이 생성한 리포트인 경우 접근 허용
        return isAdmin(employeeId) || report.getEmployeeId().equals(employeeId);
    }

    /**
     * 사용자 권한 결정
     */
    private String determineUserRole(String employeeId) {
        if (isAdmin(employeeId)) {
            return "ADMIN";
        } else {
            return "USER";
        }
    }
}
