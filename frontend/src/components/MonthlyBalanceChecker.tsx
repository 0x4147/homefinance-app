import React, { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, CircleDollarSign, Loader2, Pencil, Scale, Undo2, X } from 'lucide-react';
import { apiService } from '../services/api';
import type { BalanceLine, MonthlyBalanceResponseDto, SettlementStatus } from '../services/api';

type Person = 'ASANKA' | 'DIVYA';

interface MonthState {
    loading: boolean;
    data?: MonthlyBalanceResponseDto;
    error?: string;
}

const MONTH_NAMES = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
];

const KIND_LABELS: Record<string, string> = {
    EXPENSE: 'Expense',
    BILL: 'Bill',
    RENTAL_BILL_INCOME: 'Rental bill income',
    RENTAL_RENT_INCOME: 'Rental income',
    CARD_SPENDING: 'Shared card',
};

const currencyFormatter = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });
const formatCurrency = (amount: number | null | undefined) => currencyFormatter.format(amount || 0);

const todayIso = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const errorMessage = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong');

const STATUS_STYLES: Record<SettlementStatus, { label: string; classes: string; Icon: React.ElementType }> = {
    SETTLED: { label: 'Settled', classes: 'bg-emerald-50 text-emerald-700 ring-emerald-200', Icon: CheckCircle2 },
    UNSETTLED: { label: 'Unsettled', classes: 'bg-amber-50 text-amber-700 ring-amber-200', Icon: CircleDollarSign },
    EVEN: { label: 'Even', classes: 'bg-gray-100 text-gray-600 ring-gray-200', Icon: Scale },
};

