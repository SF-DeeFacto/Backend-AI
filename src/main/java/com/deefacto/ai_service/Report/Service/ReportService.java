package com.deefacto.ai_service.Report.Service;

import com.deefacto.ai_service.Report.Entity.Report;
import com.deefacto.ai_service.Report.Repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final S3Client s3Client;
    private final ReportRepository reportRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    public List<Report> getReportsByRoleAndEmployeeId(String role, String employeeId) {
        List<String> roles = Arrays.stream(role.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        boolean isAdmin = roles.contains("zone_A")&&roles.contains("zone_B")&&roles.contains("zone_C");

        if(isAdmin) {
            return reportRepository.findAll();
        } else {
           List<Report> regularReports = reportRepository.findRegularReportsByRoles(roles);
           List<Report> irregularReports = reportRepository.findIrregularReportsByEmployeeId(employeeId);

           List<Report> result = new ArrayList<>();
           result.addAll(regularReports);
           result.addAll(irregularReports);

           return result;
        }

    }

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
}
