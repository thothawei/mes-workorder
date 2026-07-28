package com.example.mes.material.service;

import com.example.mes.common.exception.ResourceNotFoundException;
import com.example.mes.material.domain.BomItem;
import com.example.mes.material.domain.Inventory;
import com.example.mes.material.domain.MaterialTransaction;
import com.example.mes.material.repository.BomItemRepository;
import com.example.mes.material.repository.InventoryRepository;
import com.example.mes.material.repository.MaterialTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 庫存扣帳。
 *
 * <p><b>這裡沒有任何重試邏輯，是刻意的。</b>樂觀鎖衝突必須讓整筆交易回滾後，
 * 由交易外的呼叫端重跑整個流程；在交易內部重試會拿到已經髒掉的 persistence context，
 * 重試出來的結果是錯的。重試策略見 {@code ProductionReportFacade}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final BomItemRepository bomItemRepository;
    private final MaterialTransactionRepository transactionRepository;

    /**
     * 依 BOM 展開並扣帳。
     *
     * @param productCode 成品代號
     * @param goodQty     本次良品數（不良品不扣料——料已經耗掉了，但這裡只算成品用量，
     *                    不良品的耗料透過 BOM 的 scrapRate 攤提）
     * @return 實際扣帳明細
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<MaterialTransaction> consumeByBom(String productCode, String warehouseCode, int goodQty,
                                                  Long workOrderId, Long reportId) {
        List<BomItem> bom = bomItemRepository.findByProductCode(productCode);
        if (bom.isEmpty()) {
            throw new ResourceNotFoundException("成品 BOM", productCode);
        }

        // 依物料代號排序後才扣帳：多張工單同時扣多項共用料時，
        // 固定的加鎖順序可避免資料庫層的死結（deadlock）。
        return bom.stream()
                .sorted(Comparator.comparing(BomItem::getMaterialCode))
                .map(item -> consumeOne(item, warehouseCode, goodQty, workOrderId, reportId))
                .toList();
    }

    private MaterialTransaction consumeOne(BomItem item, String warehouseCode, int goodQty,
                                           Long workOrderId, Long reportId) {
        BigDecimal required = item.requiredQty(goodQty);

        Inventory inventory = inventoryRepository
                .findByMaterialCodeAndWarehouseCode(item.getMaterialCode(), warehouseCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "庫存（物料 %s / 倉別 %s）".formatted(item.getMaterialCode(), warehouseCode), item.getMaterialCode()));

        // 庫存不足會在此拋 InsufficientInventoryException，整筆報工交易回滾，
        // 不會留下「報工紀錄寫了、料沒扣」的半套資料。
        inventory.consume(required);

        MaterialTransaction tx = MaterialTransaction.consume(
                item.getMaterialCode(), warehouseCode, required,
                workOrderId, reportId, inventory.getQtyOnHand());

        log.debug("扣料 物料={} 需求={} 餘額={} 工單={}",
                item.getMaterialCode(), required, inventory.getQtyOnHand(), workOrderId);

        return transactionRepository.save(tx);
    }
}
