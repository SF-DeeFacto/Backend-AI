package com.deefacto.ai_service.Report.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.deefacto.ai_service.Report.Entity.Report;
import com.deefacto.ai_service.Report.Repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

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
    private final AmazonS3 amazonS3;
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
        S3Object s3Object = amazonS3.getObject(bucketName, fileName);
        return s3Object.getObjectContent();
    }

    public boolean exists(String key) {
        try{
            return amazonS3.doesObjectExist(bucketName, key);
        } catch (Exception e) {
            return false; // S3 에러 시 false 처리
        }
    }
}
