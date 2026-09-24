CREATE TABLE payments (
  id UUID PRIMARY KEY,
  source_account_id UUID NOT NULL,
  destination_account_id UUID NOT NULL,
  amount NUMERIC(19, 4) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(50) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,

  CONSTRAINT fk_payments_source_account
      FOREIGN KEY (source_account_id)
          REFERENCES accounts(id),

  CONSTRAINT fk_payments_destination_account
      FOREIGN KEY (destination_account_id)
          REFERENCES accounts(id),

  CONSTRAINT chk_payments_amount_positive
      CHECK (amount > 0)
);

CREATE INDEX idx_payments_source_account_id
    ON payments(source_account_id);

CREATE INDEX idx_payments_destination_account_id
    ON payments(destination_account_id);