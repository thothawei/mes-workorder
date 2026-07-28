package com.example.mes.production.service;

import com.example.mes.material.domain.MaterialTransaction;
import com.example.mes.production.domain.ProductionReport;
import com.example.mes.workorder.domain.WorkOrder;

import java.math.BigDecimal;
import java.util.List;

/**
 * 報工結果。
 *
 * @param duplicated 為 true 代表這是重送，系統沒有重複扣料；前端據此顯示「已受理」而非「成功」
 */
public record ProductionReportResult(
        Long reportId,
        String orderNo,
        int goodQty,
        int defectQty,
        Integer accumulatedGoodQty,
        Integer plannedQty,
        List<ConsumedMaterial> consumedMaterials,
        boolean duplicated
) {

    public record ConsumedMaterial(String materialCode, BigDecimal qty, BigDecimal balanceAfter) {
    }

    static ProductionReportResult created(ProductionReport report, WorkOrder workOrder,
                                          List<MaterialTransaction> transactions) {
        List<ConsumedMaterial> materials = transactions.stream()
                // 流水以負數儲存，回給前端時轉為正的耗用量比較好讀
                .map(tx -> new ConsumedMaterial(tx.getMaterialCode(), tx.getQty().negate(), tx.getBalanceAfter()))
                .toList();
        return new ProductionReportResult(
                report.getId(), report.getOrderNo(),
                report.getGoodQty(), report.getDefectQty(),
                workOrder.getProducedQty(), workOrder.getPlannedQty(),
                materials, false);
    }

    static ProductionReportResult duplicated(ProductionReport report) {
        return new ProductionReportResult(
                report.getId(), report.getOrderNo(),
                report.getGoodQty(), report.getDefectQty(),
                null, null, List.of(), true);
    }
}
