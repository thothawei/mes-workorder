package com.example.mes.workorder.service;

import com.example.mes.common.exception.BusinessException;
import com.example.mes.common.exception.ResourceNotFoundException;
import com.example.mes.workorder.domain.WorkOrder;
import com.example.mes.workorder.domain.WorkOrderStatus;
import com.example.mes.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 工單管理。
 *
 * <p>權限標在方法上而非只擋 URL——路由改名時 URL 規則容易漏改，方法層註解不會。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;

    @Transactional
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public WorkOrder create(String orderNo, String productCode, String productName,
                            int plannedQty, String lineCode,
                            LocalDateTime plannedStart, LocalDateTime plannedEnd) {
        if (workOrderRepository.existsByOrderNo(orderNo)) {
            throw new BusinessException("DUPLICATE_ORDER_NO", "工單號 %s 已存在".formatted(orderNo));
        }
        WorkOrder wo = WorkOrder.create(orderNo, productCode, productName, plannedQty,
                lineCode, plannedStart, plannedEnd);
        log.info("建立工單 {} 產品={} 計畫量={}", orderNo, productCode, plannedQty);
        return workOrderRepository.save(wo);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public WorkOrder release(String orderNo) {
        WorkOrder wo = requireByOrderNo(orderNo);
        wo.release();
        return wo;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public WorkOrder hold(String orderNo, String reason) {
        WorkOrder wo = requireByOrderNo(orderNo);
        wo.hold(reason);
        return wo;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public WorkOrder resume(String orderNo) {
        WorkOrder wo = requireByOrderNo(orderNo);
        wo.resume();
        return wo;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public WorkOrder returnToReleased(String orderNo) {
        WorkOrder wo = requireByOrderNo(orderNo);
        wo.returnToReleased();
        return wo;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('LEADER','MANAGER')")
    public WorkOrder complete(String orderNo) {
        WorkOrder wo = requireByOrderNo(orderNo);
        wo.complete();
        log.info("工單完工 {} 良品={}/{} 不良={}", orderNo, wo.getProducedQty(), wo.getPlannedQty(), wo.getDefectQty());
        return wo;
    }

    /** 取消工單只有廠務主管能做——已投料的工單取消涉及退料與成本歸屬 */
    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public WorkOrder cancel(String orderNo, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "取消工單必須填寫原因");
        }
        WorkOrder wo = requireByOrderNo(orderNo);
        wo.cancel(reason);
        log.info("工單取消 {} 原因={}", orderNo, reason);
        return wo;
    }

    @Transactional(readOnly = true)
    public WorkOrder getByOrderNo(String orderNo) {
        return requireByOrderNo(orderNo);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrder> search(String lineCode, WorkOrderStatus status, Pageable pageable) {
        if (lineCode != null && status != null) {
            return workOrderRepository.findByLineCodeAndStatus(lineCode, status, pageable);
        }
        if (status != null) {
            return workOrderRepository.findByStatus(status, pageable);
        }
        return workOrderRepository.findAll(pageable);
    }

    private WorkOrder requireByOrderNo(String orderNo) {
        return workOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ResourceNotFoundException("工單", orderNo));
    }
}
