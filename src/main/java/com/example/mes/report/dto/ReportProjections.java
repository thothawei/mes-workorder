package com.example.mes.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 報表投影介面。
 *
 * <p>原生 SQL 直接映射到介面（Spring Data 的 interface projection），
 * 不經過 Entity——報表的欄位組合跟資料表結構本來就不同，硬套 Entity 只會逼出一堆假的欄位。
 */
public final class ReportProjections {

    private ReportProjections() {
    }

    /** 產線日產出與稼動率 */
    public interface LineDailyOutput {
        LocalDate getProductionDate();
        String getLineCode();
        Integer getGoodQty();
        Integer getDefectQty();
        BigDecimal getDefectRate();
        Integer getWorkMinutes();
        BigDecimal getUtilizationRate();
        /** 該產線在查詢區間內的累計良品（窗口函數計算） */
        Long getRunningGoodQty();
    }

    /** 不良原因柏拉圖 */
    public interface DefectPareto {
        String getDefectReason();
        Long getDefectQty();
        BigDecimal getSharePercent();
        BigDecimal getCumulativePercent();
    }

    /** 物料耗用排行 */
    public interface MaterialUsageRank {
        Integer getUsageRank();
        String getMaterialCode();
        String getMaterialName();
        String getUnit();
        BigDecimal getTotalConsumed();
        BigDecimal getCurrentStock();
        BigDecimal getSafetyStock();
        Boolean getBelowSafety();
    }
}
