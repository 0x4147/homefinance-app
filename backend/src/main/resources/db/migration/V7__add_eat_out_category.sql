-- Add the "Eat Out" expense category, counted in the shared-expense balance.
INSERT INTO category (name, kind, in_balance, description)
VALUES ('Eat Out', 'EXPENSE', TRUE, 'Eating out');
