package com.deefacto.ai_service.Report.Repository;

import com.deefacto.ai_service.Report.Entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByRole(String role);
}
