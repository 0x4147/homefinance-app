import { useEffect, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, Search, X } from 'lucide-react';
import type { Transaction, TransactionDto } from '../services/api';
import { CategoryCell, CorrectionToast } from './CategoryCell';
import { useCategoryCorrection } from './useCategoryCorrection';
import { BalanceToggle, BalanceToast } from './BalanceToggle';
import { useBalanceToggle } from './useBalanceToggle';

type SortKey = 'date' | 'entity' | 'amount' | 'account' | 'category';
type SortConfig = { key: SortKey; direction: 'asc' | 'desc' };

interface Row {
    id?: number;
    key: string | number;
    date: string;
    entity: string;
    amount: number;
    account: string;
    category: string;
    shared: boolean;
}

const currency = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });

const formatDate = (dateString: string) =>
    new Date(dateString).toLocaleDateString('en-CA', { year: 'numeric', month: 'short', day: 'numeric' });

const COLUMNS: { key: SortKey; label: string; align?: 'right' }[] = [
    { key: 'date', label: 'Date' },
    { key: 'entity', label: 'Entity' },
    { key: 'account', label: 'Account' },
    { key: 'category', label: 'Category' },
    { key: 'amount', label: 'Amount', align: 'right' },
];

/** Normalises the two transaction shapes the app passes around into one flat row. */
function toRow(t: TransactionDto | Transaction, idx: number): Row {
    const tx = t as Partial<Omit<Transaction, 'category'>> & { merchant?: string; payee?: string; sourceAccount?: string; category?: string | { name?: string } };
    const dateStr = String(tx.date ?? '');
    return {
        id: tx.transactionId,
        key: tx.transactionId ?? `idx-${idx}`,
        date: dateStr,
        entity: tx.entity || tx.merchant || tx.payee || '',
        amount: Number(tx.amount || 0),
        account: tx.account || tx.sourceAccount || '',
        category: typeof tx.category === 'string' ? tx.category : (tx.category?.name || ''),
        shared: tx.shared ?? true,
    };
}

