-- Table for storing people (users) who can be involved in income and expenses
CREATE TABLE person (
    person_id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE, -- Stable identifier used by business logic (matches Transaction account names, e.g. ASANKA)
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table to store categories (for income and expenses)
CREATE TABLE category (
                          category_id INT AUTO_INCREMENT PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          type ENUM('INCOME', 'EXPENSE') NOT NULL, -- Categorizes whether it's for income or expense
                          description TEXT,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE uncategorized_transaction (
    id INT AUTO_INCREMENT PRIMARY KEY,
    merchant VARCHAR(255) NOT NULL,
    details TEXT,
    amount DECIMAL(10,2) NOT NULL,
    date DATE NOT NULL,
    suggested_categories TEXT,
    confidence_score DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed BOOLEAN DEFAULT FALSE,
    assigned_category VARCHAR(255),
    reviewed_at TIMESTAMP NULL
);

CREATE TABLE transaction (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(10, 2) NOT NULL,
    date DATE NOT NULL,
    entity VARCHAR(255) NOT NULL, -- Payer (for income) or Payee (for expenses)
    details TEXT,
    category_id INT,
    uncategorized_transaction_id INT,
    account VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL, -- Defines type
    person_id INT, -- Relationship with the person involved
    FOREIGN KEY (category_id) REFERENCES category(category_id) ON DELETE SET NULL,
    FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE SET NULL,
    FOREIGN KEY (uncategorized_transaction_id) REFERENCES uncategorized_transaction(id) ON DELETE SET NULL
);

-- Table for storing receipts (associated with income or expense)
CREATE TABLE receipt (
    receipt_id INT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    receipt_date DATE NOT NULL,
    transaction_id INT, -- Optional, link to income
    FOREIGN KEY (transaction_id) REFERENCES transaction(transaction_id) ON DELETE CASCADE
);

-- Cards (as identified in bank exports, e.g. a masked card number) mapped to the person who holds them
CREATE TABLE person_card (
    person_card_id INT AUTO_INCREMENT PRIMARY KEY,
    person_id INT NOT NULL,
    card_identifier VARCHAR(50) NOT NULL UNIQUE, -- Value exactly as it appears in the export, e.g. 5223********5844
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE CASCADE
);

-- One row per calendar month recording that the month's shared-expense balance was paid
CREATE TABLE settlement (
    settlement_id INT AUTO_INCREMENT PRIMARY KEY,
    period_year SMALLINT NOT NULL,
    period_month TINYINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    paid_date DATE NOT NULL,
    from_person_id INT NOT NULL, -- Person who owed and paid
    to_person_id INT NOT NULL, -- Person who received the payment
    notes VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_settlement_period UNIQUE (period_year, period_month),
    CONSTRAINT chk_settlement_amount CHECK (amount > 0),
    CONSTRAINT chk_settlement_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT chk_settlement_people CHECK (from_person_id <> to_person_id),
    FOREIGN KEY (from_person_id) REFERENCES person(person_id),
    FOREIGN KEY (to_person_id) REFERENCES person(person_id)
);

-- Categorization rules for merchants
CREATE TABLE merchant_rule (
    id INT AUTO_INCREMENT PRIMARY KEY,
    normalized_merchant VARCHAR(255) NOT NULL UNIQUE,
    category_id INT NOT NULL,
    source VARCHAR(10) NOT NULL, -- SEED (V2 static rules), HISTORY (past transactions), USER (review decision)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES category(category_id) ON DELETE CASCADE
);


CREATE INDEX idx_transaction_category_id ON transaction(category_id);
CREATE INDEX idx_transaction_person_id ON transaction(person_id);
CREATE INDEX idx_transaction_uncat_id ON transaction(uncategorized_transaction_id);
CREATE INDEX idx_receipt_transaction_id ON receipt(transaction_id);
CREATE INDEX idx_person_card_person_id ON person_card(person_id);

CREATE INDEX idx_uncategorized_reviewed ON uncategorized_transaction(reviewed);
CREATE INDEX idx_uncategorized_created_at ON uncategorized_transaction(created_at);
CREATE INDEX idx_uncategorized_merchant ON uncategorized_transaction(merchant);
