package com.example.mes.batch;

import com.example.mes.batch.domain.DailyProductionSummary;
import com.example.mes.batch.repository.DailyProductionSummaryRepository;
import com.example.mes.batch.service.DailySettlementService;
import com.example.mes.batch.service.DailySettlementService.SettlementResult;
import com.example.mes.support.AbstractIntegrationTest;
import com.example.mes.workorder.domain.WorkOrder;
import com.example.mes.workorder.domain.WorkOrderStatus;
import com.example.mes.workorder.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日結批次測試。
 *
 * <p>重點只有一個：**這個批次必須能重跑**。傳產的夜間批次一定會有跑失敗的一天，
 * 不能重跑的批次出事時只能手工補資料，那才是真正的災難。
 */
@DisplayName("日結批次")
class DailySettlementIT extends AbstractIntegrationTest {

    @Autowired DailySettlementService settlementService;
    @Autowired DailyProductionSummaryRepository summaryRepository;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired JdbcTemplate jdbc;

    private LocalDate targetDate;

    @BeforeEach
    void setUp() {
        resetTransactionalData(jdbc);

        targetDate = LocalDate.now().minusDays(1);

        insertReport(targetDate.atTime(9, 0), "LINE-A", 100, 10, 480);
        insertReport(targetDate.atTime(14, 0), "LINE-A", 50, 5, 240);
        insertReport(targetDate.atTime(9, 0), "LINE-B", 80, 0, 300);
        // 區間外的資料，不該被算進來
        insertReport(targetDate.minusDays(1).atTime(9, 0), "LINE-A", 999, 999, 480);
    }

    private void insertReport(LocalDateTime at, String line, int good, int defect, int minutes) {
        jdbc.update("""
                INSERT INTO production_report
                    (work_order_id, order_no, line_code, operator, good_qty, defect_qty,
                     work_minutes, reported_at, idempotency_key, created_at, created_by)
                VALUES ((SELECT id FROM work_order WHERE order_no = 'WO-20260728-001'),
                        'WO-20260728-001', ?, 'operator01', ?, ?, ?, ?, ?, ?, 'TEST')
                """, line, good, defect, minutes, at, "st-" + UUID.randomUUID(), at);
    }

    @Test
    @DisplayName("依產線彙總當日產出，區間外資料不計入")
    void settlesByLine() {
        SettlementResult result = settlementService.settle(targetDate);

        assertThat(result.lineCount()).isEqualTo(2);
        assertThat(result.reportCount()).as("前一天的那筆不可被算進來").isEqualTo(3);

        DailyProductionSummary lineA = summaryRepository
                .findBySummaryDateAndLineCode(targetDate, "LINE-A").orElseThrow();

        assertThat(lineA.getTotalGoodQty()).isEqualTo(150);
        assertThat(lineA.getTotalDefectQty()).isEqualTo(15);
        assertThat(lineA.getTotalWorkMinutes()).isEqualTo(720);
        // 15 / 165 = 0.0909
        assertThat(lineA.getDefectRate()).isEqualByComparingTo("0.0909");
        // 720 / 960 = 0.75
        assertThat(lineA.getUtilizationRate()).isEqualByComparingTo("0.7500");
        assertThat(lineA.getReportCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("重跑同一天：不新增第二筆，數字維持一致")
    void isIdempotent() {
        settlementService.settle(targetDate);
        settlementService.settle(targetDate);
        settlementService.settle(targetDate);

        assertThat(summaryRepository.findBySummaryDateBetweenOrderBySummaryDateAscLineCodeAsc(
                targetDate, targetDate)).hasSize(2);

        DailyProductionSummary lineA = summaryRepository
                .findBySummaryDateAndLineCode(targetDate, "LINE-A").orElseThrow();
        assertThat(lineA.getTotalGoodQty()).as("重跑不可把數字疊加上去").isEqualTo(150);
    }

    @Test
    @DisplayName("補跑時若當日又多了報工，彙總要更新成新的數字")
    void rerunReflectsLateData() {
        settlementService.settle(targetDate);

        insertReport(targetDate.atTime(22, 0), "LINE-A", 30, 0, 120);
        settlementService.settle(targetDate);

        DailyProductionSummary lineA = summaryRepository
                .findBySummaryDateAndLineCode(targetDate, "LINE-A").orElseThrow();
        assertThat(lineA.getTotalGoodQty()).isEqualTo(180);
        assertThat(lineA.getReportCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("已完工的工單在日結後結案")
    void closesCompletedWorkOrders() {
        String orderNo = completedWorkOrder();

        // 結算「今天」，區間上界才會涵蓋剛剛完工的工單
        SettlementResult result = settlementService.settle(LocalDate.now());

        assertThat(result.closedWorkOrders()).isGreaterThanOrEqualTo(1);
        assertThat(workOrderRepository.findByOrderNo(orderNo).orElseThrow().getStatus())
                .isEqualTo(WorkOrderStatus.CLOSED);
    }

    @Test
    @DisplayName("結算昨天時，今天才完工的工單不可被提前結案")
    void doesNotCloseWorkOrdersCompletedAfterTheSettlementDay() {
        String orderNo = completedWorkOrder();

        settlementService.settle(targetDate);

        assertThat(workOrderRepository.findByOrderNo(orderNo).orElseThrow().getStatus())
                .as("日結只能結案該日結束前完工的工單")
                .isEqualTo(WorkOrderStatus.COMPLETED);
    }

    private String completedWorkOrder() {
        String orderNo = "WO-CLOSE-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();
        WorkOrder wo = WorkOrder.create(orderNo, "P-RAIL-100", "滑座", 10, "LINE-A", now, now.plusHours(8));
        wo.release();
        wo.reportProduction(10, 0);
        wo.complete();
        workOrderRepository.saveAndFlush(wo);
        return orderNo;
    }

    @Test
    @DisplayName("當日沒有任何報工時，批次要安靜跑完而不是拋例外")
    void handlesEmptyDay() {
        LocalDate emptyDay = LocalDate.now().minusDays(30);

        SettlementResult result = settlementService.settle(emptyDay);

        assertThat(result.lineCount()).isZero();
        assertThat(result.reportCount()).isZero();
    }
}