/** Closes on Escape, focuses the panel on open, keeps Tab inside it and restores focus on close. */
function useDialog(onClose: () => void) {
    const ref = useRef<HTMLDivElement>(null);
    useEffect(() => {
        const previouslyFocused = document.activeElement as HTMLElement | null;
        const panel = ref.current;
        const focusable = () =>
            Array.from(panel?.querySelectorAll<HTMLElement>('button, input, textarea, select, [href], [tabindex]:not([tabindex="-1"])') ?? [])
                .filter(el => !el.hasAttribute('disabled'));
        focusable()[0]?.focus();

        const onKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                onClose();
            } else if (e.key === 'Tab') {
                const items = focusable();
                if (items.length === 0) return;
                const first = items[0];
                const last = items[items.length - 1];
                if (e.shiftKey && document.activeElement === first) {
                    e.preventDefault();
                    last.focus();
                } else if (!e.shiftKey && document.activeElement === last) {
                    e.preventDefault();
                    first.focus();
                }
            }
        };
        document.addEventListener('keydown', onKeyDown);
        return () => {
            document.removeEventListener('keydown', onKeyDown);
            previouslyFocused?.focus();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    return ref;
}

const StatusBadge = ({ status }: { status: SettlementStatus }) => {
    const { label, classes, Icon } = STATUS_STYLES[status];
    return (
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${classes}`}>
            <Icon className="h-3.5 w-3.5" aria-hidden="true" />
            {label}
        </span>
    );
};

const TransactionsDrawer = ({ month, year, person, total, onClose }: {
    month: number; year: number; person: Person; total: number; onClose: () => void;
}) => {
    const ref = useDialog(onClose);
    const [lines, setLines] = useState<BalanceLine[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        apiService.getMonthlyBalanceTransactions(month, year, person)
            .then(result => { if (!cancelled) setLines(result); })
            .catch(e => { if (!cancelled) setError(errorMessage(e)); });
        return () => { cancelled = true; };
    }, [month, year, person]);

    const title = `${person === 'ASANKA' ? 'Asanka' : 'Divya'} paid · ${MONTH_NAMES[month - 1]} ${year}`;

    return (
        <div className="fixed inset-0 z-50 flex justify-end">
            <div className="absolute inset-0 bg-gray-900/40" onClick={onClose} aria-hidden="true" />
            <div
                ref={ref}
                role="dialog"
                aria-modal="true"
                aria-label={title}
                className="relative flex h-full w-full max-w-lg flex-col bg-white shadow-xl animate-fade-in"
            >
                <div className="flex items-start justify-between border-b border-gray-200 px-6 py-4">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
                        <p className="text-sm text-gray-500">Net contribution {formatCurrency(total)}</p>
                    </div>
                    <button onClick={onClose} aria-label="Close" className="rounded-lg p-1.5 text-gray-500 hover:bg-gray-100">
                        <X className="h-5 w-5" />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto px-6 py-4">
                    {error && <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700">Could not load transactions. {error}</p>}
                    {!error && lines === null && (
                        <div className="flex items-center gap-2 text-gray-500"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
                    )}
                    {lines && lines.length === 0 && <p className="text-gray-500">No transactions make up this figure.</p>}
                    {lines && lines.length > 0 && (
                        <ul className="divide-y divide-gray-100">
                            {lines.map((line, i) => (
                                <li key={i} className="flex items-start justify-between gap-4 py-3">
                                    <div className="min-w-0">
                                        <p className="truncate font-medium text-gray-900">{line.transaction.entity}</p>
                                        <p className="text-xs text-gray-500">
                                            {line.transaction.date} · {KIND_LABELS[line.kind] ?? line.kind}
                                            {line.kind === 'CARD_SPENDING' ? ` · ${line.transaction.account}` : ''}
                                        </p>
                                    </div>
                                    <span className={`whitespace-nowrap font-medium tabular-nums ${line.contribution < 0 ? 'text-emerald-700' : 'text-gray-900'}`}>
                                        {formatCurrency(line.contribution)}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                {lines && lines.length > 0 && (
                    <div className="flex items-center justify-between border-t border-gray-200 bg-gray-50 px-6 py-4">
                        <span className="text-sm font-medium text-gray-600">Total</span>
                        <span className="font-semibold tabular-nums text-gray-900">
                            {formatCurrency(lines.reduce((sum, l) => sum + l.contribution, 0))}
                        </span>
                    </div>
                )}
            </div>
        </div>
    );
};

const PaymentModal = ({ data, onClose, onSaved }: {
    data: MonthlyBalanceResponseDto; onClose: () => void; onSaved: () => void;
}) => {
    const ref = useDialog(onClose);
    const editing = !!data.settlement;
    const [amount, setAmount] = useState(String(data.settlement?.amount ?? data.balanceAmount));
    const [paidDate, setPaidDate] = useState(data.settlement?.paidDate ?? todayIso());
    const [notes, setNotes] = useState(data.settlement?.notes ?? '');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        const value = Number(amount);
        if (!(value > 0)) {
            setError('Enter an amount greater than zero.');
            return;
        }
        if (!paidDate) {
            setError('Choose the date it was paid.');
            return;
        }
        setSaving(true);
        setError(null);
        try {
            await apiService.saveSettlement(data.year, data.month, { amount: value, paidDate, notes: notes.trim() || undefined });
            onSaved();
        } catch (err) {
            setError(errorMessage(err));
            setSaving(false);
        }
    };

    const payer = data.settlement?.from ?? data.whoOwes;
    const receiver = data.settlement?.to ?? (data.whoOwes === 'Asanka' ? 'Divya' : 'Asanka');
    const title = `${editing ? 'Edit payment' : 'Mark as paid'} · ${MONTH_NAMES[data.month - 1]} ${data.year}`;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <div className="absolute inset-0 bg-gray-900/40" onClick={onClose} aria-hidden="true" />
            <div ref={ref} role="dialog" aria-modal="true" aria-label={title}
                 className="relative w-full max-w-md rounded-2xl bg-white p-6 shadow-xl animate-fade-in">
                <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
                <p className="mb-4 text-sm text-gray-500">{payer} pays {receiver}</p>
                <form onSubmit={submit} className="space-y-4">
                    <div>
                        <label htmlFor="settle-amount" className="mb-1 block text-sm font-medium text-gray-700">Amount (CAD)</label>
                        <input id="settle-amount" type="number" step="0.01" min="0.01" value={amount}
                               onChange={e => setAmount(e.target.value)}
                               className="w-full rounded-lg border border-gray-300 px-3 py-2 focus:border-blue-500 focus:ring-2 focus:ring-blue-500" />
                    </div>
                    <div>
                        <label htmlFor="settle-date" className="mb-1 block text-sm font-medium text-gray-700">Date paid</label>
                        <input id="settle-date" type="date" value={paidDate} onChange={e => setPaidDate(e.target.value)}
                               className="w-full rounded-lg border border-gray-300 px-3 py-2 focus:border-blue-500 focus:ring-2 focus:ring-blue-500" />
                    </div>
                    <div>
                        <label htmlFor="settle-notes" className="mb-1 block text-sm font-medium text-gray-700">Note (optional)</label>
                        <input id="settle-notes" type="text" maxLength={500} value={notes} onChange={e => setNotes(e.target.value)}
                               placeholder="e.g. e-transfer"
                               className="w-full rounded-lg border border-gray-300 px-3 py-2 focus:border-blue-500 focus:ring-2 focus:ring-blue-500" />
                    </div>
                    {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
                    <div className="flex justify-end gap-2 pt-2">
                        <button type="button" onClick={onClose}
                                className="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100">Cancel</button>
                        <button type="submit" disabled={saving}
                                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60">
                            {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                            {editing ? 'Save changes' : 'Record payment'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

const PaidFigure = ({ label, amount, onClick }: { label: string; amount: number; onClick: () => void }) => (
    <button onClick={onClick}
            className="rounded-xl bg-gray-50 px-3 py-2 text-left transition hover:bg-blue-50 hover:ring-1 hover:ring-blue-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
        <span className="block text-xs text-gray-500">{label}</span>
        <span className="block font-semibold tabular-nums text-gray-900">{formatCurrency(amount)}</span>
        <span className="block text-[11px] text-blue-600">View transactions</span>
    </button>
);

const MonthCard = ({ month, state, onRetry, onShowTransactions, onPay, onUndo }: {
    month: number;
    state: MonthState;
    onRetry: () => void;
    onShowTransactions: (person: Person) => void;
    onPay: () => void;
    onUndo: () => void;
}) => {
    const name = MONTH_NAMES[month - 1];
    const cardClasses = 'rounded-2xl border border-gray-200 bg-white p-5 shadow-sm';

    if (state.loading && !state.data) {
        return (
            <div className={`${cardClasses} animate-pulse`} aria-busy="true">
                <div className="mb-4 h-5 w-24 rounded bg-gray-200" />
                <div className="mb-4 h-7 w-40 rounded bg-gray-200" />
                <div className="grid grid-cols-2 gap-3"><div className="h-16 rounded-xl bg-gray-100" /><div className="h-16 rounded-xl bg-gray-100" /></div>
            </div>
        );
    }
    if (!state.data) {
        return (
            <div className={cardClasses}>
                <h3 className="mb-2 font-semibold text-gray-900">{name}</h3>
                <p className="mb-3 text-sm text-red-700">Could not load this month. {state.error}</p>
                <button onClick={onRetry} className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm hover:bg-gray-50">Retry</button>
            </div>
        );
    }

    const d = state.data;
    return (
        <div className={`${cardClasses} ${state.loading ? 'opacity-70' : ''}`}>
            <div className="mb-3 flex items-center justify-between">
                <h3 className="text-lg font-semibold text-gray-900">{name}</h3>
                <StatusBadge status={d.status} />
            </div>

            <p className="mb-4 text-gray-700">
                {d.whoOwes ? (
                    <><span className="font-medium">{d.whoOwes}</span> owes{' '}
                        <span className="text-xl font-semibold tabular-nums text-gray-900">{formatCurrency(d.balanceAmount)}</span></>
                ) : (
                    <span className="text-gray-500">All even this month</span>
                )}
            </p>

            <div className="mb-4 grid grid-cols-2 gap-3">
                <PaidFigure label="Asanka paid" amount={d.asankaPaid} onClick={() => onShowTransactions('ASANKA')} />
                <PaidFigure label="Divya paid" amount={d.divyaPaid} onClick={() => onShowTransactions('DIVYA')} />
            </div>

            {d.settlement && (
                <div className="mb-3 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-900">
                    <p className="font-medium">
                        {d.settlement.from} paid {d.settlement.to} {formatCurrency(d.settlement.amount)}
                    </p>
                    <p className="text-emerald-800/80">
                        on {d.settlement.paidDate}{d.settlement.notes ? ` · ${d.settlement.notes}` : ''}
                    </p>
                </div>
            )}
            {d.settlementMismatch && d.settlement && (
                <p className="mb-3 flex items-start gap-1.5 text-sm text-amber-700">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    Recorded {formatCurrency(d.settlement.amount)}, but the balance is now {formatCurrency(d.balanceAmount)}.
                </p>
            )}

            {d.status === 'UNSETTLED' && (
                <button onClick={onPay}
                        className="w-full rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700">
                    Mark as paid
                </button>
            )}
            {d.status === 'SETTLED' && (
                <div className="flex gap-2">
                    <button onClick={onPay}
                            className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
                        <Pencil className="h-4 w-4" aria-hidden="true" /> Edit
                    </button>
                    <button onClick={onUndo}
                            className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-50">
                        <Undo2 className="h-4 w-4" aria-hidden="true" /> Undo
                    </button>
                </div>
            )}
        </div>
    );
};

const MonthlyBalanceChecker: React.FC = () => {
    const currentYear = new Date().getFullYear();
    const years = Array.from({ length: 6 }, (_, i) => currentYear - i);

    const [year, setYear] = useState<number>(currentYear);
    const [months, setMonths] = useState<Record<number, MonthState>>({});
    const [drawer, setDrawer] = useState<{ month: number; person: Person } | null>(null);
    const [payMonth, setPayMonth] = useState<number | null>(null);
    const yearRequest = useRef(0);

    const loadMonth = useCallback(async (y: number, m: number, requestId: number) => {
        setMonths(prev => ({ ...prev, [m]: { ...prev[m], loading: true, error: undefined } }));
        try {
            const data = await apiService.getMonthlyBalance(String(m), String(y));
            if (requestId === yearRequest.current) setMonths(prev => ({ ...prev, [m]: { loading: false, data } }));
        } catch (e) {
            if (requestId === yearRequest.current) setMonths(prev => ({ ...prev, [m]: { loading: false, error: errorMessage(e) } }));
        }
    }, []);

    useEffect(() => {
        const requestId = ++yearRequest.current;
        setMonths({});
        setDrawer(null);
        setPayMonth(null);
        for (let m = 1; m <= 12; m++) {
            setMonths(prev => ({ ...prev, [m]: { loading: true } }));
            void loadMonth(year, m, requestId);
        }
    }, [year, loadMonth]);

    const undo = async (month: number) => {
        if (!window.confirm(`Undo the recorded payment for ${MONTH_NAMES[month - 1]} ${year}?`)) return;
        try {
            await apiService.deleteSettlement(year, month);
            await loadMonth(year, month, yearRequest.current);
        } catch (e) {
            alert(`Could not undo the payment. ${errorMessage(e)}`);
        }
    };

    const drawerData = drawer ? months[drawer.month]?.data : undefined;
    const payData = payMonth ? months[payMonth]?.data : undefined;

    return (
        <div className="animate-fade-in mx-auto max-w-6xl">
            <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
                <div>
                    <h2 className="text-3xl font-bold text-gray-900">Monthly Balance</h2>
                    <p className="text-gray-500">Who owes whom each month, and whether it has been paid.</p>
                </div>
                <div>
                    <label htmlFor="balance-year" className="mb-1 block text-sm font-medium text-gray-700">Year</label>
                    <select id="balance-year" value={year} onChange={e => setYear(Number(e.target.value))}
                            className="rounded-lg border border-gray-300 bg-white px-3 py-2 focus:border-blue-500 focus:ring-2 focus:ring-blue-500">
                        {years.map(y => <option key={y} value={y}>{y}</option>)}
                    </select>
                </div>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {Array.from({ length: 12 }, (_, i) => i + 1).map(m => (
                    <MonthCard
                        key={m}
                        month={m}
                        state={months[m] ?? { loading: true }}
                        onRetry={() => void loadMonth(year, m, yearRequest.current)}
                        onShowTransactions={person => setDrawer({ month: m, person })}
                        onPay={() => setPayMonth(m)}
                        onUndo={() => void undo(m)}
                    />
                ))}
            </div>

            {drawer && drawerData && (
                <TransactionsDrawer
                    month={drawer.month}
                    year={year}
                    person={drawer.person}
                    total={drawer.person === 'ASANKA' ? drawerData.asankaPaid : drawerData.divyaPaid}
                    onClose={() => setDrawer(null)}
                />
            )}
            {payData && (
                <PaymentModal
                    data={payData}
                    onClose={() => setPayMonth(null)}
                    onSaved={() => {
                        const m = payMonth!;
                        setPayMonth(null);
                        void loadMonth(year, m, yearRequest.current);
                    }}
                />
            )}
        </div>
    );
};

export default MonthlyBalanceChecker;
