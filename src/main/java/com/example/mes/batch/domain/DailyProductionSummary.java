package com.example.mes.batch.domain;

import com.example.mes.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日結彙總。
 *
 * <p>把「每次查報表都要掃明細」變成「查一張已算好的小表」。
 * 明細表會長到千萬筆，彙總表一年才幾千筆——這是傳產報表撐得住的關鍵。
 *
 * <p>{@code (summary_date, line_code)} 的 UNIQUE 約束讓批次具備冪等性：
 * 同一天重跑不會產生第二筆。
 */
@Entity
@Table(
        name = "daily_production_summary",
        uniqueConstraints = @UniqueConstraint(name = "uk_summary_date_line", columnNames = {"summary_date", "line_code"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyProductionSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "line_code", nullable = false, length = 20)
    private String lineCode;

    @Column(name = "total_good_qty", nullable = false)
    private int totalGoodQty;

    @Column(name = "total_defect_qty", nullable = false)
    private int totalDefectQty;

    @Column(name = "total_work_minutes", nullable = false)
    private int totalWorkMinutes;

    @Column(name = "defect_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal defectRate;

    @Column(name = "utilization_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal utilizationRate;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    public static DailyProductionSummary create(LocalDate summaryDate, String lineCode,
                                                int totalGoodQty, int totalDefectQty,
                                                int totalWorkMinutes, BigDecimal defectRate,
                                                BigDecimal utilizationRate, int reportCount) {
        DailyProductionSummary s = new DailyProductionSummary();
        s.summaryDate = summaryDate;
        s.lineCode = lineCode;
        s.totalGoodQty = totalGoodQty;
        s.totalDefectQty = totalDefectQty;
        s.totalWorkMinutes = totalWorkMinutes;
        s.defectRate = defectRate;
        s.utilizationRate = utilizationRate;
        s.reportCount = reportCount;
        return s;
    }

    /** 重跑時覆寫既有資料，而非新增一筆 */
    public void overwrite(int totalGoodQty, int totalDefectQty, int totalWorkMinutes,
                          BigDecimal defectRate, BigDecimal utilizationRate, int reportCount) {
        this.totalGoodQty = totalGoodQty;
        this.totalDefectQty = totalDefectQty;
        this.totalWorkMinutes = totalWorkMinutes;
        this.defectRate = defectRate;
        this.utilizationRate = utilizationRate;
        this.reportCount = reportCount;
    }
}
