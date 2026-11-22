-- Sprint 3: Over-Budget Alerts & Async Report Exports

CREATE TABLE IF NOT EXISTS budget_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    budget_id UUID NULL REFERENCES budgets(id) ON DELETE SET NULL,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    month INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year INT NOT NULL CHECK (year >= 2020),
    amount_limit NUMERIC(15, 2) NOT NULL CHECK (amount_limit > 0),
    spent_amount NUMERIC(15, 2) NOT NULL CHECK (spent_amount >= 0),
    utilization_percentage NUMERIC(7, 2) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_budget_alerts_user_created
    ON budget_alerts (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_budget_alerts_user_read
    ON budget_alerts (user_id, is_read);

CREATE INDEX IF NOT EXISTS idx_budget_alerts_user_cat_period
    ON budget_alerts (user_id, category_id, year, month);

CREATE TABLE IF NOT EXISTS report_exports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year INT NOT NULL CHECK (year >= 2020),
    format VARCHAR(10) NOT NULL CHECK (format IN ('PDF', 'CSV')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    file_name VARCHAR(255),
    file_path TEXT,
    file_url TEXT,
    content_type VARCHAR(100),
    size_bytes BIGINT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_report_exports_user_created
    ON report_exports (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_report_exports_user_status
    ON report_exports (user_id, status);
