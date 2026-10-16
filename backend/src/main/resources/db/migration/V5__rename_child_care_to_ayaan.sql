-- Rename the "Child Care" category to "Ayaan". Transactions and merchant mappings reference the category by
-- id, so they follow automatically; only name-based text columns need updating.
UPDATE category SET name = 'Ayaan' WHERE name = 'Child Care';

UPDATE uncategorized_transaction SET assigned_category = 'Ayaan' WHERE assigned_category = 'Child Care';
