ALTER TABLE payments
    ADD COLUMN idempotency_key VARCHAR(100);

ALTER TABLE payments
    ADD CONSTRAINT uq_payments_user_idempotency_key
        UNIQUE (source_account_id, idempotency_key);