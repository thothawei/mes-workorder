package com.example.mes.batch.service;

import com.example.mes.batch.domain.DailyProductionSummary;
import com.example.mes.batch.repository.DailyProductionSummaryRepository;
import com.example.mes.production.domain.ProductionReport;
import com.example.mes.production.repository.ProductionReportRepository;
import com.example.mes.workorder.domain.WorkOrder;
import com.example.mes.workorder.domain.WorkOrderStatus;
import com.example.mes.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日結批次。
 *
 * <p><b>設計上的第一原則是「可以重跑」。</b>傳產的夜間批次一定會有跑失敗的一天——
 * 資料庫維護、網路中斷、來源系統延遲。一個不能重跑的批次，出事時只能靠手工補資料，
 * 那才是真正的災難。這裡靠 {@code (summary_date, line_code)} 唯一鍵做 upsert，
 * 同一天重跑幾次結果都一樣。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementService {

    /** 兩班制每日可用工時（分鐘） */
    private static final int AVAILABLE_MINUTES_PER_DAY = 960;

    private final ProductionReportRepository reportRepository;
    private final DailyProductionSummaryRepository summaryRepository;
    private final WorkOrderRepository workOrderRepository;

    /**
     * 結算指定日期。
     *
     * @return 本次寫入或更新的產線筆數
     */
    @Transactional
    public SettlementResult settle(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<ProductionReport> reports = reportRepository.findByReportedAtBetween(from, to);
        Map<String, List<ProductionReport>> byLine = reports.stream()
                .collect(Collectors.groupingBy(ProductionReport::getLineCode));

        byLine.forEach((lineCode, lineReports) -> upsertSummary(date, lineCode, lineReports));

        int closed = closeCompletedWorkOrders(to);

        log.info("日結完成 日期={} 產線數={} 報工筆數={} 結案工單={}",
                date, byLine.size(), reports.size(), closed);

        return new SettlementResult(date, byLine.size(), reports.size(), closed);
    }

    private void upsertSummary(LocalDate date, String lineCode, List<ProductionReport> reports) {
        int good = reports.stream().mapToInt(ProductionReport::getGoodQty).sum();
        int defect = reports.stream().mapToInt(ProductionReport::getDefectQty).sum();
        int minutes = reports.stream().mapToInt(ProductionReport::getWorkMinutes).sum();

        int total = good + defect;
        BigDecimal defectRate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(defect).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        BigDecimal utilization = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(AVAILABLE_MINUTES_PER_DAY), 4, RoundingMode.HALF_UP);

        summaryRepository.findBySummaryDateAndLineCode(date, lineCode)
                .ifPresentOrElse(
                        existing -> existing.overwrite(good, defect, minutes, defectRate, utilization, reports.size()),
                        () -> summaryRepository.save(DailyProductionSummary.create(
                                date, lineCode, good, defect, minutes, defectRate, utilization, reports.size()))
                );
    }

    /**
     * 把已完工的工單推進為結案。
     *
     * <p>逐筆呼叫 {@code workOrder.close()} 而不是下一句 UPDATE——
     * 讓狀態機規則對批次同樣生效。批次繞過領域規則直接改狀態，
     * 是傳產系統資料長歪的常見起點。
     */
    private int closeCompletedWorkOrders(LocalDateTime before) {
        List<WorkOrder> completed = workOrderRepository
                .findByStatusAndActualEndBefore(WorkOrderStatus.COMPLETED, before);
        completed.forEach(WorkOrder::close);
        return completed.size();
    }

    public record SettlementResult(LocalDate date, int lineCount, int reportCount, int closedWorkOrders) {
    }
}
