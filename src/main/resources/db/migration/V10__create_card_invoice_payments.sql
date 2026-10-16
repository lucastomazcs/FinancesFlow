CREATE TABLE card_invoice_payments (
    id UUID PRIMARY KEY,
    card_id UUID NOT NULL REFERENCES credit_cards(id),
    invoice_month DATE NOT NULL CHECK (EXTRACT(DAY FROM invoice_month) = 1),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    paid_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_card_invoice_payment UNIQUE (card_id, invoice_month)
);
