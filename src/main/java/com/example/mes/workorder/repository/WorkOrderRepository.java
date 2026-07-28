package com.example.mes.workorder.repository;

import com.example.mes.workorder.domain.WorkOrder;
import com.example.mes.workorder.domain.WorkOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Optional<WorkOrder> findByOrderNo(String orderNo);

    boolean existsByOrderNo(String orderNo);

    /**
     * 以悲觀寫鎖載入工單（SELECT ... FOR UPDATE）。
     *
     * <p>報工流程專用。同一張工單同時被多位作業員報工是高機率事件，
     * 讓它們在資料庫排隊，比用樂觀鎖反覆重試更快也更好懂。
     * 鎖只在單筆交易內持有，交易本身很短（一次報工 + 數筆扣料）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkOrder w where w.orderNo = :orderNo")
    Optional<WorkOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    Page<WorkOrder> findByStatus(WorkOrderStatus status, Pageable pageable);

    Page<WorkOrder> findByLineCodeAndStatus(String lineCode, WorkOrderStatus status, Pageable pageable);

    /** 日結批次用：撈出指定時間前已完工、待結案的工單 */
    List<WorkOrder> findByStatusAndActualEndBefore(WorkOrderStatus status, LocalDateTime before);
}
