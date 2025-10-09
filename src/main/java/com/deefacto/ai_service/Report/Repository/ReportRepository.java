package com.deefacto.ai_service.Report.Repository;

import com.deefacto.ai_service.Report.Entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository
        extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {
    // 관리자용 조회
    @Query("SELECT r FROM Report r")
    Page<Report> findAllReports(Pageable pageable);

    Optional<Report> findByFileName(String fileName);
}
