# Monthly Balance Endpoint - Comprehensive Test Suite

This test suite provides comprehensive validation of the `/getMonthlyBalance` endpoint to ensure the balance calculation logic is accurate and handles all edge cases correctly.

## Test Structure

### 1. Controller Tests (`TransactionControllerTest.java`)
- **Purpose**: Tests the HTTP endpoint behavior and response format
- **Coverage**: 
  - Basic balance calculations
  - Complex scenarios with multiple transaction types
  - Error handling for invalid inputs
  - Edge cases (leap years, decimal precision)
  - Response format validation

### 2. Service Tests (`TransactionServiceTest.java`)
- **Purpose**: Tests the service layer business logic
- **Coverage**:
  - Transaction filtering by date range
  - Account type filtering
  - Transaction type filtering
  - Edge cases (empty results, null parameters)
  - Cross-month boundary handling

### 3. Integration Tests (`MonthlyBalanceIntegrationTest.java`)
- **Purpose**: Tests the complete flow with real database
- **Coverage**:
  - End-to-end scenarios with realistic data
  - Database transaction handling
  - Complex rental income scenarios
  - Date range filtering validation
  - Transaction persistence and retrieval

### 4. Validation Tests (`MonthlyBalanceValidationTest.java`)
- **Purpose**: Validates business logic and edge cases
- **Coverage**:
  - Mathematical calculations
  - Rounding mode validation (HALF_UP)
  - Card payment negation logic
  - Rental income subtraction logic
  - Complex multi-transaction scenarios

## Business Logic Validation

### Core Calculation Logic
The monthly balance calculation follows this formula:

```
For each person:
Net Amount = Expenses + Card Payments + Bills - Rental Bill Income - Rental Rent Income

For each person:
Share = Net Amount / 2

Balance = Divya's Share - Asanka's Share

If Balance > 0: Asanka owes Divya
If Balance < 0: Divya owes Asanka
If Balance = 0: No one owes anything
```

### Transaction Types Handled
1. **EXPENSE** - Regular expenses from personal accounts (ASANKA, DIVYA)
2. **CARDPAYMENT** - Credit card payments (CIBC, AMEX) - amounts are negated
3. **BILL** - Bills paid from personal accounts
4. **RENTALBILLINCOME** - Rental bill income (subtracted from expenses)
5. **RENTALRENTINCOME** - Rental rent income (subtracted from expenses)

### Key Business Rules Validated
1. **Rounding**: Uses `RoundingMode.HALF_UP` for division by 2
2. **Card Payments**: Amounts are negated (subtracted from total)
3. **Rental Income**: Both types are subtracted from expenses
4. **Date Filtering**: Only transactions within the specified month/year are included
5. **Account Mapping**: 
   - Person ID 1 = Asanka
   - Person ID 2 = Divya
6. **Balance Direction**: Positive difference means Asanka owes, negative means Divya owes

## Test Scenarios Covered

### Basic Scenarios
- ✅ Equal payments (zero balance)
- ✅ Asanka owes Divya
- ✅ Divya owes Asanka
- ✅ No transactions (zero balance)

### Complex Scenarios
- ✅ Multiple transaction types per person
- ✅ Rental income exceeding expenses (negative net)
- ✅ Card payments with negation
- ✅ Mixed account types (personal + credit cards)
- ✅ Cross-month boundary transactions

### Edge Cases
- ✅ Leap year February (29 days)
- ✅ Non-leap year February (28 days)
- ✅ Very small amounts (precision handling)
- ✅ Very large amounts (precision handling)
- ✅ Decimal precision and rounding
- ✅ Invalid month/year inputs

### Error Handling
- ✅ Invalid month format
- ✅ Invalid year format
- ✅ Month 0 or 13
- ✅ NumberFormatException handling

## Running the Tests

### Run All Tests
```bash
mvn test
```

### Run Specific Test Classes
```bash
# Controller tests
mvn test -Dtest=TransactionControllerTest

# Service tests
mvn test -Dtest=TransactionServiceTest

# Integration tests
mvn test -Dtest=MonthlyBalanceIntegrationTest

# Validation tests
mvn test -Dtest=MonthlyBalanceValidationTest
```

### Run with Coverage
```bash
mvn test jacoco:report
```

## Test Data Setup

### Integration Tests
- Uses H2 in-memory database
- Creates test persons (Asanka, Divya)
- Creates test categories
- Cleans up data between tests using `@Transactional`

### Mock Tests
- Uses Mockito for service layer mocking
- Creates realistic transaction data
- Validates exact calculation results

## Expected Test Results

All tests should pass with the following validations:

1. **Mathematical Accuracy**: All calculations match expected business logic
2. **Response Format**: JSON responses contain correct fields and values
3. **Error Handling**: Invalid inputs throw appropriate exceptions
4. **Edge Cases**: All edge cases are handled correctly
5. **Database Integration**: Real database scenarios work correctly

## Test Coverage

The test suite provides comprehensive coverage of:
- ✅ Happy path scenarios
- ✅ Error conditions
- ✅ Edge cases
- ✅ Business rule validation
- ✅ Integration scenarios
- ✅ Performance considerations (large datasets)

## Maintenance

When modifying the monthly balance calculation logic:
1. Update the corresponding test cases
2. Add new test scenarios for new business rules
3. Ensure all existing tests still pass
4. Update this documentation if business logic changes

## Troubleshooting

### Common Issues
1. **Test failures due to rounding**: Check that `RoundingMode.HALF_UP` is used consistently
2. **Date range issues**: Verify that month boundaries are calculated correctly
3. **Mock setup issues**: Ensure all required service methods are mocked
4. **Database issues**: Check that test database is properly configured

### Debug Tips
1. Enable SQL logging in test configuration
2. Use debugger to step through calculation logic
3. Add logging statements to understand data flow
4. Verify mock return values match expected data
