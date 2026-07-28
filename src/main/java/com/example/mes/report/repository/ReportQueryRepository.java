package com.example.mes.report.repository;

import com.example.mes.production.domain.ProductionReport;
import com.example.mes.report.dto.ReportProjections.DefectPareto;
import com.example.mes.report.dto.ReportProjections.LineDailyOutput;
import com.example.mes.report.dto.ReportProjections.MaterialUsageRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 報表查詢——**全部手寫原生 SQL**。
 *
 * <p>刻意不用 JPA 自動產生查詢，理由有三：
 * <ol>
 *   <li>報表需要窗口函數（累計、排名、佔比），JPQL 支援有限</li>
 *   <li>報表是效能敏感區，SQL 寫在眼前才控制得住掃描範圍與索引使用</li>
 *   <li>傳產既有系統多為 Oracle／MSSQL，維護工作大量是讀寫這種查詢</li>
 * </ol>
 *
 * <p><b>欄位別名一律加雙引號。</b>不加的話 PostgreSQL 會把別名摺成小寫、
 * H2 會摺成大寫，而 Spring Data 的 interface projection 是照名稱精確比對——
 * 結果就是同一支查詢在某個資料庫上回傳一整排 null，而且不會拋例外。
 * 這種坑很難從錯誤訊息看出來，直接用引號釘死大小寫最省事。
 *
 * <p>另外 SQL 避開資料庫特有語法（不用 {@code ::date}、不用 {@code TOP}），
 * 讓同一份查詢在 PostgreSQL 與 H2 都能跑，測試才有意義。
 */
public interface ReportQueryRepository extends JpaRepository<ProductionReport, Long> {

    /**
     * 產線日產出與稼動率。
     *
     * <p>稼動率 = 實際投入工時 ÷ 當日可用工時（兩班制 960 分鐘）。
     * {@code SUM(...) OVER (PARTITION BY ... ORDER BY ...)} 算出該產線的累計良品，
     * 讓主管一眼看出這條線是穩定爬升還是卡住。
     *
     * <p>查詢條件命中 {@code idx_report_line_date} 複合索引，不會掃全表。
     */
    @Query(value = """
            SELECT
                CAST(r.reported_at AS DATE)                          AS "productionDate",
                r.line_code                                          AS "lineCode",
                SUM(r.good_qty)                                      AS "goodQty",
                SUM(r.defect_qty)                                    AS "defectQty",
                CASE WHEN SUM(r.good_qty) + SUM(r.defect_qty) = 0 THEN 0
                     ELSE ROUND(CAST(SUM(r.defect_qty) AS DECIMAL(18,4))
                                / (SUM(r.good_qty) + SUM(r.defect_qty)), 4)
                END                                                  AS "defectRate",
                SUM(r.work_minutes)                                  AS "workMinutes",
                ROUND(CAST(SUM(r.work_minutes) AS DECIMAL(18,4))
                      / CAST(:availableMinutes AS DECIMAL(18,4)), 4) AS "utilizationRate",
                SUM(SUM(r.good_qty)) OVER (
                    PARTITION BY r.line_code
                    ORDER BY CAST(r.reported_at AS DATE)
                    ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                )                                                    AS "runningGoodQty"
            FROM production_report r
            WHERE r.reported_at >= :from AND r.reported_at < :to
            GROUP BY CAST(r.reported_at AS DATE), r.line_code
            ORDER BY CAST(r.reported_at AS DATE), r.line_code
            """, nativeQuery = true)
    List<LineDailyOutput> lineDailyOutput(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to,
                                          @Param("availableMinutes") int availableMinutes);

    /**
     * 不良原因柏拉圖分析。
     *
     * <p>算出每個原因的佔比與**累計佔比**——現場改善會議看的就是這個：
     * 前兩三項通常就佔了八成不良，資源該投在那裡。
     */
    @Query(value = """
            WITH reason_total AS (
                SELECT
                    COALESCE(r.defect_reason, 'UNSPECIFIED') AS defect_reason,
                    SUM(r.defect_qty)                        AS defect_qty
                FROM production_report r
                WHERE r.reported_at >= :from AND r.reported_at < :to
                  AND r.defect_qty > 0
                GROUP BY COALESCE(r.defect_reason, 'UNSPECIFIED')
            )
            SELECT
                t.defect_reason AS "defectReason",
                t.defect_qty    AS "defectQty",
                ROUND(CAST(t.defect_qty AS DECIMAL(18,4)) * 100
                      / SUM(t.defect_qty) OVER (), 2)               AS "sharePercent",
                ROUND(CAST(SUM(t.defect_qty) OVER (ORDER BY t.defect_qty DESC, t.defect_reason
                                                   ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
                           AS DECIMAL(18,4)) * 100
                      / SUM(t.defect_qty) OVER (), 2)               AS "cumulativePercent"
            FROM reason_total t
            ORDER BY t.defect_qty DESC, t.defect_reason
            """, nativeQuery = true)
    List<DefectPareto> defectPareto(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * 物料耗用排行，並標示低於安全庫存者。
     *
     * <p>三表 JOIN + 排名 + 子查詢彙總庫存。實務上採購最常問的就是這張表：
     * 「這個月哪些料吃最兇、哪些快沒了」。
     */
    @Query(value = """
            SELECT
                CAST(RANK() OVER (ORDER BY SUM(ABS(tx.qty)) DESC) AS INTEGER) AS "usageRank",
                m.material_code                                   AS "materialCode",
                m.material_name                                   AS "materialName",
                m.unit                                            AS "unit",
                SUM(ABS(tx.qty))                                  AS "totalConsumed",
                COALESCE(inv.total_stock, 0)                      AS "currentStock",
                m.safety_stock                                    AS "safetyStock",
                CASE WHEN COALESCE(inv.total_stock, 0) < m.safety_stock THEN TRUE
                     ELSE FALSE END                               AS "belowSafety"
            FROM material_transaction tx
            JOIN material m ON m.material_code = tx.material_code
            LEFT JOIN (
                SELECT i.material_code, SUM(i.qty_on_hand) AS total_stock
                FROM inventory i
                GROUP BY i.material_code
            ) inv ON inv.material_code = m.material_code
            WHERE tx.tx_type = 'CONSUME'
              AND tx.created_at >= :from AND tx.created_at < :to
            GROUP BY m.material_code, m.material_name, m.unit, m.safety_stock, inv.total_stock
            ORDER BY SUM(ABS(tx.qty)) DESC
            """, nativeQuery = true)
    List<MaterialUsageRank> materialUsageRank(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);
}
