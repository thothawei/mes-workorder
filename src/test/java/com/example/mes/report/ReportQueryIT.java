package com.example.mes.report;

import com.example.mes.report.dto.ReportProjections.DefectPareto;
import com.example.mes.report.dto.ReportProjections.LineDailyOutput;
import com.example.mes.report.dto.ReportProjections.MaterialUsageRank;
import com.example.mes.report.repository.ReportQueryRepository;
import com.example.mes.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 報表原生 SQL 的整合測試。
 *
 * <p>這組測試存在的主因，是原生 SQL 有一種**不會拋例外的失敗方式**：
 * 欄位別名大小寫對不上時，interface projection 會安靜地回傳 null，
 * 報表看起來「跑成功了」但整排是空的。所以每個欄位都要斷言到值，
 * 只斷言「有幾筆」等於沒測。
 */
@DisplayName("報表查詢")
class ReportQueryIT extends AbstractIntegrationTest {

    @Autowired ReportQueryRepository reportRepository;
    @Autowired JdbcTemplate jdbc;

    private LocalDate day1;
    private LocalDate day2;

    @BeforeEach
    void setUp() {
        resetTransactionalData(jdbc);

        day1 = LocalDate.now().minusDays(2);
        day2 = LocalDate.now().minusDays(1);

        // LINE-A 兩天，LINE-B 一天，數字刻意好心算
        insertReport(day1.atTime(9, 0), "LINE-A", 100, 10, "SCRATCH", 480);
        insertReport(day1.atTime(14, 0), "LINE-A", 50, 5, "DIMENSION", 240);
        insertReport(day2.atTime(9, 0), "LINE-A", 200, 20, "SCRATCH", 480);
        insertReport(day2.atTime(9, 0), "LINE-B", 80, 0, null, 300);

        insertConsumption(day2.atTime(9, 0), "M-SEAL-01", "-400.0000");
        insertConsumption(day2.atTime(9, 0), "M-STEEL-01", "-120.0000");
        insertConsumption(day2.atTime(10, 0), "M-SEAL-01", "-100.0000");
    }

    private void insertReport(LocalDateTime at, String line, int good, int defect, String reason, int minutes) {
        jdbc.update("""
                INSERT INTO production_report
                    (work_order_id, order_no, line_code, operator, good_qty, defect_qty,
                     defect_reason, work_minutes, reported_at, idempotency_key, created_at, created_by)
                VALUES ((SELECT id FROM work_order WHERE order_no = 'WO-20260728-001'),
                        'WO-20260728-001', ?, 'operator01', ?, ?, ?, ?, ?, ?, ?, 'TEST')
                """, line, good, defect, reason, minutes, at, "rpt-" + java.util.UUID.randomUUID(), at);
    }

    private void insertConsumption(LocalDateTime at, String materialCode, String qty) {
        jdbc.update("""
                INSERT INTO material_transaction
                    (material_code, warehouse_code, qty, tx_type, balance_after, created_at, created_by)
                VALUES (?, 'WH-RAW', CAST(? AS DECIMAL(18,4)), 'CONSUME', 0, ?, 'TEST')
                """, materialCode, qty, at);
    }

    @Test
    @DisplayName("產線日產出：每個欄位都要有值，不良率與稼動率算對")
    void lineDailyOutput() {
        List<LineDailyOutput> rows = reportRepository.lineDailyOutput(
                day1.atStartOfDay(), day2.plusDays(1).atStartOfDay(), 960);

        assertThat(rows).hasSize(3);

        LineDailyOutput firstDayLineA = rows.get(0);
        assertThat(firstDayLineA.getProductionDate()).isEqualTo(day1);
        assertThat(firstDayLineA.getLineCode()).isEqualTo("LINE-A");
        assertThat(firstDayLineA.getGoodQty()).isEqualTo(150);
        assertThat(firstDayLineA.getDefectQty()).isEqualTo(15);
        // 15 / (150 + 15) = 0.0909
        assertThat(firstDayLineA.getDefectRate()).isEqualByComparingTo("0.0909");
        // (480 + 240) / 960 = 0.75
        assertThat(firstDayLineA.getWorkMinutes()).isEqualTo(720);
        assertThat(firstDayLineA.getUtilizationRate()).isEqualByComparingTo("0.7500");
    }

