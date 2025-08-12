package com.deefacto.ai_service.Report.Repository;

import com.deefacto.ai_service.Report.Entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    // 관리자용 조회
    @Query("SELECT r FROM Report r")
    List<Report> findAllReports();

    // 일반 사용자 - 정기 리포트 조회
    @Query("SELECT r FROM Report r WHERE r.type = '정기' AND r.role IN :roles")
    List<Report> findRegularReportsByRoles(@Param("roles") List<String> roles);

    // 일반 사용자 - 비정기 리포트 조회
    @Query("SELECT r FROM Report r WHERE r.type='비정기' AND r.employee_id= :employeeId")
    List<Report> findIrregularReportsByEmployeeId(@Param("employeeId") String employeeId);
}
