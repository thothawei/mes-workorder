package com.example.mes.production.domain;

import com.example.mes.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 報工紀錄。
 *
 * <p><b>{@code idempotency_key} 上的 UNIQUE 約束是整個系統防重複扣料的最後防線。</b>
 * 現場平板網路不穩，作業員按不到回應就重按是常態；應用層的「先查再寫」在多節點部署下
 * 會有競態窗口，只有資料庫約束擋得住。這條約束比任何應用層檢查都重要。
 */
@Entity
@Table(
        name = "production_report",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_idempotency", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "idx_report_work_order", columnList = "work_order_id"),
                @Index(name = "idx_report_reported_at", columnList = "reported_at"),
                @Index(name = "idx_report_line_date", columnList = "line_code, reported_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductionReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;

    /** 冗餘工單號，報表查詢免 JOIN */
    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    /** 冗餘產線代號，日結彙總免 JOIN */
    @Column(name = "line_code", nullable = false, length = 20)
    private String lineCode;

    @Column(name = "operator", nullable = false, length = 50)
    private String operator;

    @Column(name = "machine_code", length = 20)
    private String machineCode;

    @Column(name = "good_qty", nullable = false)
    private int goodQty;

    @Column(name = "defect_qty", nullable = false)
    private int defectQty;

    /** 不良原因代碼，用於柏拉圖分析 */
    @Column(name = "defect_reason", length = 50)
    private String defectReason;

    /** 本次投入工時（分鐘），用於計算稼動率 */
    @Column(name = "work_minutes", nullable = false)
    private int workMinutes;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    /** 由前端產生的唯一鍵，同一次現場操作重送時值不變 */
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    public static ProductionReport create(Long workOrderId, String orderNo, String lineCode,
                                          String operator, String machineCode,
                                          int goodQty, int defectQty, String defectReason,
                                          int workMinutes, String idempotencyKey) {
        ProductionReport r = new ProductionReport();
        r.workOrderId = workOrderId;
        r.orderNo = orderNo;
        r.lineCode = lineCode;
        r.operator = operator;
        r.machineCode = machineCode;
        r.goodQty = goodQty;
        r.defectQty = defectQty;
        r.defectReason = defectReason;
        r.workMinutes = workMinutes;
        r.reportedAt = LocalDateTime.now();
        r.idempotencyKey = idempotencyKey;
        return r;
    }
}
