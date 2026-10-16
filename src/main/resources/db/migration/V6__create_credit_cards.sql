CREATE TABLE credit_cards (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users (id),
    name VARCHAR(60) NOT NULL,
    credit_limit NUMERIC(12, 2) NOT NULL CHECK (credit_limit > 0),
    closing_day SMALLINT NOT NULL CHECK (closing_day BETWEEN 1 AND 28),
    due_day SMALLINT NOT NULL CHECK (due_day BETWEEN 1 AND 28),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_credit_cards_owner_name
    ON credit_cards (owner_id, LOWER(name));
