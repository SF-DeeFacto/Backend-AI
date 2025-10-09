package com.deefacto.ai_service.Report.Repository;

import com.deefacto.ai_service.Report.Entity.Report;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

public class ReportSpecs {

    // 타입이 일치하는 조건 생성
    public static Specification<Report> hasType(String type) {
        return (root, query, cb) -> {
            if(type == null || type.trim().isEmpty()) return null;
            return cb.equal(root.get("type"),type);
        };
    }

    // 기간 검색 조건 생성
    public static  Specification<Report> createdBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            if(startDate == null || endDate == null) return null;
            return cb.between(root.get("createdAt"),startDate,endDate);
        };
    }

    // 권한이 일치하는 조건 생성
    public static Specification<Report> hasRole(List<String> roles) {
        return (root, query, cb) -> {
          if(roles == null || roles.isEmpty()) return null;

          // CriteriaBuilder : SQL 쿼리 조건을 프로그래밍으로 만드는 도구
          CriteriaBuilder.In<String> inClause = cb.in(root.get("role"));
          for(String role :roles) {
              inClause.value(role);
          }
          return inClause;
        };
    }

    // 작성자가 일치하는 조건 생성
    public static Specification<Report> hasAuthor(String employeeId) {
        return (root, query, cb) -> {
          if(employeeId == null || employeeId.trim().isEmpty()) return null;
          return cb.equal(root.get("employeeId"), employeeId);
        };
    }

}
