ALTER TABLE dt_account ADD COLUMN account_type VARCHAR(8) NOT NULL DEFAULT 'USER'
    CHECK (account_type IN ('USER', 'ADMIN'));
