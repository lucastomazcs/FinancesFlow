CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users (id),
    category VARCHAR(60) NOT NULL,
    budget_month DATE NOT NULL CHECK (EXTRACT(DAY FROM budget_month) = 1),
    monthly_limit NUMERIC(12, 2) NOT NULL CHECK (monthly_limit > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_budgets_owner_category_month
    ON budgets (owner_id, LOWER(category), budget_month);
