package com.example.mes.material.repository;

import com.example.mes.material.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * 不加鎖查詢——庫存採樂觀鎖策略，衝突由 @Version 在 flush 時偵測。
     * 見 {@link Inventory} 類別註解對鎖策略的說明。
     */
    Optional<Inventory> findByMaterialCodeAndWarehouseCode(String materialCode, String warehouseCode);
}
