import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { LayoutDashboard, PlusSquare, FilePlus, List, BarChart2, Calendar, Receipt, Brain, Tag } from 'lucide-react';
import { DonutChartCard } from './components/Charts';
import { TrendChartCard } from './components/TrendChart';
import type { TrendPoint } from './components/TrendChart';
import { apiService } from './services/api';
import type { TransactionDto, TransactionSummary, Transaction } from './services/api';
import CategorizationReview from './components/CategorizationReview';
import MonthlyBalanceChecker from './components/MonthlyBalanceChecker';
import AddTransaction from './components/AddTransaction';
import BulkUpload from './components/BulkUpload';
import { DashboardBanner } from './components/DashboardBanner';
import { AthenaInsights } from './components/AthenaInsights';
import { DateRangePicker } from './components/DateRangePicker';
import { rangeForPreset } from './utils/dateRange';
import type { RangePreset, DateRange } from './utils/dateRange';
import { CategoryCell, CorrectionToast } from './components/CategoryCell';
import { useCategoryCorrection } from './components/useCategoryCorrection';
import { LargestTransactionsCard, RecurringChargesCard } from './components/SpendingBehaviourCards';
import { TransactionsModal } from './components/TransactionsModal';

// --- Type Definitions for TypeScript ---
// This defines the possible views our application can have.
type View =
  | 'Dashboard'
  | 'AddBulkTransactions'
  | 'AddTransaction'
  | 'ViewTransactions'
  | 'SpendingInsights'
  | 'MonthlyBalanceChecker'
  | 'AddReceipt'
  | 'CategorizationReview';

