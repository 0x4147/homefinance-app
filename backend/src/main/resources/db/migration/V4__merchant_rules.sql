CREATE TABLE merchant_rule (
    id INT AUTO_INCREMENT PRIMARY KEY,
    normalized_merchant VARCHAR(255) NOT NULL UNIQUE,
    category_id INT NOT NULL,
    source VARCHAR(10) NOT NULL, -- SEED (V5 static rules), HISTORY (past transactions), USER (review decision)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES Category(category_id) ON DELETE CASCADE
);
