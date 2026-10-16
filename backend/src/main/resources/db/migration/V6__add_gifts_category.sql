-- Add the "Gifts" expense category (gifts bought for others), counted in the shared-expense balance.
INSERT INTO category (name, kind, in_balance, description)
VALUES ('Gifts', 'EXPENSE', TRUE, 'Gifts bought for others');