// --- Dashboard Component ---
const Dashboard = () => {
    const [categoryData, setCategoryData] = useState<{ labels: string[]; values: number[]; details?: Record<string, (TransactionDto | Transaction)[]>; originalKeys?: string[] }>({ labels: [], values: [] });
    const [merchantData, setMerchantData] = useState<{ labels: string[]; values: number[]; details?: Record<string, (TransactionDto | Transaction)[]>; originalKeys?: string[] }>({ labels: [], values: [] });
    const [monthlyData, setMonthlyData] = useState<{ labels: string[]; values: number[]; details?: Record<string, (TransactionDto | Transaction)[]>; originalKeys?: string[] }>({ labels: [], values: [] });
    const [trendData, setTrendData] = useState<TrendPoint[]>([]);
    const [trendDetails, setTrendDetails] = useState<Record<string, (TransactionDto | Transaction)[]>>({});
    const [yearTransactions, setYearTransactions] = useState<TransactionDto[]>([]);
    const [recentTransactions, setRecentTransactions] = useState<TransactionDto[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [modalOpen, setModalOpen] = useState(false);
    const [modalTitle, setModalTitle] = useState('');
    const [modalTransactions, setModalTransactions] = useState<(TransactionDto | Transaction)[]>([]);
    // Bumped after a category correction so the charts behind the modal refresh
    const [reloadKey, setReloadKey] = useState(0);
    const reloadDashboard = useCallback(() => setReloadKey(k => k + 1), []);

    useEffect(() => {
        const loadDashboardData = async () => {
            try {
                // Year to date: January 1st through today (local dates, not UTC)
                const now = new Date();
                const yearStart = new Date(now.getFullYear(), 0, 1);
                const pad = (n: number) => String(n).padStart(2, '0');
                const startDate = `${yearStart.getFullYear()}-01-01`;
                const endDate = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;

                // Load category data
                const categoryResponse = await apiService.getExpensesByCategoryTop10(startDate, endDate);
                if (categoryResponse) {
                    setCategoryData({
                        labels: Object.keys(categoryResponse.totals),
                        values: Object.values(categoryResponse.totals).map(val => Number(val)),
                        details: categoryResponse.details,
                        originalKeys:  Object.keys(categoryResponse.totals)
                    });
                }

                // Load merchant data
                const merchantResponse = await apiService.getExpensesByEntityTop10(startDate, endDate);
                if (merchantResponse) {
                    const originalKeys = Object.keys(merchantResponse.totals);
                    const labels = originalKeys.map(key =>
                        key.length > 15 ? key.substring(0, 10) + "…" : key
                    );                    
                    setMerchantData({
                        labels,
                        values: Object.values(merchantResponse.totals).map(val => Number(val)),
                        details: merchantResponse.details,
                        originalKeys:  Object.keys(merchantResponse.totals)
                    });
                }

                // Load monthly data
                const startMonth = startDate.slice(0, 7); // YYYY-MM format
                const endMonth = endDate.slice(0, 7);
                const monthlyResponse = await apiService.getExpensesByMonth(startMonth, endMonth);
                if (monthlyResponse) {
                    const keys = Object.keys(monthlyResponse.totals);
                    setMonthlyData({
                        labels: keys.map(month => {
                            const [year, monthNum] = month.split('-');
                            const date = new Date(parseInt(year), parseInt(monthNum) - 1);
                            return date.toLocaleDateString('en-US', { month: 'long' });
                        }),
                        values: Object.values(monthlyResponse.totals).map(val => Number(val)),
                        details: monthlyResponse.details,
                        originalKeys: keys
                    });
                }

                // Load 12-month trend (months with no spending are filled with 0)
                const fmtKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
                // Trend covers the 12 completed months; the current month is excluded since it is rarely imported yet.
                const trendStart = new Date(now.getFullYear(), now.getMonth() - 12, 1);
                const trendEnd = new Date(now.getFullYear(), now.getMonth() - 1, 1);
                const trendResponse = await apiService.getExpensesByMonth(fmtKey(trendStart), fmtKey(trendEnd));
                if (trendResponse) {
                    const points: TrendPoint[] = [];
                    for (let i = 0; i < 12; i++) {
                        const d = new Date(trendStart.getFullYear(), trendStart.getMonth() + i, 1);
                        const key = fmtKey(d);
                        const month = d.toLocaleDateString('en-US', { month: 'short' });
                        points.push({
                            key,
                            label: i === 0 || d.getMonth() === 0 ? `${month} '${String(d.getFullYear()).slice(2)}` : month,
                            value: Number(trendResponse.totals[key] ?? 0)
                        });
                    }
                    setTrendData(points);
                    setTrendDetails(trendResponse.details || {});
                }

                // Raw transactions feed the largest-purchases and recurring-charges tiles
                const fmtDay = (d: Date) => `${fmtKey(d)}-${String(d.getDate()).padStart(2, '0')}`;
                const yearTxs = await apiService.getTransactionsByDateRange(fmtDay(new Date(now.getFullYear(), now.getMonth() - 11, 1)), fmtDay(now));
                setYearTransactions(yearTxs);
                setRecentTransactions(yearTxs.filter(t => t.date >= startDate));
            } catch (error) {
                console.error('Error loading dashboard data:', error);
            } finally {
                setIsLoading(false);
            }
        };

        loadDashboardData();
    }, [reloadKey]);

    if (isLoading) {
        return (
            <div className="max-w-7xl mx-auto animate-fade-in">
                <DashboardBanner />
                <div className="flex items-center justify-center h-64">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
                    <span className="ml-3 text-gray-600">Loading dashboard data...</span>
                </div>
            </div>
        );
    }

    const openTransactions = (title: string, txs: (TransactionDto | Transaction)[] | undefined) => {
        setModalTitle(title);
        setModalTransactions(txs || []);
        setModalOpen(true);
    };

    return (
        <div className="max-w-7xl mx-auto animate-fade-in">
            <DashboardBanner transactions={yearTransactions} />
            <AthenaInsights />

            <TrendChartCard
                title="Spending Trend (Past 12 Months)"
                data={trendData}
                onPointClick={(p) => openTransactions(`Transactions in ${p.label}`, trendDetails[p.key])}
            />
            
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                <DonutChartCard
                    title="Top10 Spending by Category (Year to Date)" 
                    data={categoryData} 
                    onSliceClick={(_label, index) => {
                        const key = categoryData.originalKeys?.[index] || '';
                        openTransactions(`Transactions in ${categoryData.labels[index]}`, categoryData.details?.[key]);
                    }}
                />
                <DonutChartCard 
                    title="Top10 Spending by Merchant (Year to Date)" 
                    data={merchantData} 
                    onSliceClick={(_label, index) => {
                        const key = merchantData.originalKeys?.[index] || '';
                        openTransactions(`Transactions in ${merchantData.labels[index]}`, merchantData.details?.[key]);
                    }}
                />
                <DonutChartCard 
                    title="Spending by Month (Year to Date)" 
                    data={monthlyData} 
                    onSliceClick={(_label, index) => {
                        const key = monthlyData.originalKeys?.[index] || '';
                        openTransactions(`Transactions in ${monthlyData.labels[index]}`, monthlyData.details?.[key]);
                    }}
                />
                <LargestTransactionsCard transactions={recentTransactions} onSelect={openTransactions} />
            </div>

            <RecurringChargesCard className="mt-8" transactions={yearTransactions} onSelect={openTransactions} />

            <TransactionsModal
                title={modalTitle}
                open={modalOpen}
                onClose={() => setModalOpen(false)}
                transactions={modalTransactions}
                onCategoryChanged={reloadDashboard}
            />
        </div>
    );
};

const ViewTransactions = () => {
    const [preset, setPreset] = useState<RangePreset>('ytd');
    const [range, setRange] = useState<DateRange>(() => rangeForPreset('ytd'));
    const [transactions, setTransactions] = useState<TransactionDto[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');
    const [sortConfig, setSortConfig] = useState<{ key: keyof TransactionDto; direction: "asc" | "desc" } | null>(null);
    const correction = useCategoryCorrection();

    // Load automatically on arrival and whenever a valid range is chosen
    useEffect(() => {
        if (!range.start || !range.end || range.start > range.end) return;

        let cancelled = false;
        setIsLoading(true);
        setError('');

        apiService.getTransactionsByDateRange(range.start, range.end)
            .then(data => { if (!cancelled) setTransactions(data); })
            .catch(err => {
                if (cancelled) return;
                console.error('Error fetching transactions:', err);
                setError('Failed to fetch transactions. Please try again.');
            })
            .finally(() => { if (!cancelled) setIsLoading(false); });

        return () => { cancelled = true; };
    }, [range.start, range.end]);

    const handleRangeChange = (nextPreset: RangePreset, nextRange: DateRange) => {
        setPreset(nextPreset);
        setRange(nextRange);
    };

    const formatCurrency = (amount: number) => {
        return new Intl.NumberFormat('en-CA', {
            style: 'currency',
            currency: 'CAD'
        }).format(Math.abs(amount));
    };

    const formatDate = (dateString: string) => {
        return new Date(dateString).toLocaleDateString('en-CA', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    };

    const handleSort = (key: keyof TransactionDto) => {
        setSortConfig((prev) => {
            if (prev?.key === key && prev.direction === "asc") {
                return { key, direction: "desc" };
            }
            return { key, direction: "asc" };
        });
    };

    const sortedTransactions = useMemo(() => {
        if (!sortConfig) return transactions;

        return [...transactions].sort((a, b) => {
            const { key, direction } = sortConfig;
            let valA = a[key];
            let valB = b[key];

            // Special handling for date
            if (key === "date") {
                valA = new Date(a.date).getTime();
                valB = new Date(b.date).getTime();
            }

            if (typeof valA === "number" && typeof valB === "number") {
                return direction === "asc" ? valA - valB : valB - valA;
            }
            return direction === "asc"
                ? String(valA).localeCompare(String(valB))
                : String(valB).localeCompare(String(valA));
        });
    }, [transactions, sortConfig]);

    const renderSortIcon = (key: keyof TransactionDto) => {
        if (sortConfig?.key !== key) return null;
        return sortConfig.direction === "asc" ? "▲" : "▼";
    };

    return (
        <div className="animate-fade-in max-w-7xl mx-auto">
            <h2 className="text-3xl font-bold text-gray-900 mb-6">View Transactions</h2>
            
            {/* Date Filter Section */}
            <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm mb-6">
                <DateRangePicker preset={preset} range={range} onChange={handleRangeChange} />
            </div>

            {/* Error Display */}
            {error && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
                    <div className="flex items-center">
                        <svg className="w-5 h-5 text-red-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                        </svg>
                        <span className="text-red-700 font-medium">{error}</span>
                    </div>
                </div>
            )}

            {/* Transactions Table */}
            <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
                <div className="px-6 py-4 border-b border-gray-200">
                    <h3 className="text-lg font-semibold text-gray-900">
                        Transactions {transactions.length > 0 && `(${transactions.length} found)`}
                    </h3>
                </div>
                
                {transactions.length === 0 && !isLoading ? (
                    <div className="p-8 text-center text-gray-500">
                        <svg className="mx-auto h-12 w-12 text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                        </svg>
                        <p className="text-lg font-medium">No transactions found</p>
                        <p className="text-sm">Try a wider date range</p>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200">
                            <thead className="bg-gray-50">
                                <tr>
                                    <th onClick={() => handleSort("date")} className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer">
                                        Date {renderSortIcon("date")}
                                    </th>
                                    <th onClick={() => handleSort("entity")} className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer">
                                        Merchant {renderSortIcon("entity")}
                                    </th>
                                    <th onClick={() => handleSort("transactionType")} className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer">
                                        Type {renderSortIcon("transactionType")}
                                    </th>
                                    <th onClick={() => handleSort("amount")} className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer">
                                        Amount {renderSortIcon("amount")}
                                    </th>
                                    <th onClick={() => handleSort("account")} className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer">
                                        Account {renderSortIcon("account")}
                                    </th>
                                    <th onClick={() => handleSort("category")} className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer">
                                        Category {renderSortIcon("category")}
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="bg-white divide-y divide-gray-200">
                                {isLoading ? (
                                    <tr>
                                        <td colSpan={5} className="px-6 py-4 text-center">
                                            <div className="flex items-center justify-center">
                                                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600"></div>
                                                <span className="ml-2 text-gray-600">Loading transactions...</span>
                                            </div>
                                        </td>
                                    </tr>
                                ) : (
                                    sortedTransactions.map((transaction, index) => (
                                        <tr key={transaction.transactionId ?? index} className="hover:bg-gray-50">
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                                {formatDate(transaction.date)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">                           
                                                {transaction.entity}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                                                    ['INCOME', 'REFUND'].includes(transaction.transactionType)
                                                        ? 'bg-green-100 text-green-800' 
                                                        : 'bg-red-100 text-red-800'
                                                }`}>
                                                    {transaction.transactionType}
                                                </span>
                                            </td>
                                            <td className={`px-6 py-4 whitespace-nowrap text-sm font-medium ${
                                                transaction.amount >= 0 ? 'text-green-600' : 'text-red-600'
                                            }`}>
                                                {transaction.amount >= 0 ? '+' : '-'}{formatCurrency(transaction.amount)}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                                {transaction.account}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                                <CategoryCell
                                                    transactionId={transaction.transactionId}
                                                    entity={transaction.entity}
                                                    category={correction.categoryOf(transaction.transactionId, transaction.category ?? '')}
                                                    categories={correction.categories}
                                                    onSelect={(name) => correction.correct(transaction.transactionId!, name)}
                                                />
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
            <CorrectionToast state={correction} />
        </div>
    );
};

const SpendingInsights = () => {
    const [selectedOption, setSelectedOption] = useState<string>('');
    const [athenaQuery, setAthenaQuery] = useState<string>('');
    const [athenaResponse, setAthenaResponse] = useState<string>('');
    const [isAthenaLoading, setIsAthenaLoading] = useState<boolean>(false);
    const [preset, setPreset] = useState<RangePreset>('ytd');
    const [range, setRange] = useState<DateRange>(() => rangeForPreset('ytd'));
    const [error, setError] = useState<string>('');
    const [chartData, setChartData] = useState<any>(null);
    const [detailsMap, setDetailsMap] = useState<Record<string, (TransactionDto | Transaction)[]> | null>(null);
    const [originalKeys, setOriginalKeys] = useState<string[] | null>(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [modalTitle, setModalTitle] = useState('');
    const [modalTransactions, setModalTransactions] = useState<(TransactionDto | Transaction)[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(false);
    // Bumped after a category correction so the charts behind the modal refresh
    const [reloadKey, setReloadKey] = useState(0);
    const reloadInsights = useCallback(() => setReloadKey(k => k + 1), []);


    // Sample data for different chart types
    // const sampleCategoryData = {
    //     labels: ['Groceries', 'Entertainment', 'Transportation', 'Utilities', 'Dining', 'Shopping'],
    //     values: [1200, 800, 600, 400, 350, 300]
    // };

    // const sampleMerchantData = {
    //     labels: ['Walmart', 'Netflix', 'Shell', 'Hydro One', 'Restaurant A', 'Amazon'],
    //     values: [800, 15, 200, 150, 300, 250]
    // };

    // const sampleMonthlyData = {
    //     labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
    //     values: [2800, 3200, 2650, 3100, 2900, 3400]
    // };

    const handleAthenaSubmit = async () => {
        if (!athenaQuery.trim()) {
            alert('Please enter a question for Athena');
            return;
        }

        setIsAthenaLoading(true);
        setAthenaResponse('');
        try {
            const res = await apiService.athenaChat(athenaQuery);
            setAthenaResponse(res.answer);
        } catch (e) {
            console.error('Athena chat failed', e);
            setAthenaResponse('Athena is unavailable. Please try again later.');
        } finally {
            setIsAthenaLoading(false);
        }
    };

    // Guards against a slow response from a previous option/range overwriting the current one
    const requestIdRef = useRef(0);

    const selectOption = (option: string) => {
        if (option === selectedOption) return;
        requestIdRef.current++;
        setSelectedOption(option);
        setChartData(null);
        setDetailsMap(null);
        setOriginalKeys(null);
        setError('');
        setIsLoading(false);
    };

    const handleRangeChange = (nextPreset: RangePreset, nextRange: DateRange) => {
        setPreset(nextPreset);
        setRange(nextRange);
    };

    // Load automatically whenever the option or a valid range changes
    useEffect(() => {
        if (selectedOption !== 'category' && selectedOption !== 'merchant' && selectedOption !== 'monthly') return;
        if (!range.start || !range.end || range.start > range.end) return;

        const requestId = ++requestIdRef.current;
        setIsLoading(true);
        setError('');

        const load = async () => {
            try {
                let data: TransactionSummary | undefined;
                if (selectedOption === 'category') {
                    data = await apiService.getExpensesByCategory(range.start, range.end);
                } else if (selectedOption === 'merchant') {
                    data = await apiService.getExpensesByEntityTop10(range.start, range.end);
                } else {
                    data = await apiService.getExpensesByMonth(range.start.substring(0, 7), range.end.substring(0, 7));
                }
                if (requestId !== requestIdRef.current) return;
                if (!data) return;

                const keys = Object.keys(data.totals);
                let labels = keys;
                if (selectedOption === 'merchant') {
                    labels = keys.map(key => key.length > 20 ? key.substring(0, 10) + "…" : key);
                } else if (selectedOption === 'monthly') {
                    // Convert "2025-04" to "Apr 2025"
                    labels = keys.map(month => {
                        const [year, monthNum] = month.split('-');
                        return new Date(parseInt(year), parseInt(monthNum) - 1)
                            .toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
                    });
                }
                setChartData({ labels, values: Object.values(data.totals).map(val => Number(val)) });
                setDetailsMap(data.details);
                setOriginalKeys(selectedOption === 'category' ? null : keys);
            } catch (e) {
                if (requestId !== requestIdRef.current) return;
                console.error('Error fetching data:', e);
                setError('Failed to fetch data. Please try again.');
            } finally {
                if (requestId === requestIdRef.current) setIsLoading(false);
            }
        };
        load();
    }, [selectedOption, range.start, range.end, reloadKey]);

    const renderRangeCard = (
        title: string,
        onSliceClick: (label: string, index: number) => void
    ) => {
        const total: number = chartData ? chartData.values.reduce((a: number, b: number) => a + b, 0) : 0;
        return (
            <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-4 mb-5">
                    <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
                    {chartData && (
                        <div className="text-right">
                            <p className="text-xs uppercase tracking-wide text-gray-500">Total</p>
                            <p className="text-2xl font-bold text-gray-900">
                                {total.toLocaleString('en-CA', { style: 'currency', currency: 'CAD' })}
                            </p>
                        </div>
                    )}
                </div>
                <DateRangePicker preset={preset} range={range} onChange={handleRangeChange} />
                {error && (
                    <div className="mt-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">{error}</div>
                )}
                <div className={`mt-6 transition-opacity duration-200 ${isLoading ? 'opacity-50' : 'opacity-100'}`}>
                    {chartData ? (
                        <DonutChartCard title={title} data={chartData} onSliceClick={onSliceClick} />
                    ) : (
                        isLoading && <div className="py-16 text-center text-gray-400">Loading…</div>
                    )}
                    {chartData && chartData.labels.length === 0 && !isLoading && (
                        <div className="py-8 text-center text-gray-500">No spending in this period.</div>
                    )}
                </div>
            </div>
        );
    };

    const renderOptionContent = () => {
        switch (selectedOption) {
            case 'athena':
                return (
                    <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                        <h3 className="text-lg font-semibold text-gray-900 mb-4">Ask Athena's Analysis</h3>
                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">Your Question</label>
                                <textarea
                                    value={athenaQuery}
                                    onChange={(e) => setAthenaQuery(e.target.value)}
                                    placeholder="Ask Athena about your spending patterns, budget advice, or financial insights..."
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500 h-24 resize-none"
                                />
                            </div>
                            <button
                                onClick={handleAthenaSubmit}
                                disabled={!athenaQuery.trim() || isAthenaLoading}
                                className={`px-6 py-2 rounded-lg font-medium transition-colors duration-200 ${
                                    athenaQuery.trim() && !isAthenaLoading
                                        ? 'bg-purple-600 text-white hover:bg-purple-700 shadow-md'
                                        : 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                }`}
                            >
                                {isAthenaLoading ? 'Analyzing...' : 'Submit to Athena'}
                            </button>
                            {athenaResponse && (
                                <div className="mt-6 p-4 bg-gradient-to-r from-purple-50 to-blue-50 border border-purple-200 rounded-lg">
                                    <p className="text-gray-700 leading-relaxed">{athenaResponse}</p>
                                </div>
                            )}
                        </div>
                    </div>
                );

            case 'category':
                return renderRangeCard('Spending by Category', (label) => {
                    const txs = detailsMap?.[label] || [];
                    setModalTitle(`Transactions in ${label}`);
                    setModalTransactions(txs);
                    setModalOpen(true);
                });

            case 'merchant':
                return renderRangeCard('Top Merchants', (_label, index) => {
                    const key = originalKeys?.[index] || '';
                    const txs = (key && detailsMap) ? (detailsMap[key] || []) : [];
                    setModalTitle(`Transactions in ${chartData.labels[index]}`);
                    setModalTransactions(txs);
                    setModalOpen(true);
                });

            case 'monthly':
                return renderRangeCard('Spending by Month', (_label, index) => {
                    const key = originalKeys?.[index] || '';
                    const txs = (key && detailsMap) ? (detailsMap[key] || []) : [];
                    setModalTitle(`Transactions in ${chartData.labels[index]}`);
                    setModalTransactions(txs);
                    setModalOpen(true);
                });

            default:
                return (
                    <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm">
                        <div className="text-center text-gray-500">
                            <svg className="mx-auto h-12 w-12 text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                            </svg>
                            <p className="text-lg font-medium">Select an option to view insights</p>
                            <p className="text-sm">Choose from the options above to analyze your spending patterns</p>
                        </div>
                    </div>
                );
        }
    };

    return (
        <div className="animate-fade-in max-w-7xl mx-auto">
            <h2 className="text-3xl font-bold text-gray-900 mb-6">Spending Insights</h2>
            
            {/* Option Selection */}
            <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm mb-6">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">Choose Analysis Type</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                    <button
                        onClick={() => selectOption('athena')}
                        className={`p-4 rounded-lg border-2 transition-colors duration-200 ${
                            selectedOption === 'athena'
                                ? 'border-purple-500 bg-purple-50 text-purple-700'
                                : 'border-gray-200 hover:border-gray-300 text-gray-700'
                        }`}
                    >
                        <div className="text-center">
                            <Brain className="w-8 h-8 mx-auto mb-2" />
                            <span className="font-medium">Ask Athena's Analysis</span>
                        </div>
                    </button>
                    <button
                        onClick={() => selectOption('category')}
                        className={`p-4 rounded-lg border-2 transition-colors duration-200 ${
                            selectedOption === 'category'
                                ? 'border-blue-500 bg-blue-50 text-blue-700'
                                : 'border-gray-200 hover:border-gray-300 text-gray-700'
                        }`}
                    >
                        <div className="text-center">
                            <BarChart2 className="w-8 h-8 mx-auto mb-2" />
                            <span className="font-medium">Spending by Category</span>
                        </div>
                    </button>
                    <button
                        onClick={() => selectOption('merchant')}
                        className={`p-4 rounded-lg border-2 transition-colors duration-200 ${
                            selectedOption === 'merchant'
                                ? 'border-green-500 bg-green-50 text-green-700'
                                : 'border-gray-200 hover:border-gray-300 text-gray-700'
                        }`}
                    >
                        <div className="text-center">
                            <BarChart2 className="w-8 h-8 mx-auto mb-2" />
                            <span className="font-medium">Top Merchants</span>
                        </div>
                    </button>
                    <button
                        onClick={() => selectOption('monthly')}
                        className={`p-4 rounded-lg border-2 transition-colors duration-200 ${
                            selectedOption === 'monthly'
                                ? 'border-orange-500 bg-orange-50 text-orange-700'
                                : 'border-gray-200 hover:border-gray-300 text-gray-700'
                        }`}
                    >
                        <div className="text-center">
                            <Calendar className="w-8 h-8 mx-auto mb-2" />
                            <span className="font-medium">Spending by Month</span>
                        </div>
                    </button>
                </div>
            </div>

            {/* Content Area */}
            {renderOptionContent()}
            <TransactionsModal
                title={modalTitle}
                open={modalOpen}
                onClose={() => setModalOpen(false)}
                transactions={modalTransactions}
                onCategoryChanged={reloadInsights}
            />
        </div>
    );
};

const AddReceipt = () => (
    <div className="animate-fade-in">
        <h2 className="text-3xl font-bold text-gray-900">Add Receipt</h2>
        <p className="mt-2 text-gray-600">Upload a receipt to automatically create a transaction.</p>
    </div>
);

const CategorizationReviewView = () => {
    return <CategorizationReview />;
};

// --- Sidebar Navigation Component ---
interface SidebarProps {
    activeView: View;
    setActiveView: (view: View) => void;
}

const Sidebar: React.FC<SidebarProps> = ({ activeView, setActiveView }) => {
    const menuItems: { id: number; name: View; icon: React.ElementType }[] = [
        { id: 1, name: 'Dashboard', icon: LayoutDashboard },
        { id: 2, name: 'AddBulkTransactions', icon: PlusSquare },
        { id: 8, name: 'AddTransaction', icon: FilePlus },
        { id: 3, name: 'ViewTransactions', icon: List },
        { id: 4, name: 'SpendingInsights', icon: BarChart2 },
        { id: 5, name: 'MonthlyBalanceChecker', icon: Calendar },
        { id: 6, name: 'AddReceipt', icon: Receipt },
        { id: 7, name: 'CategorizationReview', icon: Tag },
    ];

    // Function to format the view name for display
    const formatViewName = (name: View) => name.replace(/([A-Z])/g, ' $1').trim();

    return (
        <aside className="w-64 flex-shrink-0 bg-gray-100/95 border-r border-gray-200 flex flex-col transition-all duration-300">
            <div className="h-16 flex items-center px-6 border-b border-gray-200">
                <h1 className="text-xl font-bold text-gray-900">Home Finance</h1>
            </div>
            <nav className="flex-1 px-4 py-4 space-y-1">
                {menuItems.map((item) => {
                    const isActive = activeView === item.name;
                    return (
                        <a
                            key={item.id}
                            href="#"
                            onClick={(e) => {
                                e.preventDefault();
                                setActiveView(item.name);
                            }}
                            className={`flex items-center px-4 py-2.5 text-sm font-medium rounded-lg transition-colors duration-200 ${
                                isActive
                                    ? 'bg-gray-200 text-gray-900 font-semibold'
                                    : 'text-gray-600 hover:bg-gray-200/70 hover:text-gray-800'
                            }`}
                        >
                            <item.icon className="w-5 h-5 mr-3" />
                            {formatViewName(item.name)}
                        </a>
                    );
                })}
            </nav>
        </aside>
    );
};


// --- Main App Component ---
const App: React.FC = () => {
    // State to manage the currently displayed view.
    const [activeView, setActiveView] = useState<View>('Dashboard');

    // This function returns the component that corresponds to the active view.
    const renderActiveView = () => {
        switch (activeView) {
            case 'Dashboard':
                return <Dashboard />;
            case 'AddBulkTransactions':
                return <BulkUpload />;
            case 'AddTransaction':
                return <AddTransaction />;
            case 'ViewTransactions':
                return <ViewTransactions />;
            case 'SpendingInsights':
                return <SpendingInsights />;
            case 'MonthlyBalanceChecker':
                return <MonthlyBalanceChecker />;
            case 'AddReceipt':
                return <AddReceipt />;
            case 'CategorizationReview':
                return <CategorizationReviewView />;
            default:
                return <Dashboard />;
        }
    };

    return (
        <div className="flex h-screen bg-white font-sans">
            <style>{`
                @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
                body { font-family: 'Inter', sans-serif; }
                .animate-fade-in { animation: fadeIn 0.5s ease-in-out; }
                @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
            `}</style>
            <Sidebar activeView={activeView} setActiveView={setActiveView} />
            <main className="flex-1 p-6 sm:p-8 md:p-10 overflow-y-auto">
                {renderActiveView()}
            </main>
        </div>
    );
};

export default App;
