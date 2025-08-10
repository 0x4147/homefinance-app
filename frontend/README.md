# Home Finance Frontend

A React TypeScript application for managing personal finances with a beautiful, modern UI.

## Features

- **Dashboard**: Overview of spending patterns with interactive charts
- **View Transactions**: Filter and view transactions by date range
- **Add Bulk Transactions**: Upload CSV/Excel files for batch processing
- **Spending Insights**: Detailed analysis by category, merchant, and monthly trends
- **Monthly Balance Checker**: Track shared expenses and balances
- **Add Receipt**: Upload receipts for automatic transaction creation

## Backend Integration

The frontend is now fully integrated with the Spring Boot backend API. All components use real API calls instead of mock data.

### API Endpoints Used

- `GET /api/v1/transaction/getAllTransactions` - Get all transactions
- `GET /api/v1/transaction/getTransactionsByDateRange` - Get transactions by date range
- `POST /api/v1/transaction/saveTransaction` - Save a new transaction
- `GET /api/v1/transaction/getMonthlyBalance` - Get monthly balance summary
- `GET /api/v1/transaction/getExpensesByCategory` - Get expenses grouped by category
- `GET /api/v1/transaction/getExpensesByEntity` - Get expenses grouped by merchant
- `GET /api/v1/transaction/getExpensesByMonth` - Get expenses grouped by month
- `POST /api/v1/transactionBatchUpload` - Upload files for batch processing

### Configuration

The API base URL is configured in `src/services/api.ts`. By default, it points to `http://localhost:8080/api/v1`.

## Getting Started

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the development server:
   ```bash
   npm run dev
   ```

3. Make sure the backend server is running on `http://localhost:8080`

4. Open your browser to `http://localhost:5173`

## Development

The application uses:
- React 18 with TypeScript
- Vite for build tooling
- Tailwind CSS for styling
- Chart.js for data visualization
- Lucide React for icons

## Project Structure

```
src/
├── App.tsx              # Main application component
├── services/
│   └── api.ts          # API service for backend communication
├── assets/             # Static assets
└── index.css           # Global styles
```

## API Service

The `api.ts` file contains a comprehensive service class that handles all backend communication:

- Type-safe interfaces for all API responses
- Error handling and request management
- Support for file uploads
- Automatic JSON parsing and formatting

## Components

Each major feature is implemented as a separate component:

- `Dashboard` - Overview with charts
- `ViewTransactions` - Transaction listing and filtering
- `AddBulkTransactions` - File upload for batch processing
- `SpendingInsights` - Detailed analysis tools
- `MonthlyBalanceChecker` - Shared expense tracking
- `AddReceipt` - Receipt upload (placeholder)

All components now use real API data and provide proper loading states and error handling.
