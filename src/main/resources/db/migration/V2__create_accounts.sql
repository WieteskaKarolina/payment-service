CREATE TABLE accounts (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  currency VARCHAR(3) NOT NULL,
  balance NUMERIC(19, 4) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

  CONSTRAINT fk_accounts_user
      FOREIGN KEY (user_id)
          REFERENCES users(id),

  CONSTRAINT chk_accounts_balance_non_negative
      CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_user_id
    ON accounts(user_id);