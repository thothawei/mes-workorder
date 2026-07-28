-- ============================================================
-- V2 示範主檔資料
--
-- 情境設定為台中常見的精密機械加工：生產一款線性滑軌滑座。
-- 資料量刻意很小（3 種物料、2 張工單），讓人 3 分鐘內看懂整個流程。
--
-- 使用者帳號不寫在這裡——密碼雜湊由程式在啟動時以 BCrypt 產生，
-- 見 DemoDataInitializer。把雜湊硬編在 SQL 裡既難維護也容易外流。
-- ============================================================

-- ---------- 物料 ----------
INSERT INTO material (material_code, material_name, unit, safety_stock, created_at, created_by) VALUES
('M-STEEL-01', 'S45C 中碳鋼棒 φ50', 'KG',  500.0000, CURRENT_TIMESTAMP, 'SYSTEM'),
('M-BALL-01',  '鋼珠 φ3.175',       'PCS', 20000.0000, CURRENT_TIMESTAMP, 'SYSTEM'),
('M-SEAL-01',  '端蓋油封組',        'SET', 300.0000, CURRENT_TIMESTAMP, 'SYSTEM');

-- ---------- 用料表：一件滑座的組成 ----------
-- scrap_rate 為製程損耗：鋼棒車削損耗高（3%），鋼珠幾乎不損耗（0.5%）
INSERT INTO bom_item (product_code, material_code, qty_per_unit, scrap_rate, created_at, created_by) VALUES
('P-RAIL-100', 'M-STEEL-01', 1.2000, 0.0300, CURRENT_TIMESTAMP, 'SYSTEM'),
('P-RAIL-100', 'M-BALL-01',  48.0000, 0.0050, CURRENT_TIMESTAMP, 'SYSTEM'),
('P-RAIL-100', 'M-SEAL-01',  2.0000, 0.0000, CURRENT_TIMESTAMP, 'SYSTEM');

-- ---------- 期初庫存 ----------
-- M-SEAL-01 刻意只給 100 SET。一件滑座用 2 SET，所以剛好夠做 50 件；
-- 報到第 51 件就會庫存不足，整筆報工回滾。
-- 這樣不必自己造資料就能直接示範「交易一致性」——這是本專案最想展示的行為。
INSERT INTO inventory (material_code, warehouse_code, qty_on_hand, version, created_at, created_by) VALUES
('M-STEEL-01', 'WH-RAW', 2000.0000, 0, CURRENT_TIMESTAMP, 'SYSTEM'),
('M-BALL-01',  'WH-RAW', 80000.0000, 0, CURRENT_TIMESTAMP, 'SYSTEM'),
('M-SEAL-01',  'WH-RAW', 100.0000, 0, CURRENT_TIMESTAMP, 'SYSTEM');

-- ---------- 示範工單 ----------
-- WO-20260728-001：已派工，可直接報工
-- WO-20260728-002：草稿，用來示範派工流程
INSERT INTO work_order (order_no, product_code, product_name, planned_qty, produced_qty, defect_qty,
                        status, line_code, planned_start, planned_end, version, created_at, created_by) VALUES
('WO-20260728-001', 'P-RAIL-100', '線性滑軌滑座 100mm', 60, 0, 0,
 'RELEASED', 'LINE-A', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 'SYSTEM'),
('WO-20260728-002', 'P-RAIL-100', '線性滑軌滑座 100mm', 40, 0, 0,
 'DRAFT', 'LINE-B', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 'SYSTEM');
