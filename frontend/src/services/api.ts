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
    uncategorizedTransaction?: { id: number } | null;
}

export interface TransactionDto {
    amount: number;
    date: string;
    entity: string;
    details?: string;
    account: string;
    transactionType: string;
    category?: string;
    person: string;
}

export interface TransactionSummary {
    totals: { [key: string]: number };
    details: { [key: string]: Transaction[] };
}

export interface SettlementDto {
    year: number;
    month: number;
    amount: number;
    paidDate: string;
    notes?: string | null;
    from: string;
    to: string;
}

export interface SettlementRequest {
    amount: number;
    paidDate: string;
    notes?: string;
}

export interface BalanceLine {
    transaction: TransactionDto;
    contribution: number;
    kind: string;
}

export type SettlementStatus = 'SETTLED' | 'UNSETTLED' | 'EVEN';

export interface MonthlyBalanceResponseDto {
    monthAndYear: string;
    whoOwes: string | null;
    balanceAmount: number;
    asankaPaid: number;
    divyaPaid: number;
    month: number;
    year: number;
    status: SettlementStatus;
    settlement?: SettlementDto | null;
    settlementMismatch: boolean;
}

export interface UncategorizedTransaction {
    id: number;
    merchant: string;
    details: string;
    amount: number;
    date: string;
    suggestedCategories: string;
    confidenceScore: number;
    createdAt: string;
    reviewed: boolean;
    assignedCategory?: string;
    reviewedAt?: string;
}

export interface ReviewGroup {
    key: string;
    merchant: string;
    count: number;
    total: number;
    suggestions: string[];
    ids: number[];
}

export interface Category {
    categoryId: number;
    name: string;
    type: string;
    description?: string;
}

export interface Person {
    personId: number;
    code: string;
    name: string;
}

export interface AthenaInsightsResponse {
    analysis: string;
}

export interface AthenaChatResponse {
    answer: string;
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

