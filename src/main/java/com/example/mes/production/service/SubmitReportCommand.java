package com.example.mes.production.service;

/**
 * 報工指令。
 *
 * @param idempotencyKey 由現場裝置產生的唯一鍵；同一次操作重送時必須帶相同的值
 */
public record SubmitReportCommand(
        String orderNo,
        String warehouseCode,
        String machineCode,
        int goodQty,
        int defectQty,
        String defectReason,
        int workMinutes,
        String idempotencyKey
) {
}
