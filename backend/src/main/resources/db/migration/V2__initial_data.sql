INSERT INTO Person (code, name, email)
VALUES
    ('ASANKA', 'Asanka', 'asanka.nz@gmail.com'),
    ('DIVYA', 'Divya', 'divyamehta.nz@gmail.com');

INSERT INTO Category (name, type)
VALUES
    ('Utility Bill', 'EXPENSE'),
    ('Groceries', 'EXPENSE'),
    ('Transportation', 'EXPENSE'),
    ('Healthcare', 'EXPENSE'),
    ('Entertainment', 'EXPENSE'),
    ('Mortgage', 'EXPENSE'),
    ('Property tax', 'EXPENSE'),
    ('Insurance', 'EXPENSE'),
    ('Car repair', 'EXPENSE'),
    ('Home maintenance', 'EXPENSE'),
    ('Salary', 'INCOME'),
    ('Rental Income', 'INCOME'),
    ('Government Grants', 'INCOME'),
    ('Take Out', 'EXPENSE'),
    ('Amazon', 'EXPENSE'),
    ('Temu', 'EXPENSE'),
    ('Card payment', 'INCOME'),
    ('Card loan', 'EXPENSE'),
    ('Alcohol', 'EXPENSE'),
    ('Clothing', 'EXPENSE');




INSERT INTO PersonCard (person_id, card_identifier)
SELECT person_id, '5223********5844' FROM Person WHERE code = 'ASANKA';

INSERT INTO PersonCard (person_id, card_identifier)
SELECT person_id, '5223********3406' FROM Person WHERE code = 'DIVYA';
