CREATE INDEX IF NOT EXISTS idx_transactions_user_type_date
    ON transactions (user_id, type, transaction_date);

CREATE INDEX IF NOT EXISTS idx_budgets_user_period
    ON budgets (user_id, year, month);
