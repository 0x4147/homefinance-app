// API service for backend communication
const API_BASE_URL = 'http://localhost:8080/api/v1';

export interface Transaction {
    transactionId?: number;
    amount: number;
    date: string;
    entity: string;
    details?: string;
    account: string;
    transactionType: string;
    category: string;
    person: string;
}

export interface TransactionDto {
    amount: number;
    date: string;
    entity: string;
    details?: string;
    account: string;
    transactionType: string;
    category: string;
    person: string;
}

export interface TransactionSummary {
    totals: { [key: string]: number };
    details: { [key: string]: Transaction[] };
}

export interface MonthlyBalanceResponseDto {
    monthAndYear: string;
    whoOwes: string;
    amount: number;
    asankaTotal: number;
    divyaTotal: number;
}

// API service class
class ApiService {
    private async makeRequest<T>(endpoint: string, options?: RequestInit): Promise<T> {
        const url = `${API_BASE_URL}${endpoint}`;
        const response = await fetch(url, {
            headers: {
                'Content-Type': 'application/json',
                ...options?.headers,
            },
            ...options,
        });

        if (!response.ok) {
            throw new Error(`API request failed: ${response.status} ${response.statusText}`);
        }

        return response.json();
    }

    // Get all transactions
    async getAllTransactions(): Promise<Transaction[]> {
        return this.makeRequest<Transaction[]>('/transaction/getAllTransactions');
    }

    // Get transactions by date range
    async getTransactionsByDateRange(startDate: string, endDate: string): Promise<TransactionDto[]> {
        const params = new URLSearchParams({
            start: startDate,
            end: endDate,
        });
        return this.makeRequest<TransactionDto[]>(`/transaction/getTransactionsByDateRange?${params}`);
    }

    // Save a new transaction
    async saveTransaction(transaction: TransactionDto): Promise<Transaction> {
        return this.makeRequest<Transaction>('/transaction/saveTransaction', {
            method: 'POST',
            body: JSON.stringify(transaction),
        });
    }

    // Get monthly balance
    async getMonthlyBalance(month: string, year: string): Promise<MonthlyBalanceResponseDto> {
        const params = new URLSearchParams({ month, year });
        return this.makeRequest<MonthlyBalanceResponseDto>(`/transaction/getMonthlyBalance?${params}`);
    }

    // Get expenses by category
    async getExpensesByCategory(startDate: string, endDate: string): Promise<TransactionSummary> {
        const params = new URLSearchParams({
            startDate,
            endDate,
        });
        return this.makeRequest<TransactionSummary>(`/transaction/getExpensesByCategory?${params}`);
    }

    // Get expenses by entity (merchant)
    async getExpensesByEntity(startDate: string, endDate: string): Promise<TransactionSummary> {
        const params = new URLSearchParams({
            startDate,
            endDate,
        });
        return this.makeRequest<TransactionSummary>(`/transaction/getExpensesByEntity?${params}`);
    }

    // Get expenses by month
    async getExpensesByMonth(startMonth: string, endMonth: string): Promise<TransactionSummary> {
        const params = new URLSearchParams({
            startMonth,
            endMonth,
        });
        return this.makeRequest<TransactionSummary>(`/transaction/getExpensesByMonth?${params}`);
    }

    // Upload file for batch processing
    async uploadTransactionFile(file: File, sourceType: string): Promise<string> {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('sourceType', sourceType);

        const response = await fetch(`${API_BASE_URL}/transactionBatchUpload`, {
            method: 'POST',
            body: formData,
        });

        if (!response.ok) {
            throw new Error(`File upload failed: ${response.status} ${response.statusText}`);
        }

        return response.text();
    }
}

// Export singleton instance
export const apiService = new ApiService();