    @Test
    @DisplayName("窗口函數：同一產線的累計良品要逐日往上疊，跨產線不互相污染")
    void runningTotalPartitionsByLine() {
        List<LineDailyOutput> rows = reportRepository.lineDailyOutput(
                day1.atStartOfDay(), day2.plusDays(1).atStartOfDay(), 960);

        var lineA = rows.stream().filter(r -> r.getLineCode().equals("LINE-A")).toList();
        assertThat(lineA).hasSize(2);
        assertThat(lineA.get(0).getRunningGoodQty()).isEqualTo(150L);
        assertThat(lineA.get(1).getRunningGoodQty()).as("150 + 200").isEqualTo(350L);

        var lineB = rows.stream().filter(r -> r.getLineCode().equals("LINE-B")).toList();
        assertThat(lineB.get(0).getRunningGoodQty())
                .as("LINE-B 的累計不可把 LINE-A 的算進來")
                .isEqualTo(80L);
    }

    @Test
    @DisplayName("柏拉圖：依不良數遞減排序，累計佔比最後一筆必須是 100%")
    void defectPareto() {
        List<DefectPareto> rows = reportRepository.defectPareto(
                day1.atStartOfDay(), day2.plusDays(1).atStartOfDay());

        assertThat(rows).hasSize(2);

        assertThat(rows.get(0).getDefectReason()).isEqualTo("SCRATCH");
        assertThat(rows.get(0).getDefectQty()).isEqualTo(30L);
        // 30 / 35 = 85.71%
        assertThat(rows.get(0).getSharePercent()).isEqualByComparingTo("85.71");
        assertThat(rows.get(0).getCumulativePercent()).isEqualByComparingTo("85.71");

        assertThat(rows.get(1).getDefectReason()).isEqualTo("DIMENSION");
        assertThat(rows.get(1).getCumulativePercent())
                .as("累計佔比的最後一筆必須收斂到 100")
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("物料耗用排行：排名正確，低於安全庫存要標示出來")
    void materialUsageRank() {
        List<MaterialUsageRank> rows = reportRepository.materialUsageRank(
                day1.atStartOfDay(), day2.plusDays(1).atStartOfDay());

        assertThat(rows).hasSize(2);

        MaterialUsageRank top = rows.get(0);
        assertThat(top.getUsageRank()).isEqualTo(1);
        assertThat(top.getMaterialCode()).isEqualTo("M-SEAL-01");
        assertThat(top.getMaterialName()).isEqualTo("端蓋油封組");
        assertThat(top.getUnit()).isEqualTo("SET");
        // 400 + 100，ABS 之後相加
        assertThat(top.getTotalConsumed()).isEqualByComparingTo("500.0000");
        // 種子資料庫存 100 < 安全庫存 300
        assertThat(top.getCurrentStock()).isEqualByComparingTo("100.0000");
        assertThat(top.getSafetyStock()).isEqualByComparingTo("300.0000");
        assertThat(top.getBelowSafety()).as("庫存 100 低於安全庫存 300，必須示警").isTrue();

        MaterialUsageRank second = rows.get(1);
        assertThat(second.getUsageRank()).isEqualTo(2);
        assertThat(second.getMaterialCode()).isEqualTo("M-STEEL-01");
        assertThat(second.getBelowSafety()).as("庫存 2000 高於安全庫存 500").isFalse();
    }

    @Test
    @DisplayName("查詢區間外的資料不可被算進來")
    void respectsDateRange() {
        List<LineDailyOutput> rows = reportRepository.lineDailyOutput(
                day2.atStartOfDay(), day2.plusDays(1).atStartOfDay(), 960);

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r.getProductionDate()).isEqualTo(day2));
    }
}
