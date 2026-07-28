package com.example.mes.production.repository;

import com.example.mes.production.domain.ProductionReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductionReportRepository extends JpaRepository<ProductionReport, Long> {

    Optional<ProductionReport> findByIdempotencyKey(String idempotencyKey);

    Page<ProductionReport> findByWorkOrderIdOrderByReportedAtDesc(Long workOrderId, Pageable pageable);

    List<ProductionReport> findByReportedAtBetween(LocalDateTime from, LocalDateTime to);
}
