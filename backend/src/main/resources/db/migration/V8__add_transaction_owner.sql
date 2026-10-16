-- Who a transaction is really for. NULL = split between both people (the default). When set, that person owes the
-- whole amount even though the account's payer settled it, e.g. a personal purchase put on a shared card for the rewards.
ALTER TABLE transaction
    ADD COLUMN owner_person_id INT NULL,
    ADD CONSTRAINT fk_transaction_owner FOREIGN KEY (owner_person_id) REFERENCES person(person_id) ON DELETE SET NULL;
