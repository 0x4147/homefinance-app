// API service for backend communication
// Relative so the same build works behind any host: Nginx (compose/Kubernetes) and the Vite dev server both proxy /api to the backend
const API_BASE_URL = '/api/v1';

// Console logging utility
const log = {
    info: (message: string, data?: unknown) => {
        console.log(`[API] ${message}`, data || '');
    },
    error: (message: string, error?: unknown) => {
        console.error(`[API ERROR] ${message}`, error || '');
    },
    debug: (message: string, data?: unknown) => {
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
    shared?: boolean;
    owner?: string | null;
    tripId?: number | null;
    tripName?: string | null;
    uncategorizedTransaction?: { id: number } | null;
}

export interface TransactionDto {
    transactionId?: number;
    amount: number;
    date: string;
    entity: string;
    details?: string;
    account: string;
    transactionType: string;
    category?: string;
    person: string;
    /** False when this one transaction is kept out of the shared balance. */
    shared?: boolean;
    /** Whether it currently counts toward the balance (shared, and in a category that counts). */
    countsInBalance?: boolean;
    /** Code of the person who owes all of it (ASANKA / DIVYA); null when it is split between both. */
    owner?: string | null;
    /** The trip it is tagged with, if any. */
    tripId?: number | null;
    tripName?: string | null;
}

export interface Trip {
    tripId?: number;
    name: string;
    startDate: string;
    endDate: string;
    /** Optional spending target. */
    budget?: number | null;
    /** Read-only: what the trip has cost so far; set by the trip list and summary, ignored when saving. */
    total?: number | null;
}

export interface TripSummary {
    trip: Trip;
    /** Spending is positive; refunds reduce it. Only transactions that count in the balance are included. */
    total: number;
    byCategory: Record<string, number>;
    byDay: Record<string, number>;
    byPayer: Record<string, number>;
    /** Budget minus total; null when the trip has no budget. */
    budgetRemaining: number | null;
    /** Tagged transactions left out of the total (unshared or uncategorized). */
    uncountedTransactions: number;
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
    aiSuggestion?: string | null;
    aiConfidence?: number | null;
    firstDate?: string | null;
    lastDate?: string | null;
    transactions: ReviewTransaction[];
    direction: ReviewDirection;
    /** Accounts the group's transactions came from, sorted. */
    accounts: string[];
}

export type ReviewDirection = 'DEBIT' | 'CREDIT' | 'MIXED';

export interface ReviewTransaction {
    id: number;
    date: string;
    merchant: string;
    amount: number;
    direction: ReviewDirection;
    /** Code of the account the transaction came from (CIBC, AMEX, ASANKA, DIVYA). */
    account?: string | null;
}

export interface Category {
    categoryId: number;
    name: string;
    kind: string;
    inBalance: boolean;
    description?: string;
}

/** Result of correcting a category; send it back unchanged to undo. */
export interface CategoryCorrection {
    updated: number;
    merchant: string;
    category: string;
    ruleKey: string;
    previousRuleCategoryId: number | null;
    previousRuleSource: string | null;
    changes: { transactionId: number; previousCategoryId: number | null }[];
}

export interface Person {
    personId: number;
    code: string;
    name: string;
}

export interface AthenaInsightsResponse {
    analysis: string;
    status?: 'online' | 'offline' | 'paused';
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
    async getTransactionsByDateRange(startDate: string, endDate: string, scope: 'ALL' | 'SHARED' = 'ALL'): Promise<TransactionDto[]> {
        log.info(`Fetching ${scope} transactions from ${startDate} to ${endDate}`);
        const params = new URLSearchParams({
            start: startDate,
            end: endDate,
            scope,
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

    /** Sets a transaction's category and applies it to all transactions of the same merchant, now and later. */
    async correctCategory(transactionId: number, category: string): Promise<CategoryCorrection> {
        log.info(`Correcting transaction ${transactionId} to category ${category}`);
        return this.makeRequest<CategoryCorrection>('/categorization/correct', {
            method: 'POST',
            body: JSON.stringify({ transactionId, category }),
        });
    }

    async setTransactionShared(transactionId: number, shared: boolean): Promise<TransactionDto> {
        log.info(`Setting transaction ${transactionId} shared=${shared}`);
        return this.makeRequest<TransactionDto>(`/transaction/${transactionId}/shared`, {
            method: 'PUT',
            body: JSON.stringify({ shared }),
        });
    }

    async setTransactionOwner(transactionId: number, owner: string | null): Promise<TransactionDto> {
        log.info(`Setting transaction ${transactionId} owner=${owner}`);
        return this.makeRequest<TransactionDto>(`/transaction/${transactionId}/owner`, {
            method: 'PUT',
            body: JSON.stringify({ owner }),
        });
    }

    // Trips
    async getTrips(): Promise<Trip[]> {
        return this.makeRequest<Trip[]>('/trip');
    }

    /** Creates the trip, or updates it when it already has an id. */
    async saveTrip(trip: Trip): Promise<Trip> {
        log.info('Saving trip:', trip);
        return this.makeRequest<Trip>(trip.tripId === undefined ? '/trip' : `/trip/${trip.tripId}`, {
            method: trip.tripId === undefined ? 'POST' : 'PUT',
            body: JSON.stringify(trip),
        });
    }

    /** Its transactions are untagged, not deleted. */
    async deleteTrip(tripId: number): Promise<void> {
        log.info(`Deleting trip ${tripId}`);
        return this.makeRequest<void>(`/trip/${tripId}`, { method: 'DELETE' });
    }

    async getTripTransactions(tripId: number): Promise<TransactionDto[]> {
        return this.makeRequest<TransactionDto[]>(`/trip/${tripId}/transactions`);
    }

    /** Untagged transactions dated within the trip; suggestions only. */
    async getTripCandidates(tripId: number): Promise<TransactionDto[]> {
        return this.makeRequest<TransactionDto[]>(`/trip/${tripId}/candidates`);
    }

    /** Transactions not on this trip (including ones on other trips), matching the text and date range; at most 200. */
    async searchTripTransactions(tripId: number, query: string, start?: string, end?: string): Promise<TransactionDto[]> {
        const params = new URLSearchParams();
        if (query.trim()) params.set('query', query.trim());
        if (start) params.set('start', start);
        if (end) params.set('end', end);
        return this.makeRequest<TransactionDto[]>(`/trip/${tripId}/search?${params}`);
    }

    async getTripSummary(tripId: number): Promise<TripSummary> {
        return this.makeRequest<TripSummary>(`/trip/${tripId}/summary`);
    }

    /** Tags the transactions with the trip; any already in another trip move. */
    async assignTransactionsToTrip(tripId: number, transactionIds: number[]): Promise<TransactionDto[]> {
        log.info(`Assigning ${transactionIds.length} transactions to trip ${tripId}`);
        return this.makeRequest<TransactionDto[]>(`/trip/${tripId}/transactions`, {
            method: 'PUT',
            body: JSON.stringify({ transactionIds }),
        });
    }

    /** Tags one transaction with a trip, or clears its tag when tripId is null. */
    async setTransactionTrip(transactionId: number, tripId: number | null): Promise<TransactionDto> {
        log.info(`Setting transaction ${transactionId} trip=${tripId}`);
        return this.makeRequest<TransactionDto>(`/transaction/${transactionId}/trip`, {
            method: 'PUT',
            body: JSON.stringify({ tripId }),
        });
    }

    async undoCorrection(correction: CategoryCorrection): Promise<void> {
        log.info(`Undoing correction for ${correction.ruleKey}`);
        await this.makeRequest<unknown>('/categorization/correct/undo', {
            method: 'POST',
            body: JSON.stringify(correction),
        });
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

    /**
     * Categorizes every pending transaction of a merchant. When `options` is given, `shared` (counts toward the
     * balance) and `owner` (ASANKA / DIVYA, or '' to split between both) are applied to each of them too.
     */
    async reviewGroup(
        merchantKey: string,
        assignedCategory: string,
        options?: { shared: boolean; owner: string },
    ): Promise<{ resolved: number }> {
        log.info(`Categorizing merchant group ${merchantKey} as ${assignedCategory}`);
        const params = new URLSearchParams({ merchantKey, assignedCategory });
        if (options) {
            params.set('shared', String(options.shared));
            params.set('owner', options.owner);
        }
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

    async suggestCategoriesWithAi(): Promise<{ suggested: number; skipped: number }> {
        log.info('Requesting AI category suggestions for pending reviews');
        return this.makeRequest<{ suggested: number; skipped: number }>('/categorization/suggest-ai', {
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
