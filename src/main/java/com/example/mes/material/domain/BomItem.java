package com.example.mes.material.domain;

import com.example.mes.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 用料表（BOM）：一個成品用到哪些物料、每件用多少。
 *
 * <p>本專案只做單階 BOM。多階展開是遞迴問題，會把重心從「交易正確性」帶偏，
 * 刻意不做——範圍收斂本身就是設計決策。
 */
@Entity
@Table(
        name = "bom_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_bom_product_material", columnNames = {"product_code", "material_code"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BomItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, length = 30)
    private String productCode;

    @Column(name = "material_code", nullable = false, length = 30)
    private String materialCode;

    /** 每生產一件成品所耗用的物料量 */
    @Column(name = "qty_per_unit", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyPerUnit;

    /** 損耗率（0.02 = 2%），實際扣料時一併計入 */
    @Column(name = "scrap_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal scrapRate;

    public static BomItem create(String productCode, String materialCode,
                                 BigDecimal qtyPerUnit, BigDecimal scrapRate) {
        BomItem item = new BomItem();
        item.productCode = productCode;
        item.materialCode = materialCode;
        item.qtyPerUnit = qtyPerUnit;
        item.scrapRate = scrapRate;
        return item;
    }

    /**
     * 計算生產 {@code producedQty} 件所需扣帳量 = 單位用量 × 件數 × (1 + 損耗率)。
     *
     * <p>四捨五入到 4 位小數，與資料庫 scale 一致——若這裡用 double，
     * 累積數千筆報工後庫存會漂移，這是傳產系統最忌諱的錯。
     */
    public BigDecimal requiredQty(int producedQty) {
        return qtyPerUnit
                .multiply(BigDecimal.valueOf(producedQty))
                .multiply(BigDecimal.ONE.add(scrapRate))
                .setScale(4, RoundingMode.HALF_UP);
    }
}
