CREATE TABLE UncategorizedTransaction (
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

-- Add index for better performance
CREATE INDEX idx_uncategorized_reviewed ON UncategorizedTransaction(reviewed);
CREATE INDEX idx_uncategorized_created_at ON UncategorizedTransaction(created_at);
CREATE INDEX idx_uncategorized_merchant ON UncategorizedTransaction(merchant);


