ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS goal_years INT NOT NULL DEFAULT 3;
ALTER TABLE app_settings ADD COLUMN IF NOT EXISTS goal_start_date DATE NOT NULL DEFAULT CURRENT_DATE;

CREATE TABLE IF NOT EXISTS monthly_budgets (
    id BIGSERIAL PRIMARY KEY,
    month_key VARCHAR(7) NOT NULL UNIQUE,
    amount NUMERIC(15, 0) NOT NULL DEFAULT 0,
    copied_from_previous BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO monthly_budgets (month_key, amount, copied_from_previous)
SELECT TO_CHAR(CURRENT_DATE, 'YYYY-MM'), monthly_budget, FALSE
FROM app_settings
WHERE NOT EXISTS (SELECT 1 FROM monthly_budgets);
