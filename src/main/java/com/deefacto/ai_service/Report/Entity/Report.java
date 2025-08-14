package com.deefacto.ai_service.Report.Entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonTypeId;
import lombok.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    // 파일명 (s3 key)
    @Column(nullable = false, name = "file_name")
    private String fileName;

    // 권한 (zone_A)
    @Column(nullable = false)
    private String role;

    // 생성일자
    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    // 타입 (정기/비정기)
    @Column(nullable = false)
    private String type;

    // 작성자 (비정기일 경우 구분 필요)
    @Column(nullable = true, name = "employee_id")
    private String employeeId;

}
