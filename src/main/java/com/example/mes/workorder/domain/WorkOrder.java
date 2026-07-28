package com.example.mes.workorder.domain;

import com.example.mes.common.audit.BaseEntity;
import com.example.mes.common.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 工單——本系統的聚合根。
 *
 * <p><b>設計要點：所有狀態變更都必須經過這個類別的方法，不提供 setter。</b>
 * 這樣「非法狀態轉換」與「超產」在編譯期就無法繞過：Service 層即使忘記檢查，
 * 呼叫 {@link #reportProduction} 一樣會被擋下。防呆優於紀律。
 */
@Entity
@Table(
        name = "work_order",
        indexes = {
                @Index(name = "idx_wo_status_line", columnList = "status, line_code"),
                @Index(name = "idx_wo_planned_start", columnList = "planned_start")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrder extends BaseEntity {

    /**
     * 超交容許率 5%。
     *
     * <p>製造現場真實存在的規則：模具一開就是一模多穴，收尾時常會多做幾件，
     * 卡死在計畫量會讓現場報不進去而改用紙本，系統就形同虛設。
     */
    private static final BigDecimal OVER_PRODUCTION_TOLERANCE = new BigDecimal("1.05");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 30)
    private String orderNo;

    @Column(name = "product_code", nullable = false, length = 30)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    /** 計畫產量（件） */
    @Column(name = "planned_qty", nullable = false)
    private int plannedQty;

    /** 良品累計 */
    @Column(name = "produced_qty", nullable = false)
    private int producedQty;

    /** 不良累計 */
    @Column(name = "defect_qty", nullable = false)
    private int defectQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkOrderStatus status;

    @Column(name = "line_code", nullable = false, length = 20)
    private String lineCode;

    @Column(name = "planned_start", nullable = false)
    private LocalDateTime plannedStart;

    @Column(name = "planned_end", nullable = false)
    private LocalDateTime plannedEnd;

    @Column(name = "actual_start")
    private LocalDateTime actualStart;

    @Column(name = "actual_end")
    private LocalDateTime actualEnd;

    /** 暫停或取消原因，供現場與主管回溯 */
    @Column(name = "status_remark", length = 200)
    private String statusRemark;

    /**
     * 樂觀鎖版本號。
     *
     * <p>本系統對工單採悲觀鎖為主（見 ProductionReportService），
     * 但保留 @Version 以擋住「繞過 Service、由其他路徑更新工單」的情況。
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ---------- 建立 ----------

    public static WorkOrder create(String orderNo, String productCode, String productName,
                                   int plannedQty, String lineCode,
                                   LocalDateTime plannedStart, LocalDateTime plannedEnd) {
        if (plannedQty <= 0) {
            throw new BusinessException("INVALID_PLANNED_QTY", "計畫產量必須大於 0");
        }
        if (plannedEnd.isBefore(plannedStart)) {
            throw new BusinessException("INVALID_SCHEDULE", "計畫完工時間不可早於計畫開工時間");
        }
        WorkOrder wo = new WorkOrder();
        wo.orderNo = orderNo;
        wo.productCode = productCode;
        wo.productName = productName;
        wo.plannedQty = plannedQty;
        wo.producedQty = 0;
        wo.defectQty = 0;
        wo.lineCode = lineCode;
        wo.plannedStart = plannedStart;
        wo.plannedEnd = plannedEnd;
        wo.status = WorkOrderStatus.DRAFT;
        return wo;
    }

    // ---------- 狀態轉換 ----------

    /** 派工：草稿 → 已派工 */
    public void release() {
        transitionTo(WorkOrderStatus.RELEASED, null);
    }

    /** 暫停：待料、設備異常 */
    public void hold(String reason) {
        transitionTo(WorkOrderStatus.ON_HOLD, reason);
    }

    /** 復工：暫停 → 生產中 */
    public void resume() {
        transitionTo(WorkOrderStatus.IN_PROGRESS, null);
    }

    /** 退回待派：暫停 → 已派工（例如整批物料改由其他產線生產） */
    public void returnToReleased() {
        transitionTo(WorkOrderStatus.RELEASED, null);
    }

    /** 完工 */
    public void complete() {
        if (producedQty == 0) {
            throw new BusinessException("NO_PRODUCTION", "工單 %s 尚無任何良品產出，不可完工".formatted(orderNo));
        }
        transitionTo(WorkOrderStatus.COMPLETED, null);
        this.actualEnd = LocalDateTime.now();
    }

    /** 取消（需 MANAGER 權限，權限檢查在 Service 層） */
    public void cancel(String reason) {
        transitionTo(WorkOrderStatus.CANCELLED, reason);
    }

    /** 結案：由日結批次呼叫 */
    public void close() {
        transitionTo(WorkOrderStatus.CLOSED, null);
    }

    // ---------- 報工 ----------

    /**
     * 累計本次報工數量。
     *
     * <p>首次報工會自動由「已派工」推進到「生產中」並記錄實際開工時間——
     * 現場不會為了「按開工鈕」多操作一次，這是真實流程。
     */
    public void reportProduction(int goodQty, int defectQty) {
        if (goodQty < 0 || defectQty < 0) {
            throw new BusinessException("INVALID_REPORT_QTY", "報工數量不可為負數");
        }
        if (goodQty == 0 && defectQty == 0) {
            throw new BusinessException("INVALID_REPORT_QTY", "報工數量不可全為 0");
        }
        if (!status.acceptsProduction()) {
            throw new BusinessException(
                    "STATUS_NOT_ACCEPT_PRODUCTION",
                    "工單 %s 目前為「%s」，不可報工".formatted(orderNo, status.getLabel())
            );
        }

        int newProduced = this.producedQty + goodQty;
        int ceiling = overProductionCeiling();
        if (newProduced > ceiling) {
            throw new BusinessException(
                    "OVER_PRODUCTION",
                    "工單 %s 良品累計將達 %d 件，超過容許上限 %d 件（計畫 %d 件、容許超交 5%%）"
                            .formatted(orderNo, newProduced, ceiling, plannedQty)
            );
        }

        if (status == WorkOrderStatus.RELEASED) {
            transitionTo(WorkOrderStatus.IN_PROGRESS, null);
            this.actualStart = LocalDateTime.now();
        }
        this.producedQty = newProduced;
        this.defectQty += defectQty;
    }

    /** 良品可報上限（含 5% 超交容許），無條件捨去到整數件 */
    public int overProductionCeiling() {
        return BigDecimal.valueOf(plannedQty)
                .multiply(OVER_PRODUCTION_TOLERANCE)
                .setScale(0, RoundingMode.DOWN)
                .intValue();
    }

    /** 不良率；尚無產出時回 0 */
    public BigDecimal defectRate() {
        int total = producedQty + defectQty;
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(defectQty)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    /** 達成率（良品 / 計畫量） */
    public BigDecimal completionRate() {
        return BigDecimal.valueOf(producedQty)
                .divide(BigDecimal.valueOf(plannedQty), 4, RoundingMode.HALF_UP);
    }

    // ---------- 內部 ----------

    private void transitionTo(WorkOrderStatus target, String remark) {
        WorkOrderTransition.validate(orderNo, this.status, target);
        this.status = target;
        if (remark != null) {
            this.statusRemark = remark;
        }
    }
}
