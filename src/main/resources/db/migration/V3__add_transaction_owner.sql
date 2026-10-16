ALTER TABLE transactions
    ADD COLUMN user_id UUID;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_user
    FOREIGN KEY (user_id) REFERENCES app_users (id);

CREATE INDEX idx_transactions_user_date
    ON transactions (user_id, transaction_date);
