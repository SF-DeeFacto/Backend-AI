package com.deefacto.ai_service.Report.Service;

import com.deefacto.ai_service.Report.Entity.Report;
import com.deefacto.ai_service.Report.Repository.ReportRepository;
import com.deefacto.ai_service.Report.Repository.ReportSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final S3Client s3Client;
    private final ReportRepository reportRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    // 리포트 조회 - 전체 조회
    public Page<Report> getReportsByRoleAndEmployeeId(List<String> roles, String employeeId, Pageable pageable) {

        boolean isAdmin = isAdmin(roles);

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
        boolean isAdmin = isAdmin(roles);

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

    // 관리자 확인 로직
    public Boolean isAdmin(List<String> roles) {
        return roles.contains("a")&&roles.contains("b")&&roles.contains("c");
    }

    // role List 생성
    public List<String> makeRoles(String role) {
        return Arrays.stream(role.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    // 다운로드 시도 시 검증
    public boolean isDownloadAllowed(
            List<String> roles,
            String employeeId,
            String fileName
    ) {
        Optional<Report> reportOpt = reportRepository.findByFileName(fileName);

        if (reportOpt.isEmpty()) {
            return false; // 파일 자체가 존재하지 않음
        }

        Report report = reportOpt.get();

        if("정기".equals(report.getType())) {
            return roles.contains(report.getRole());
        } else if("비정기".equals(report.getType())) {
            return employeeId.equals(report.getEmployeeId());
        }

        return false;
    }
}
