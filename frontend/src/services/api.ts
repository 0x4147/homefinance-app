// API service for backend communication
const API_BASE_URL = 'http://localhost:8585/api/v1';

// Console logging utility
const log = {
    info: (message: string, data?: any) => {
        console.log(`[API] ${message}`, data || '');
    },
    error: (message: string, error?: any) => {
        console.error(`[API ERROR] ${message}`, error || '');
    },
    debug: (message: string, data?: any) => {
        console.debug(`[API DEBUG] ${message}`, data || '');
    }
};

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
        log.info(`Making request to: ${url}`);
        log.debug('Request options:', options);
        
        try {
            const response = await fetch(url, {
                headers: {
                    'Content-Type': 'application/json',
                    ...options?.headers,
                },
                ...options,
            });

            log.info(`Response status: ${response.status} ${response.statusText}`);
            log.debug('Response headers:', Object.fromEntries(response.headers.entries()));

            if (!response.ok) {
                const errorText = await response.text();
                log.error(`API request failed: ${response.status} ${response.statusText}`, errorText);
                throw new Error(`API request failed: ${response.status} ${response.statusText} - ${errorText}`);
            }

            const data = await response.json();
            log.debug('Response data:', data);
            return data;
        } catch (error) {
            log.error(`Request failed for ${url}:`, error);
            throw error;
        }
    }

    // Get all transactions
    async getAllTransactions(): Promise<Transaction[]> {
        log.info('Fetching all transactions');
        return this.makeRequest<Transaction[]>('/transaction/getAllTransactions');
    }

    // Get transactions by date range
    async getTransactionsByDateRange(startDate: string, endDate: string): Promise<TransactionDto[]> {
        log.info(`Fetching transactions from ${startDate} to ${endDate}`);
        const params = new URLSearchParams({
            start: startDate,
            end: endDate,
        });
        return this.makeRequest<TransactionDto[]>(`/transaction/getTransactionsByDateRange?${params}`);
    }

    // Save a new transaction
    async saveTransaction(transaction: TransactionDto): Promise<Transaction> {
        log.info('Saving new transaction:', transaction);
        return this.makeRequest<Transaction>('/transaction/saveTransaction', {
            method: 'POST',
            body: JSON.stringify(transaction),
        });
    }

    // Get monthly balance
    async getMonthlyBalance(month: string, year: string): Promise<MonthlyBalanceResponseDto> {
        log.info(`Fetching monthly balance for ${month}/${year}`);
        const params = new URLSearchParams({ month, year });
        return this.makeRequest<MonthlyBalanceResponseDto>(`/transaction/getMonthlyBalance?${params}`);
    }

    // Get expenses by category
    async getExpensesByCategory(startDate: string, endDate: string): Promise<TransactionSummary> {
        log.info(`Fetching expenses by category from ${startDate} to ${endDate}`);
        const params = new URLSearchParams({
            startDate,
            endDate,
        });
        return this.makeRequest<TransactionSummary>(`/transaction/getExpensesByCategory?${params}`);
    }

    // Get expenses by entity (merchant)
    async getExpensesByEntity(startDate: string, endDate: string): Promise<TransactionSummary> {
        log.info(`Fetching expenses by entity from ${startDate} to ${endDate}`);
        const params = new URLSearchParams({
            startDate,
            endDate,
        });
        return this.makeRequest<TransactionSummary>(`/transaction/getExpensesByEntity?${params}`);
    }

    // Get expenses by month
    async getExpensesByMonth(startMonth: string, endMonth: string): Promise<TransactionSummary> {
        log.info(`Fetching expenses by month from ${startMonth} to ${endMonth}`);
        const params = new URLSearchParams({
            startMonth,
            endMonth,
        });
        return this.makeRequest<TransactionSummary>(`/transaction/getExpensesByMonth?${params}`);
    }

    // Upload file for batch processing
    async uploadTransactionFile(file: File, sourceType: string): Promise<string> {
        log.info(`Uploading file: ${file.name} (${file.size} bytes) for source type: ${sourceType}`);
        log.debug('File details:', {
            name: file.name,
            size: file.size,
            type: file.type,
            lastModified: new Date(file.lastModified).toISOString()
        });

        const formData = new FormData();
        formData.append('file', file);
        formData.append('sourceType', sourceType);

        const url = `${API_BASE_URL}/transactionBatchUpload`;
        log.info(`Making file upload request to: ${url}`);

        try {
            const response = await fetch(url, {
                method: 'POST',
                body: formData,
            });

            log.info(`File upload response status: ${response.status} ${response.statusText}`);
            log.debug('File upload response headers:', Object.fromEntries(response.headers.entries()));

            if (!response.ok) {
                const errorText = await response.text();
                log.error(`File upload failed: ${response.status} ${response.statusText}`, errorText);
                throw new Error(`File upload failed: ${response.status} ${response.statusText} - ${errorText}`);
            }

            const result = await response.text();
            log.info('File upload successful:', result);
            return result;
        } catch (error) {
            log.error(`File upload request failed for ${url}:`, error);
            throw error;
        }
    }
}

// Export singleton instance
export const apiService = new ApiService();
