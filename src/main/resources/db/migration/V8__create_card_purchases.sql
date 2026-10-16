ALTER TABLE credit_cards
    ADD CONSTRAINT ck_credit_cards_due_after_closing CHECK (due_day > closing_day);

CREATE TABLE card_purchases (
    id UUID PRIMARY KEY,
    card_id UUID NOT NULL REFERENCES credit_cards (id),
    description VARCHAR(120) NOT NULL,
    category VARCHAR(60) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    purchase_date DATE NOT NULL,
    invoice_month DATE NOT NULL CHECK (EXTRACT(DAY FROM invoice_month) = 1),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_card_purchases_card_invoice_month
    ON card_purchases (card_id, invoice_month, purchase_date);
