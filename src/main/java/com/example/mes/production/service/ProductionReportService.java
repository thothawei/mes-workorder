package com.example.mes.production.service;

import com.example.mes.common.exception.ResourceNotFoundException;
import com.example.mes.material.domain.MaterialTransaction;
import com.example.mes.material.service.InventoryService;
import com.example.mes.production.domain.ProductionReport;
import com.example.mes.production.repository.ProductionReportRepository;
import com.example.mes.workorder.domain.WorkOrder;
import com.example.mes.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 報工核心交易。
 *
 * <p>整個系統的重心。一次報工要在**同一個交易**內完成四件事，全成功或全失敗：
 * <ol>
 *   <li>寫入報工紀錄（受 idempotency_key UNIQUE 約束保護）</li>
 *   <li>累加工單的良品／不良數（工單以悲觀鎖持有）</li>
 *   <li>依 BOM 展開扣減庫存（庫存以樂觀鎖保護）</li>
 *   <li>寫入物料異動流水，可回溯到這一筆報工</li>
 * </ol>
 *
 * <p>其中任何一步失敗——最常見的是庫存不足——整筆回滾，資料庫不會留下半套狀態。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionReportService {

    private final WorkOrderRepository workOrderRepository;
    private final ProductionReportRepository reportRepository;
    private final InventoryService inventoryService;

    /**
     * 提交報工。
     *
     * <p>注意這個方法**沒有**捕捉 DataIntegrityViolationException 或
     * OptimisticLockingFailureException。一旦交易被標記為 rollback-only，
     * 在同一個交易裡做任何補救查詢都會失敗。這兩種例外交由
     * {@link ProductionReportFacade} 在交易外處理。
     */
    @Transactional
    @PreAuthorize("hasAnyRole('OPERATOR','LEADER','MANAGER')")
    public ProductionReportResult submit(SubmitReportCommand cmd, String operator) {
        // 第一層冪等防護：快樂路徑，避免重送時白跑一次完整交易。
        // 這一層在多節點併發下會漏，真正的保證是 DB 的 UNIQUE 約束。
        Optional<ProductionReport> existing = reportRepository.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            log.info("報工重送，直接回傳既有結果 key={}", cmd.idempotencyKey());
            return ProductionReportResult.duplicated(existing.get());
        }

        // 悲觀鎖載入工單：同一張工單的報工在資料庫層排隊，一次處理一筆。
        WorkOrder workOrder = workOrderRepository.findByOrderNoForUpdate(cmd.orderNo())
                .orElseThrow(() -> new ResourceNotFoundException("工單", cmd.orderNo()));

        // 狀態合法性與超產上限由 Entity 自己守，Service 不需要（也不應該）複製這些規則。
        workOrder.reportProduction(cmd.goodQty(), cmd.defectQty());

        // 先存報工紀錄以取得 id，物料流水才能指回這一筆。
        // saveAndFlush 會在此立即觸發 UNIQUE 約束檢查——這是第二層冪等防護。
        ProductionReport report = reportRepository.saveAndFlush(ProductionReport.create(
                workOrder.getId(), workOrder.getOrderNo(), workOrder.getLineCode(),
                operator, cmd.machineCode(),
                cmd.goodQty(), cmd.defectQty(), cmd.defectReason(),
                cmd.workMinutes(), cmd.idempotencyKey()));

        List<MaterialTransaction> consumed = List.of();
        if (cmd.goodQty() > 0) {
            consumed = inventoryService.consumeByBom(
                    workOrder.getProductCode(), cmd.warehouseCode(),
                    cmd.goodQty(), workOrder.getId(), report.getId());
        }

        log.info("報工完成 工單={} 良品={} 不良={} 扣料={}項 累計良品={}/{}",
                workOrder.getOrderNo(), cmd.goodQty(), cmd.defectQty(), consumed.size(),
                workOrder.getProducedQty(), workOrder.getPlannedQty());

        return ProductionReportResult.created(report, workOrder, consumed);
    }

    /**
     * 供 Facade 在交易外查回已存在的報工結果。
     *
     * <p>唯讀且獨立交易，不會被前一個已回滾的交易影響。
     */
    @Transactional(readOnly = true)
    public Optional<ProductionReportResult> findByIdempotencyKey(String key) {
        return reportRepository.findByIdempotencyKey(key).map(ProductionReportResult::duplicated);
    }
}
