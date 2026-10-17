-- A trip groups transactions for reporting (e.g. a family holiday). It is only a label: categories, the shared flag and
-- the monthly balance are unaffected. Deleting a trip untags its transactions (ON DELETE SET NULL), never deletes them.
CREATE TABLE trip (
    trip_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    budget DECIMAL(10, 2) NULL,
    CONSTRAINT chk_trip_dates CHECK (end_date >= start_date)
);

ALTER TABLE transaction
    ADD COLUMN trip_id INT NULL,
    ADD CONSTRAINT fk_transaction_trip FOREIGN KEY (trip_id) REFERENCES trip(trip_id) ON DELETE SET NULL;
