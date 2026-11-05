-- V4 · 邮箱大小写归一化
--
-- 背景：V3 的 idx_users_email 是大小写敏感的唯一索引，而写入路径此前只做 trim，
-- 于是 A@x.com 与 a@x.com 可以同时存在，findByEmail 也查不到大小写不同的那一行。
-- 应用层现在统一小写写入（EmailPolicy）+ 忽略大小写查询（findByEmailIgnoreCase），
-- 本迁移负责把存量数据规整到同一口径。
--
-- 兼容性：只用标准 UPDATE / lower()，PostgreSQL 与 H2(MODE=PostgreSQL) 均可执行，
-- 集成测试每次启动都会真跑一遍。刻意不改成 lower(email) 的函数/部分唯一索引：
-- H2 对该语法支持不一致，会让测试库与生产库跑在不同的 schema 上。
-- 唯一性由「应用层归一化 + 原有精确唯一索引」共同保证。
--
-- 上线前可先用下面语句排查是否存在仅大小写不同的重复邮箱（应返回 0 行）：
--   SELECT lower(email) AS dup, count(*) FROM users
--    WHERE email IS NOT NULL GROUP BY lower(email) HAVING count(*) > 1;

-- 1) 消解历史脏数据：仅大小写不同的重复邮箱，保留 id 最小的一行，其余清空邮箱
--    并重置 email_verified，避免下一步小写化撞唯一索引、导致 Flyway 失败并中止启动。
--    邮箱在本系统只是 email scope 的展示型 claim，不是登录凭据也不是找回通道，
--    清空后由用户重新填写即可；若确实存在重复，发布后应通知相关账号核对资料。
UPDATE users
   SET email = NULL, email_verified = false
 WHERE id IN (
       SELECT dup.id
         FROM users dup
        WHERE dup.email IS NOT NULL
          AND EXISTS (
                SELECT 1 FROM users keeper
                 WHERE keeper.id < dup.id
                   AND lower(keeper.email) = lower(dup.email)
          )
 );

-- 2) 存量邮箱统一小写；WHERE 条件让已合规的行不产生无谓写入。
UPDATE users
   SET email = lower(email)
 WHERE email IS NOT NULL
   AND email <> lower(email);
