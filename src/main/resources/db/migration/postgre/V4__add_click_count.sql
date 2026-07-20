-- 短链点击量统计字段
ALTER TABLE short_links ADD COLUMN click_count BIGINT NOT NULL DEFAULT 0;
