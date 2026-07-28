package com.example.mes.material.domain;

import com.example.mes.common.audit.BaseEntity;
import com.example.mes.common.exception.BusinessException;
import com.example.mes.common.exception.InsufficientInventoryException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 庫存（物料 × 倉別）。
 *
 * <p><b>採樂觀鎖。</b>理由：同一物料被不同工單同時搶用是低機率事件（物料種類多、需求分散），
 * 用悲觀鎖會讓熱門原料的資料列變成全系統瓶頸；樂觀鎖衝突時重試成本低。
 * 對比之下工單採悲觀鎖，因為同一張工單被多人同時報工是高機率事件（輪班交接）。
 */
@Entity
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_material_wh", columnNames = {"material_code", "warehouse_code"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_code", nullable = false, length = 30)
    private String materialCode;

    @Column(name = "warehouse_code", nullable = false, length = 20)
    private String warehouseCode;

    @Column(name = "qty_on_hand", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyOnHand;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Inventory create(String materialCode, String warehouseCode, BigDecimal qtyOnHand) {
        Inventory inv = new Inventory();
        inv.materialCode = materialCode;
        inv.warehouseCode = warehouseCode;
        inv.qtyOnHand = qtyOnHand;
        return inv;
    }

    /**
     * 扣帳。庫存不足時拋例外，讓整筆報工交易回滾。
     *
     * <p>刻意不允許扣成負數：傳產現場一旦容許負庫存，帳面數字就再也對不回來。
     * 寧可讓現場先補入庫，也不要留一個永遠查不清的洞。
     */
    public void consume(BigDecimal qty) {
        requirePositive(qty);
        if (qtyOnHand.compareTo(qty) < 0) {
            throw new InsufficientInventoryException(materialCode, qty, qtyOnHand);
        }
        this.qtyOnHand = this.qtyOnHand.subtract(qty);
    }

    /** 入庫或退料 */
    public void replenish(BigDecimal qty) {
        requirePositive(qty);
        this.qtyOnHand = this.qtyOnHand.add(qty);
    }

    private void requirePositive(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_QTY", "異動數量必須大於 0");
        }
    }
}
