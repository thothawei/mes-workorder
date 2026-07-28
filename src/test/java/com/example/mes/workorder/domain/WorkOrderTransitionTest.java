package com.example.mes.workorder.domain;

import com.example.mes.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.example.mes.workorder.domain.WorkOrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 狀態機規則測試。
 *
 * <p>把 12 條合法轉換全部列成參數化測試，並且**反向驗證**其餘 37 種組合都被擋下——
 * 只測合法路徑會漏掉「規則表少寫一條卻沒人發現」的情況。
 */
class WorkOrderTransitionTest {

    static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(DRAFT, RELEASED),
                Arguments.of(DRAFT, CANCELLED),
                Arguments.of(RELEASED, IN_PROGRESS),
                Arguments.of(RELEASED, ON_HOLD),
                Arguments.of(RELEASED, CANCELLED),
                Arguments.of(IN_PROGRESS, ON_HOLD),
                Arguments.of(IN_PROGRESS, COMPLETED),
                Arguments.of(IN_PROGRESS, CANCELLED),
                Arguments.of(ON_HOLD, IN_PROGRESS),
                Arguments.of(ON_HOLD, RELEASED),
                Arguments.of(ON_HOLD, CANCELLED),
                Arguments.of(COMPLETED, CLOSED)
        );
    }

    @DisplayName("12 條合法轉換全部放行")
    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("legalTransitions")
    void allowsLegalTransitions(WorkOrderStatus from, WorkOrderStatus to) {
        assertThat(WorkOrderTransition.isAllowed(from, to)).isTrue();
    }

    @DisplayName("合法轉換恰好 12 條，多寫或漏寫都會被抓到")
    @Test
    void hasExactlyTwelveLegalTransitions() {
        long total = Arrays.stream(WorkOrderStatus.values())
                .mapToLong(s -> WorkOrderTransition.allowedTargets(s).size())
                .sum();
        assertThat(total).isEqualTo(12);
    }

    @DisplayName("清單以外的組合一律拒絕")
    @Test
    void rejectsEverythingElse() {
        var legal = legalTransitions()
                .map(a -> a.get()[0] + "->" + a.get()[1])
                .toList();

        for (WorkOrderStatus from : WorkOrderStatus.values()) {
            for (WorkOrderStatus to : WorkOrderStatus.values()) {
                if (legal.contains(from + "->" + to)) {
                    continue;
                }
                assertThat(WorkOrderTransition.isAllowed(from, to))
                        .as("%s → %s 應被拒絕", from, to)
                        .isFalse();
            }
        }
    }

    @DisplayName("終態不可再轉出任何狀態")
    @ParameterizedTest
    @EnumSource(value = WorkOrderStatus.class, names = {"CLOSED", "CANCELLED"})
    void terminalStatusHasNoOutgoingTransition(WorkOrderStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(WorkOrderTransition.allowedTargets(terminal)).isEmpty();
    }

    @DisplayName("非法轉換的錯誤訊息要告訴現場「目前可以做什麼」")
    @Test
    void errorMessageListsAvailableActions() {
        assertThatThrownBy(() -> WorkOrderTransition.validate("WO-001", DRAFT, COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WO-001")
                .hasMessageContaining("草稿")
                .hasMessageContaining("已完工")
                .hasMessageContaining("目前僅可轉為");
    }

    @DisplayName("終態的錯誤訊息不能空著讓人乾瞪眼")
    @Test
    void errorMessageForTerminalStatus() {
        assertThatThrownBy(() -> WorkOrderTransition.validate("WO-001", CLOSED, IN_PROGRESS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目前無可用操作");
    }
}
