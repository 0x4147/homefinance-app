-- Table for storing people (users) who can be involved in income and expenses
CREATE TABLE Person (
    person_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table to store categories (for income and expenses)
CREATE TABLE Category (
                          category_id INT AUTO_INCREMENT PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          type ENUM('INCOME', 'EXPENSE') NOT NULL, -- Categorizes whether it's for income or expense
                          description TEXT,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE Transaction (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(10, 2) NOT NULL,
    date DATE NOT NULL,
    entity VARCHAR(255) NOT NULL, -- Payer (for income) or Payee (for expenses)
    details TEXT,
    category_id INT,
    account VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL, -- Defines type
    person_id INT, -- Relationship with the person involved
    FOREIGN KEY (category_id) REFERENCES Category(category_id) ON DELETE SET NULL,
    FOREIGN KEY (person_id) REFERENCES Person(person_id) ON DELETE SET NULL
);

-- Table for storing receipts (associated with income or expense)
CREATE TABLE Receipt (
    receipt_id INT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    receipt_date DATE NOT NULL,
    transaction_id INT, -- Optional, link to income
    FOREIGN KEY (transaction_id) REFERENCES Transaction(transaction_id) ON DELETE CASCADE
);

-- Table to track payments between people (for shared expenses or income)
CREATE TABLE Payment (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(10, 2) NOT NULL,
    date DATE NOT NULL,
	start_date_range DATE,
	end_date_range DATE,
    from_person_id INT, -- Who made the payment
    to_person_id INT, -- Who received the payment
    transaction_id INT, -- Associated transaction (if any)
    FOREIGN KEY (from_person_id) REFERENCES Person(person_id) ON DELETE CASCADE,
    FOREIGN KEY (to_person_id) REFERENCES Person(person_id) ON DELETE CASCADE,
    FOREIGN KEY (transaction_id) REFERENCES Transaction(transaction_id) ON DELETE SET NULL
);


