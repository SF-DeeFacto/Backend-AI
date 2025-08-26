package com.deefacto.ai_service.Report.Service;

import com.deefacto.ai_service.Report.Entity.Report;
import com.deefacto.ai_service.Report.Repository.ReportRepository;
import com.deefacto.ai_service.Report.Repository.ReportSpecs;
import com.deefacto.ai_service.common.exception.CustomException;
import com.deefacto.ai_service.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    private final S3Client s3Client;
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReactiveRedisTemplate<String, String> redisTemplate;


    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    // 리포트 조회 - 전체 조회
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
                .key(fileName)
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
}
