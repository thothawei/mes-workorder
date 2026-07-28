package com.example.mes.material.domain;

import com.example.mes.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 物料主檔。
 */
@Entity
@Table(name = "material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_code", nullable = false, unique = true, length = 30)
    private String materialCode;

    @Column(name = "material_name", nullable = false, length = 100)
    private String materialName;

    /** 計量單位：PCS、KG、M 等 */
    @Column(name = "unit", nullable = false, length = 10)
    private String unit;

    /** 安全庫存，低於此值由報表列入警示 */
    @Column(name = "safety_stock", nullable = false, precision = 18, scale = 4)
    private BigDecimal safetyStock;

    public static Material create(String materialCode, String materialName, String unit, BigDecimal safetyStock) {
        Material m = new Material();
        m.materialCode = materialCode;
        m.materialName = materialName;
        m.unit = unit;
        m.safetyStock = safetyStock;
        return m;
    }
}
