ALTER TABLE card_purchases
    ADD COLUMN installment_count INTEGER NOT NULL DEFAULT 1
        CHECK (installment_count BETWEEN 1 AND 48);
