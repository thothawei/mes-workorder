package com.example.mes.workorder.domain;

import com.example.mes.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static java.math.BigDecimal.valueOf;

/**
 * 工單領域規則測試。
 *
 * <p>這些規則寫在 Entity 裡，所以測試不需要 Spring context、不需要資料庫，毫秒級跑完。
 * 領域邏輯與框架解耦帶來的好處，這裡看得最清楚。
 */
class WorkOrderTest {

    private WorkOrder newOrder(int plannedQty) {
        LocalDateTime now = LocalDateTime.now();
        return WorkOrder.create("WO-001", "P-RAIL-100", "滑座", plannedQty,
                "LINE-A", now, now.plusHours(8));
    }

    private WorkOrder releasedOrder(int plannedQty) {
        WorkOrder wo = newOrder(plannedQty);
        wo.release();
        return wo;
    }

    @Nested
    @DisplayName("建立")
    class Creation {

        @Test
        @DisplayName("新建工單為草稿、產出歸零")
        void startsAsDraft() {
            WorkOrder wo = newOrder(100);
            assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);
            assertThat(wo.getProducedQty()).isZero();
            assertThat(wo.getDefectQty()).isZero();
        }

        @Test
        @DisplayName("計畫產量不可為 0 或負數")
        void rejectsNonPositiveQty() {
            assertThatThrownBy(() -> newOrder(0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("計畫產量必須大於 0");
        }

        @Test
        @DisplayName("完工時間不可早於開工時間")
        void rejectsInvertedSchedule() {
            LocalDateTime now = LocalDateTime.now();
            assertThatThrownBy(() -> WorkOrder.create("WO-002", "P", "品", 10,
                    "LINE-A", now, now.minusHours(1)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("計畫完工時間");
        }
    }

    @Nested
    @DisplayName("報工")
    class Reporting {

        @Test
        @DisplayName("首次報工自動由「已派工」進入「生產中」並記錄實際開工時間")
        void firstReportStartsProduction() {
            WorkOrder wo = releasedOrder(100);

            wo.reportProduction(10, 1);

            assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
            assertThat(wo.getActualStart()).isCloseTo(LocalDateTime.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
            assertThat(wo.getProducedQty()).isEqualTo(10);
            assertThat(wo.getDefectQty()).isEqualTo(1);
        }

        @Test
        @DisplayName("多次報工累加")
        void accumulates() {
            WorkOrder wo = releasedOrder(100);

            wo.reportProduction(10, 1);
            wo.reportProduction(20, 2);

            assertThat(wo.getProducedQty()).isEqualTo(30);
            assertThat(wo.getDefectQty()).isEqualTo(3);
        }

        @Test
        @DisplayName("草稿狀態不可報工")
        void draftCannotReport() {
            assertThatThrownBy(() -> newOrder(100).reportProduction(1, 0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不可報工");
        }

        @Test
        @DisplayName("暫停中不可報工")
        void onHoldCannotReport() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(5, 0);
            wo.hold("待料");

            assertThatThrownBy(() -> wo.reportProduction(1, 0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("暫停");
        }

        @Test
        @DisplayName("數量不可為負")
        void rejectsNegativeQty() {
            WorkOrder wo = releasedOrder(100);
            assertThatThrownBy(() -> wo.reportProduction(-1, 0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不可為負數");
        }

        @Test
        @DisplayName("良品與不良皆為 0 的空報工要擋掉")
        void rejectsEmptyReport() {
            WorkOrder wo = releasedOrder(100);
            assertThatThrownBy(() -> wo.reportProduction(0, 0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不可全為 0");
        }
    }

    @Nested
    @DisplayName("超交容許")
    class OverProduction {

        @Test
        @DisplayName("計畫 100 件的上限是 105 件")
        void ceilingIsFivePercentAbovePlan() {
            assertThat(releasedOrder(100).overProductionCeiling()).isEqualTo(105);
        }

        @Test
        @DisplayName("容許上限以內放行")
        void allowsWithinTolerance() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(105, 0);
            assertThat(wo.getProducedQty()).isEqualTo(105);
        }

        @Test
        @DisplayName("超過上限擋下，且訊息要講清楚上限是多少")
        void rejectsBeyondTolerance() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(100, 0);

            assertThatThrownBy(() -> wo.reportProduction(6, 0))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("106")
                    .hasMessageContaining("105");
        }

        @Test
        @DisplayName("零頭無條件捨去：計畫 10 件上限是 10 件而非 10.5")
        void truncatesFraction() {
            assertThat(releasedOrder(10).overProductionCeiling()).isEqualTo(10);
        }

        @Test
        @DisplayName("不良品不佔用超交額度")
        void defectDoesNotCountTowardCeiling() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(50, 50);
            wo.reportProduction(55, 0);
            assertThat(wo.getProducedQty()).isEqualTo(105);
        }
    }

    @Nested
    @DisplayName("完工與計算")
    class CompletionAndMetrics {

        @Test
        @DisplayName("沒有任何良品產出不可完工")
        void cannotCompleteWithoutOutput() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(0, 5);

            assertThatThrownBy(wo::complete)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("尚無任何良品產出");
        }

        @Test
        @DisplayName("完工記錄實際完工時間")
        void completeRecordsActualEnd() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(100, 0);
            wo.complete();

            assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
            assertThat(wo.getActualEnd()).isNotNull();
        }

        @Test
        @DisplayName("不良率 = 不良 ÷ (良品 + 不良)")
        void calculatesDefectRate() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(90, 10);
            assertThat(wo.defectRate()).isEqualByComparingTo(valueOf(0.1));
        }

        @Test
        @DisplayName("尚無產出時不良率為 0，不可除以零")
        void defectRateIsZeroWhenNoOutput() {
            assertThat(releasedOrder(100).defectRate()).isEqualByComparingTo(valueOf(0));
        }

        @Test
        @DisplayName("達成率 = 良品 ÷ 計畫量")
        void calculatesCompletionRate() {
            WorkOrder wo = releasedOrder(200);
            wo.reportProduction(50, 0);
            assertThat(wo.completionRate()).isEqualByComparingTo(valueOf(0.25));
        }
    }

    @Nested
    @DisplayName("狀態流轉")
    class StatusFlow {

        @Test
        @DisplayName("暫停會記錄原因")
        void holdRecordsReason() {
            WorkOrder wo = releasedOrder(100);
            wo.hold("模具維修");
            assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.ON_HOLD);
            assertThat(wo.getStatusRemark()).isEqualTo("模具維修");
        }

        @Test
        @DisplayName("暫停後可復工並繼續報工")
        void resumeAllowsFurtherReporting() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(10, 0);
            wo.hold("換模");
            wo.resume();
            wo.reportProduction(10, 0);

            assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
            assertThat(wo.getProducedQty()).isEqualTo(20);
        }

        @Test
        @DisplayName("完工後不可再取消")
        void cannotCancelAfterComplete() {
            WorkOrder wo = releasedOrder(100);
            wo.reportProduction(100, 0);
            wo.complete();

            assertThatThrownBy(() -> wo.cancel("反悔"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("無法由");
        }
    }
}
