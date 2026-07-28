package com.example.mes.batch.repository;

import com.example.mes.batch.domain.DailyProductionSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyProductionSummaryRepository extends JpaRepository<DailyProductionSummary, Long> {

    Optional<DailyProductionSummary> findBySummaryDateAndLineCode(LocalDate summaryDate, String lineCode);

    List<DailyProductionSummary> findBySummaryDateBetweenOrderBySummaryDateAscLineCodeAsc(LocalDate from, LocalDate to);
}
