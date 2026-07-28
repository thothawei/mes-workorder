package com.example.mes.material.domain;

import com.example.mes.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 物料異動流水——**只增不改不刪**。
 *
 * <p>庫存表只存「現在有多少」，這張表存「怎麼變成這樣的」。
 * 現場對帳時，把某物料的流水加總必須等於庫存餘額；對不上就代表有程式繞過了正規扣帳路徑。
 * 這是傳產系統查帳的標準做法，也是這個專案最能展現「資料可追溯」的地方。
 */
@Entity
@Table(
        name = "material_transaction",
        indexes = {
                @Index(name = "idx_mtx_material", columnList = "material_code, created_at"),
                @Index(name = "idx_mtx_work_order", columnList = "work_order_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialTransaction extends BaseEntity {

    public enum Type {
        /** 生產耗用（負向） */
        CONSUME,
        /** 退料入庫（正向） */
        RETURN,
        /** 盤點調整 */
        ADJUST
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_code", nullable = false, length = 30)
    private String materialCode;

    @Column(name = "warehouse_code", nullable = false, length = 20)
    private String warehouseCode;

    /** 異動量：耗用為負、入庫為正 */
    @Column(name = "qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 20)
    private Type txType;

    /** 來源工單，可回溯是哪張單耗掉的 */
    @Column(name = "work_order_id")
    private Long workOrderId;

    /** 來源報工單，可精準回溯到單次報工 */
    @Column(name = "report_id")
    private Long reportId;

    /** 異動後餘額快照，對帳時不必重算累計 */
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceAfter;

    public static MaterialTransaction consume(String materialCode, String warehouseCode, BigDecimal qty,
                                              Long workOrderId, Long reportId, BigDecimal balanceAfter) {
        MaterialTransaction tx = new MaterialTransaction();
        tx.materialCode = materialCode;
        tx.warehouseCode = warehouseCode;
        tx.qty = qty.negate();
        tx.txType = Type.CONSUME;
        tx.workOrderId = workOrderId;
        tx.reportId = reportId;
        tx.balanceAfter = balanceAfter;
        return tx;
    }
}
