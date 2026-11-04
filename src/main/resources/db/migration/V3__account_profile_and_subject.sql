-- V3 · 账号资料、稳定主体标识与规范化 scope
--
-- 适用阶段：Dev 试运行。按「不必考虑客户端存量」的约定，本迁移直接把协议层
-- 收敛到标准形态，不保留兼容旧数据的分支：
--   1. OIDC Core 要求 sub 在 issuer 内 locally unique 且 never reassigned。
--      以前把可变的 username 当 sub 用，改名就会让客户端账号失配；改为不可变 public_id。
--   2. nickname / preferred_username 属于 profile scope，email / email_verified 属于
--      email scope，都是标准 claim，不新造私有 scope。
--   3. 旧的 authorization / consent 记录的 principal_name 是可变 username，
--      主体标识切换后无法与新记录对应，Dev 阶段直接清空，让所有用户重新授权。
--
-- H2(MODE=PostgreSQL) 与 PostgreSQL 共用本脚本：UUID 由应用层生成并回填，
-- 不在 SQL 里调用数据库特有的随机函数。生产环境执行前请另行评估清空授权的影响。

ALTER TABLE users ADD COLUMN IF NOT EXISTS public_id varchar(36);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email varchar(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname varchar(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified boolean NOT NULL DEFAULT false;

-- 允许多个 NULL：存量行由 DataInitializer 启动时回填 public_id。
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_public_id ON users (public_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- 客户端 scope 规范化：openid + profile + email 全部显式广告/允许。
-- Dev 阶段不做「只补差异」的兼容写法，避免新旧客户端能力不一致。
UPDATE oauth2_registered_client SET scopes = 'openid,profile,email';

DELETE FROM oauth2_authorization_consent;
DELETE FROM oauth2_authorization;
