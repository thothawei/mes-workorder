package com.example.mes.workorder.domain;

import com.example.mes.common.exception.BusinessException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.mes.workorder.domain.WorkOrderStatus.*;

/**
 * 工單狀態機規則表——**全系統唯一的狀態轉換真理來源**。
 *
 * <p>把規則集中在這裡，是為了讓「現場流程要改」變成改一張表，而不是全域搜尋 if-else。
 * 例如日後要在完工前插入「品檢」關卡，只需要在 {@link WorkOrderStatus} 新增列舉值、
 * 在下面的 RULES 改兩行，Service 與 Controller 完全不用動。
 *
 * <p>共 12 條合法轉換，見 {@code docs/architecture.md} 的狀態圖。
 */
public final class WorkOrderTransition {

    /** key = 來源狀態，value = 允許轉入的目標狀態 */
    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> RULES = Map.of(
            DRAFT,       Set.of(RELEASED, CANCELLED),                  // 1, 2
            RELEASED,    Set.of(IN_PROGRESS, ON_HOLD, CANCELLED),      // 3, 4, 5
            IN_PROGRESS, Set.of(ON_HOLD, COMPLETED, CANCELLED),        // 6, 7, 8
            ON_HOLD,     Set.of(IN_PROGRESS, RELEASED, CANCELLED),     // 9, 10, 11
            COMPLETED,   Set.of(CLOSED),                               // 12
            CLOSED,      Set.of(),
            CANCELLED,   Set.of()
    );

    private WorkOrderTransition() {
    }

    public static boolean isAllowed(WorkOrderStatus from, WorkOrderStatus to) {
        return RULES.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<WorkOrderStatus> allowedTargets(WorkOrderStatus from) {
        return RULES.getOrDefault(from, Set.of());
    }

    /**
     * 驗證轉換合法性，非法時拋出帶有「目前可以做什麼」的錯誤訊息。
     *
     * <p>訊息刻意列出合法目標——現場人員看到「不能取消」還要打電話問資訊室，
     * 看到「已結案的工單不可異動，目前無可用操作」就自己懂了。
     */
    public static void validate(String orderNo, WorkOrderStatus from, WorkOrderStatus to) {
        if (isAllowed(from, to)) {
            return;
        }
        List<String> targets = allowedTargets(from).stream().map(WorkOrderStatus::getLabel).sorted().toList();
        String hint = targets.isEmpty() ? "目前無可用操作" : "目前僅可轉為：" + String.join("、", targets);
        throw new BusinessException(
                "ILLEGAL_STATUS_TRANSITION",
                "工單 %s 無法由「%s」轉為「%s」，%s".formatted(orderNo, from.getLabel(), to.getLabel(), hint)
        );
    }
}
