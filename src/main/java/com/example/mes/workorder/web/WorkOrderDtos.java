package com.example.mes.workorder.web;

import com.example.mes.workorder.domain.WorkOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工單相關 DTO。
 *
 * <p>集中在一個檔案，因為它們是同一組契約，分散成七個檔案只會增加翻找成本。
 */
public final class WorkOrderDtos {

    private WorkOrderDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 30) String orderNo,
            @NotBlank @Size(max = 30) String productCode,
            @NotBlank @Size(max = 100) String productName,
            @Min(1) int plannedQty,
            @NotBlank @Size(max = 20) String lineCode,
            @NotNull LocalDateTime plannedStart,
            @NotNull LocalDateTime plannedEnd
    ) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 200) String reason) {
    }

    @Schema(description = "工單檢視資料")
    public record WorkOrderView(
            Long id,
            String orderNo,
            String productCode,
            String productName,
            int plannedQty,
            int producedQty,
            int defectQty,
            String status,
            String statusLabel,
            String lineCode,
            LocalDateTime plannedStart,
            LocalDateTime plannedEnd,
            LocalDateTime actualStart,
            LocalDateTime actualEnd,
            String statusRemark,
            BigDecimal defectRate,
            BigDecimal completionRate
    ) {
        public static WorkOrderView from(WorkOrder w) {
            return new WorkOrderView(
                    w.getId(), w.getOrderNo(), w.getProductCode(), w.getProductName(),
                    w.getPlannedQty(), w.getProducedQty(), w.getDefectQty(),
                    w.getStatus().name(), w.getStatus().getLabel(), w.getLineCode(),
                    w.getPlannedStart(), w.getPlannedEnd(), w.getActualStart(), w.getActualEnd(),
                    w.getStatusRemark(), w.defectRate(), w.completionRate());
        }
    }
}