            if (response.status === 204) {
                return undefined as T;
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
    async saveTransaction(transaction: TransactionDto, allowDuplicate = false): Promise<Transaction> {
        log.info('Saving new transaction:', transaction);
        const params = new URLSearchParams({ allowDuplicate: String(allowDuplicate) });
        return this.makeRequest<Transaction>(`/transaction/saveTransaction?${params}`, {
            method: 'POST',
            body: JSON.stringify(transaction),
        });
    }

    async getPersons(): Promise<Person[]> {
        return this.makeRequest<Person[]>('/person/getAllPersons');
    }

    // Get monthly balance
    async getMonthlyBalance(month: string, year: string): Promise<MonthlyBalanceResponseDto> {
        log.info(`Fetching monthly balance for ${month}/${year}`);
        const params = new URLSearchParams({ month, year });
        return this.makeRequest<MonthlyBalanceResponseDto>(`/transaction/getMonthlyBalance?${params}`);
    }

    // Transactions behind one person's "paid" figure for a month
    async getMonthlyBalanceTransactions(month: number, year: number, person: 'ASANKA' | 'DIVYA'): Promise<BalanceLine[]> {
        const params = new URLSearchParams({ month: String(month), year: String(year), person });
        return this.makeRequest<BalanceLine[]>(`/transaction/getMonthlyBalanceTransactions?${params}`);
    }

    // Settlements (recorded payments of a month's balance)
    async getSettlements(year: number): Promise<SettlementDto[]> {
        return this.makeRequest<SettlementDto[]>(`/settlement?year=${year}`);
    }

    // Creates the month's settlement, or replaces it if one exists
    async saveSettlement(year: number, month: number, body: SettlementRequest): Promise<SettlementDto> {
        log.info(`Saving settlement for ${month}/${year}`, body);
        return this.makeRequest<SettlementDto>(`/settlement/${year}/${month}`, {
            method: 'PUT',
            body: JSON.stringify(body),
        });
    }

    async deleteSettlement(year: number, month: number): Promise<void> {
        log.info(`Deleting settlement for ${month}/${year}`);
        return this.makeRequest<void>(`/settlement/${year}/${month}`, { method: 'DELETE' });
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

    // Get expenses by category Top 10
    async getExpensesByCategoryTop10(startDate: string, endDate: string): Promise<TransactionSummary> {
        log.info(`Fetching expenses by category from ${startDate} to ${endDate}`);
        const params = new URLSearchParams({
            startDate,
            endDate,
        });
        return this.makeRequest<TransactionSummary>(`/transaction/getExpensesByCategoryTop10?${params}`);
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

    // Get expenses by entity (merchant) Top 10
    async getExpensesByEntityTop10(startDate: string, endDate: string): Promise<TransactionSummary> {
        log.info(`Fetching expenses by entity from ${startDate} to ${endDate}`);
        const params = new URLSearchParams({
            startDate,
            endDate,
        });
        return this.makeRequest<TransactionSummary>(`/transaction/getExpensesByEntityTop10?${params}`);
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

    // Categorization endpoints
    async getUncategorizedTransactions(): Promise<UncategorizedTransaction[]> {
        log.info('Fetching uncategorized transactions');
        return this.makeRequest<UncategorizedTransaction[]>('/categorization/uncategorized');
    }

    async getAllCategories(): Promise<Category[]> {
        log.info('Fetching all categories');
        return this.makeRequest<Category[]>('/categorization/categories');
    }

    async reviewUncategorizedTransaction(uncategorizedId: number, assignedCategory: string): Promise<string> {
        log.info(`Reviewing uncategorized transaction ${uncategorizedId} with category ${assignedCategory}`);
        const params = new URLSearchParams({
            uncategorizedId: uncategorizedId.toString(),
            assignedCategory,
        });
        return this.makeRequest<string>('/categorization/review', {
            method: 'POST',
            body: params.toString(),
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
        });
    }

    async getUncategorizedGroups(): Promise<ReviewGroup[]> {
        log.info('Fetching uncategorized transactions grouped by merchant');
        return this.makeRequest<ReviewGroup[]>('/categorization/uncategorized/groups');
    }

    async reviewGroup(merchantKey: string, assignedCategory: string): Promise<{ resolved: number }> {
        log.info(`Categorizing merchant group ${merchantKey} as ${assignedCategory}`);
        const params = new URLSearchParams({ merchantKey, assignedCategory });
        return this.makeRequest<{ resolved: number }>('/categorization/review/group', {
            method: 'POST',
            body: params.toString(),
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
        });
    }

    async recategorizePending(): Promise<{ resolved: number; remaining: number }> {
        log.info('Re-running categorization rules over pending reviews');
        return this.makeRequest<{ resolved: number; remaining: number }>('/categorization/recategorize', {
            method: 'POST',
        });
    }

    async getCategorizationStats(): Promise<{ totalTransactions: number; categorizedTransactions: number; uncategorizedTransactions: number }> {
        log.info('Fetching categorization statistics');
        return this.makeRequest<{ totalTransactions: number; categorizedTransactions: number; uncategorizedTransactions: number }>('/categorization/stats');
    }

    async getUncategorizedCount(): Promise<{ uncategorizedCount: number }> {
        log.info('Fetching uncategorized count');
        return this.makeRequest<{ uncategorizedCount: number }>('/categorization/uncategorized/count');
    }

    async categorizeTransaction(merchant: string, details: string, amount: string, date: string): Promise<{ merchant: string; category: string; autoCategorized: boolean }> {
        log.info(`Auto-categorizing transaction for merchant: ${merchant}`);
        const params = new URLSearchParams({
            merchant,
            details: details || '',
            amount,
            date,
        });
        return this.makeRequest<{ merchant: string; category: string; autoCategorized: boolean }>('/categorization/categorize', {
            method: 'POST',
            body: params.toString(),
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
        });
    }

    // Athena: dashboard insights (last 3 months)
    async getAthenaInsights(): Promise<AthenaInsightsResponse> {
        log.info('Fetching Athena insights');
        return this.makeRequest<AthenaInsightsResponse>('/athena/insights');
    }

    // Athena: chat with optional date range
    async athenaChat(question: string, start?: string, end?: string): Promise<AthenaChatResponse> {
        log.info('Sending Athena chat question');
        const params = new URLSearchParams();
        params.set('question', question);
        if (start) params.set('start', start);
        if (end) params.set('end', end);
        return this.makeRequest<AthenaChatResponse>(`/athena/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: params.toString(),
        });
    }
}

// Export singleton instance
export const apiService = new ApiService();
