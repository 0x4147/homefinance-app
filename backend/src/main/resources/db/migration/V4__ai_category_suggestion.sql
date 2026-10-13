-- AI-proposed category for a pending review row. Suggestion only: assigned_category/reviewed are set
-- exclusively by a user decision.
ALTER TABLE uncategorized_transaction
    ADD COLUMN ai_suggested_category VARCHAR(255) NULL,
    ADD COLUMN ai_confidence DOUBLE NULL;
