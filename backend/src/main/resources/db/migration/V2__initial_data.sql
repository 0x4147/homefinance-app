-- Person
INSERT INTO person (code, name, email)
VALUES
    ('ASANKA', 'Asanka', 'asanka.nz@gmail.com'),
    ('DIVYA', 'Divya', 'divyamehta.nz@gmail.com');

-- Category
INSERT INTO category (name, type)
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


-- PersonCard
INSERT INTO person_card (person_id, card_identifier)
SELECT person_id, '5223********5844' FROM person WHERE code = 'ASANKA';
INSERT INTO person_card (person_id, card_identifier)
SELECT person_id, '5223********3406' FROM person WHERE code = 'DIVYA';


-- merchant_rule
INSERT INTO merchant_rule (normalized_merchant, category_id, source)
VALUES
    ('costco', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('fortinos', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('supermarket', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('no fr', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('no fri', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('freshco', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('wal mart', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('grocery', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('food basics', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('sobeys', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('swadesh', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('walmart', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('desi mandi', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('freshii', (SELECT category_id FROM category WHERE name = 'Groceries'), 'SEED'),
    ('kardamom kitchen', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('shawarma', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('spring sushi', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('tim hortons', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('popeyes', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('mcdonalds', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('mcdonald', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('originalshawarma', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('cafe', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('restaurant', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('castelli', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('mcdonald s', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('chick fil a', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('burger king', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('crepe delicious', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('szechuan express', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('demetres 8', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('thai express', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('scaddabush', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('starbucks', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('subway', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('pizza', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('burrito', (SELECT category_id FROM category WHERE name = 'Take Out'), 'SEED'),
    ('midas', (SELECT category_id FROM category WHERE name = 'Car repair'), 'SEED'),
    ('parking', (SELECT category_id FROM category WHERE name = 'Transportation'), 'SEED'),
    ('honk', (SELECT category_id FROM category WHERE name = 'Transportation'), 'SEED'),
    ('esso', (SELECT category_id FROM category WHERE name = 'Transportation'), 'SEED'),
    ('pioneer', (SELECT category_id FROM category WHERE name = 'Transportation'), 'SEED'),
    ('uber', (SELECT category_id FROM category WHERE name = 'Transportation'), 'SEED'),
    ('peoples pharmachoice', (SELECT category_id FROM category WHERE name = 'Healthcare'), 'SEED'),
    ('shoppers drug mart', (SELECT category_id FROM category WHERE name = 'Healthcare'), 'SEED'),
    ('st joseph', (SELECT category_id FROM category WHERE name = 'Healthcare'), 'SEED'),
    ('mcmaster', (SELECT category_id FROM category WHERE name = 'Healthcare'), 'SEED'),
    ('st joseph s', (SELECT category_id FROM category WHERE name = 'Healthcare'), 'SEED'),
    ('healthcare', (SELECT category_id FROM category WHERE name = 'Healthcare'), 'SEED'),
    ('rexall', (SELECT category_id FROM category WHERE name = 'Healthcare'), 'SEED'),
    ('amazon', (SELECT category_id FROM category WHERE name = 'Amazon'), 'SEED'),
    ('amzn', (SELECT category_id FROM category WHERE name = 'Amazon'), 'SEED'),
    ('temu', (SELECT category_id FROM category WHERE name = 'Temu'), 'SEED'),
    ('the home depot', (SELECT category_id FROM category WHERE name = 'Home maintenance'), 'SEED'),
    ('ikea', (SELECT category_id FROM category WHERE name = 'Home maintenance'), 'SEED'),
    ('canadian tire', (SELECT category_id FROM category WHERE name = 'Home maintenance'), 'SEED'),
    ('best buy', (SELECT category_id FROM category WHERE name = 'Home maintenance'), 'SEED'),
    ('dollarama', (SELECT category_id FROM category WHERE name = 'Home maintenance'), 'SEED'),
    ('reliance', (SELECT category_id FROM category WHERE name = 'Home maintenance'), 'SEED'),
    ('lcbo', (SELECT category_id FROM category WHERE name = 'Alcohol'), 'SEED'),
    ('the beer store', (SELECT category_id FROM category WHERE name = 'Alcohol'), 'SEED'),
    ('netflix', (SELECT category_id FROM category WHERE name = 'Alcohol'), 'SEED'),
    ('payment', (SELECT category_id FROM category WHERE name = 'Card payment'), 'SEED'),
    ('thank you', (SELECT category_id FROM category WHERE name = 'Card payment'), 'SEED'),
    ('wfcuconditional', (SELECT category_id FROM category WHERE name = 'Card loan'), 'SEED'),
    ('alectra', (SELECT category_id FROM category WHERE name = 'Utility Bill'), 'SEED'),
    ('enbridge', (SELECT category_id FROM category WHERE name = 'Utility Bill'), 'SEED'),
    ('old navy', (SELECT category_id FROM category WHERE name = 'Clothing'), 'SEED'),
    ('h m', (SELECT category_id FROM category WHERE name = 'Clothing'), 'SEED'),
    ('carters', (SELECT category_id FROM category WHERE name = 'Clothing'), 'SEED');