export function TransactionsModal({
    title,
    open,
    onClose,
    transactions,
    onCategoryChanged,
}: {
    title: string;
    open: boolean;
    onClose: () => void;
    transactions: (TransactionDto | Transaction)[];
    onCategoryChanged?: () => void;
}) {
    const [sortConfig, setSortConfig] = useState<SortConfig>({ key: 'date', direction: 'desc' });
    const [search, setSearch] = useState('');
    const correction = useCategoryCorrection(onCategoryChanged);
    const balance = useBalanceToggle();

    // Fresh search each time the modal is opened with new content
    useEffect(() => { if (open) setSearch(''); }, [open, transactions]);

    // Escape closes; lock background scroll while open
    useEffect(() => {
        if (!open) return;
        const onKeyDown = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
        document.addEventListener('keydown', onKeyDown);
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            document.removeEventListener('keydown', onKeyDown);
            document.body.style.overflow = previousOverflow;
        };
    }, [open, onClose]);

    const rows = useMemo(() => transactions.map(toRow), [transactions]);

    const visibleRows = useMemo(() => {
        const needle = search.trim().toLowerCase();
        const filtered = needle
            ? rows.filter(r => `${r.entity} ${r.category} ${r.account}`.toLowerCase().includes(needle))
            : rows;
        const { key, direction } = sortConfig;
        const sign = direction === 'asc' ? 1 : -1;
        return [...filtered].sort((a, b) => {
            if (key === 'amount') return sign * (a.amount - b.amount);
            if (key === 'date') return sign * (new Date(a.date).getTime() - new Date(b.date).getTime());
            return sign * a[key].localeCompare(b[key]);
        });
    }, [rows, search, sortConfig]);

    const totals = useMemo(() => {
        let out = 0;
        let incoming = 0;
        for (const r of visibleRows) {
            if (r.amount < 0) out += -r.amount;
            else incoming += r.amount;
        }
        return { out, incoming };
    }, [visibleRows]);

    const handleSort = (key: SortKey) =>
        setSortConfig(prev => (prev.key === key
            ? { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' }
            : { key, direction: key === 'date' || key === 'amount' ? 'desc' : 'asc' }));

    if (!open) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-end justify-center bg-gray-900/50 p-0 backdrop-blur-sm sm:items-center sm:p-6"
            onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}
        >
            <div
                role="dialog"
                aria-modal="true"
                aria-label={title}
                className="flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-t-2xl bg-white shadow-2xl ring-1 ring-black/5 sm:rounded-2xl"
            >
                {/* Header */}
                <div className="flex items-start justify-between gap-4 px-6 pb-4 pt-5">
                    <div className="min-w-0">
                        <h3 className="truncate text-lg font-semibold text-gray-900">{title}</h3>
                        <p className="mt-0.5 text-sm text-gray-500">
                            {visibleRows.length}{visibleRows.length !== rows.length && ` of ${rows.length}`}{' '}
                            {rows.length === 1 ? 'transaction' : 'transactions'}
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        aria-label="Close"
                        className="-mr-2 -mt-1 rounded-full p-2 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    >
                        <X className="h-5 w-5" aria-hidden="true" />
                    </button>
                </div>

                {rows.length > 0 && (
                    <div className="space-y-3 px-6 pb-4">
                        <div className="grid grid-cols-2 gap-3">
                            <div className="rounded-xl bg-red-50 px-4 py-3">
                                <div className="text-xs font-medium uppercase tracking-wide text-red-700/70">Money out</div>
                                <div className="mt-0.5 text-lg font-semibold tabular-nums text-red-700">{currency.format(totals.out)}</div>
                            </div>
                            <div className="rounded-xl bg-emerald-50 px-4 py-3">
                                <div className="text-xs font-medium uppercase tracking-wide text-emerald-700/70">Money in</div>
                                <div className="mt-0.5 text-lg font-semibold tabular-nums text-emerald-700">{currency.format(totals.incoming)}</div>
                            </div>
                        </div>
                        <div className="relative">
                            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
                            <input
                                value={search}
                                onChange={e => setSearch(e.target.value)}
                                placeholder="Filter by entity, category or account"
                                aria-label="Filter transactions"
                                className="w-full rounded-lg border border-gray-200 bg-gray-50 py-2 pl-9 pr-3 text-sm text-gray-900 placeholder:text-gray-400 focus:border-blue-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20"
                            />
                        </div>
                    </div>
                )}

                {/* Table */}
                <div className="min-h-0 flex-1 overflow-auto border-t border-gray-100">
                    {rows.length === 0 ? (
                        <div className="py-16 text-center text-gray-500">No transactions to display</div>
                    ) : visibleRows.length === 0 ? (
                        <div className="py-16 text-center text-gray-500">No transactions match “{search}”</div>
                    ) : (
                        <table className="min-w-full text-sm">
                            <thead className="sticky top-0 z-10 bg-white/95 backdrop-blur">
                                <tr className="border-b border-gray-200">
                                    {COLUMNS.map(col => {
                                        const active = sortConfig.key === col.key;
                                        const Icon = !active ? ArrowUpDown : sortConfig.direction === 'asc' ? ArrowUp : ArrowDown;
                                        return (
                                            <th
                                                key={col.key}
                                                scope="col"
                                                aria-sort={active ? (sortConfig.direction === 'asc' ? 'ascending' : 'descending') : 'none'}
                                                className={`px-6 py-3 ${col.align === 'right' ? 'text-right' : 'text-left'}`}
                                            >
                                                <button
                                                    type="button"
                                                    onClick={() => handleSort(col.key)}
                                                    className={`inline-flex items-center gap-1 text-xs font-semibold uppercase tracking-wide transition-colors hover:text-gray-900 focus:outline-none focus-visible:text-gray-900 ${active ? 'text-gray-900' : 'text-gray-500'}`}
                                                >
                                                    {col.label}
                                                    <Icon className={`h-3 w-3 ${active ? '' : 'opacity-40'}`} aria-hidden="true" />
                                                </button>
                                            </th>
                                        );
                                    })}
                                    <th scope="col" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Balance</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {visibleRows.map(r => (
                                    <tr key={r.key} className="transition-colors hover:bg-gray-50">
                                        <td className="whitespace-nowrap px-6 py-3 text-gray-500">{formatDate(r.date)}</td>
                                        <td className="max-w-[16rem] truncate px-6 py-3 font-medium text-gray-900" title={r.entity}>{r.entity}</td>
                                        <td className="whitespace-nowrap px-6 py-3">
                                            {r.account && (
                                                <span className="inline-flex rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-700">{r.account}</span>
                                            )}
                                        </td>
                                        <td className="whitespace-nowrap px-6 py-3 text-gray-900">
                                            <CategoryCell
                                                transactionId={r.id}
                                                entity={r.entity}
                                                category={correction.categoryOf(r.id, r.category)}
                                                categories={correction.categories}
                                                onSelect={name => { if (r.id !== undefined) correction.correct(r.id, name); }}
                                            />
                                        </td>
                                        <td className="whitespace-nowrap px-6 py-3">
                                            <BalanceToggle
                                                transactionId={r.id}
                                                shared={balance.sharedOf(r.id, r.shared)}
                                                category={correction.categoryOf(r.id, r.category)}
                                                categories={correction.categories}
                                                onToggle={next => {
                                                    if (r.id !== undefined) balance.setShared(r.id, r.entity, balance.sharedOf(r.id, r.shared), next);
                                                }}
                                            />
                                        </td>
                                        <td className={`whitespace-nowrap px-6 py-3 text-right font-semibold tabular-nums ${r.amount >= 0 ? 'text-emerald-600' : 'text-gray-900'}`}>
                                            {r.amount >= 0 ? '+' : '−'}{currency.format(Math.abs(r.amount))}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>
            <CorrectionToast state={correction} />
            <BalanceToast state={balance} />
        </div>
    );
}
