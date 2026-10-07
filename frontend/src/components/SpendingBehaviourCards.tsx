import type { Transaction, TransactionDto } from '../services/api';

type Tx = TransactionDto | Transaction;
type OnSelect = (title: string, txs: Tx[]) => void;

const INK = '#0b0b0b';
const INK_2 = '#52514e';
const MUTED = '#898781';
const ACCENT = '#2a78d6';
const WARN = '#eb6834';

const money = (n: number, digits = 0) =>
    new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD', maximumFractionDigits: digits, minimumFractionDigits: digits }).format(n);

const shortDate = (d: string) =>
    new Date(d).toLocaleDateString('en-CA', { year: 'numeric', month: 'short', day: 'numeric' });

const DAY_MS = 86_400_000;

const isExpense = (t: Tx) => (t.transactionType || '').toUpperCase() === 'EXPENSE';

const Card = ({ title, subtitle, className = '', children }: {
    title: string;
    subtitle?: string;
    className?: string;
    children: React.ReactNode;
}) => (
    <div className={`bg-white border border-gray-200 rounded-2xl p-6 shadow-sm ${className}`}>
        <div className="mb-5">
            <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
            {subtitle && <p className="text-sm mt-0.5" style={{ color: MUTED }}>{subtitle}</p>}
        </div>
        {children}
    </div>
);

// --- Largest single transactions ---
export const LargestTransactionsCard = ({ transactions, onSelect }: { transactions: Tx[]; onSelect: OnSelect }) => {
    const top = transactions
        .filter(isExpense)
        .sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
        .slice(0, 10);
    const max = top.length ? Math.abs(top[0].amount) : 0;

    return (
        <Card title="Largest Purchases (Year to Date)" subtitle="Click a row to see the transaction">
            {top.length === 0 ? (
                <p className="text-sm text-gray-500 py-12 text-center">No spending in this range</p>
            ) : (
                <ul className="divide-y divide-gray-100">
                    {top.map((t, i) => (
                        <li
                            key={`${t.date}-${t.entity}-${t.amount}-${i}`}
                            className="py-2 px-2 rounded-lg cursor-pointer hover:bg-gray-50 transition-colors"
                            onClick={() => onSelect(`${t.entity} on ${shortDate(t.date)}`, [t])}
                        >
                            <div className="flex items-center gap-3">
                                <span className="flex-1 truncate text-sm" style={{ color: INK }}>{t.entity}</span>
                                <span className="text-sm tabular-nums font-medium" style={{ color: INK }}>
                                    {money(Math.abs(t.amount), 2)}
                                </span>
                            </div>
                            <div className="flex items-center gap-3 mt-1">
                                <div className="flex-1 h-1.5 rounded-full bg-gray-100 overflow-hidden">
                                    <div className="h-full rounded-full" style={{ width: `${(Math.abs(t.amount) / max) * 100}%`, background: ACCENT }} />
                                </div>
                                <span className="text-xs shrink-0" style={{ color: MUTED }}>
                                    {t.category ? `${t.category} · ` : ''}{shortDate(t.date)}
                                </span>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </Card>
    );
};

// --- Recurring charges ---
type Recurring = {
    merchant: string;
    cadence: 'Monthly' | 'Yearly';
    amount: number;       // typical charge
    monthlyCost: number;
    lastDate: string;
    count: number;
    priceChange: number;  // fractional change, latest vs. earliest charge
    charges: Tx[];
};

const median = (xs: number[]) => {
    const s = [...xs].sort((a, b) => a - b);
    const m = Math.floor(s.length / 2);
    return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

// A merchant counts as recurring when its charges land at a steady monthly (or yearly) interval
// with a similar amount each time, and the most recent one is not stale.
const detectRecurring = (transactions: Tx[], now = new Date()): Recurring[] => {
    const groups = new Map<string, Tx[]>();
    for (const t of transactions.filter(isExpense)) {
        const key = (t.entity || '').trim().toLowerCase();
        if (!key) continue;
        groups.set(key, [...(groups.get(key) ?? []), t]);
    }

    const found: Recurring[] = [];
    groups.forEach(list => {
        const charges = [...list].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
        if (charges.length < 2) return;

        const times = charges.map(c => new Date(c.date).getTime());
        const gaps = times.slice(1).map((t, i) => (t - times[i]) / DAY_MS);
        const gap = median(gaps);

        const cadence = gap >= 26 && gap <= 35 ? 'Monthly' : gap >= 355 && gap <= 375 ? 'Yearly' : null;
        if (!cadence) return;
        if (cadence === 'Monthly' && charges.length < 3) return;

        const amounts = charges.map(c => Math.abs(c.amount));
        const typical = median(amounts);
        if (typical <= 0) return;
        const similar = amounts.filter(a => Math.abs(a - typical) / typical <= 0.15).length;
        if (similar / amounts.length < 0.6) return;

        // Skip charges that have stopped (e.g. cancelled subscriptions).
        const sinceLast = (now.getTime() - times[times.length - 1]) / DAY_MS;
        if (sinceLast > gap * 1.6) return;

        found.push({
            merchant: charges[charges.length - 1].entity,
            cadence,
            amount: amounts[amounts.length - 1],
            monthlyCost: cadence === 'Monthly' ? typical : typical / 12,
            lastDate: charges[charges.length - 1].date,
            count: charges.length,
            priceChange: (amounts[amounts.length - 1] - amounts[0]) / amounts[0],
            charges: [...charges].reverse(),
        });
    });

    return found.sort((a, b) => b.monthlyCost - a.monthlyCost);
};

export const RecurringChargesCard = ({ transactions, onSelect, className = '' }: {
    transactions: Tx[];
    onSelect: OnSelect;
    className?: string;
}) => {
    const items = detectRecurring(transactions);
    const monthly = items.reduce((s, r) => s + r.monthlyCost, 0);

    return (
        <Card
            title="Recurring Charges"
            subtitle="Merchants that bill on a steady monthly or yearly schedule (past 12 months)"
            className={className}
        >
            {items.length === 0 ? (
                <p className="text-sm text-gray-500 py-12 text-center">No recurring charges detected</p>
            ) : (
                <>
                    <div className="flex flex-wrap gap-x-10 gap-y-2 mb-4">
                        <div>
                            <div className="text-xs" style={{ color: MUTED }}>Per month</div>
                            <div className="text-2xl font-semibold tabular-nums" style={{ color: INK }}>{money(monthly)}</div>
                        </div>
                        <div>
                            <div className="text-xs" style={{ color: MUTED }}>Per year</div>
                            <div className="text-2xl font-semibold tabular-nums" style={{ color: INK }}>{money(monthly * 12)}</div>
                        </div>
                        <div>
                            <div className="text-xs" style={{ color: MUTED }}>Detected</div>
                            <div className="text-2xl font-semibold tabular-nums" style={{ color: INK }}>{items.length}</div>
                        </div>
                    </div>
                    <ul className="divide-y divide-gray-100">
                        {items.map(r => (
                            <li
                                key={r.merchant}
                                className="flex items-center gap-3 py-2 px-2 rounded-lg cursor-pointer hover:bg-gray-50 transition-colors"
                                onClick={() => onSelect(`${r.merchant} charges`, r.charges)}
                            >
                                <span className="flex-1 min-w-0">
                                    <span className="block truncate text-sm" style={{ color: INK }}>{r.merchant}</span>
                                    <span className="block text-xs" style={{ color: MUTED }}>
                                        {r.cadence} · {r.count} charges · last {shortDate(r.lastDate)}
                                    </span>
                                </span>
                                {Math.abs(r.priceChange) >= 0.05 && (
                                    <span className="text-xs shrink-0" style={{ color: r.priceChange > 0 ? WARN : INK_2 }}>
                                        {r.priceChange > 0 ? '▲' : '▼'} {Math.abs(r.priceChange * 100).toFixed(0)}%
                                    </span>
                                )}
                                <span className="text-sm tabular-nums" style={{ color: INK }}>{money(r.amount, 2)}</span>
                                <span className="w-24 text-right text-xs tabular-nums" style={{ color: MUTED }}>
                                    {money(r.monthlyCost)}/mo
                                </span>
                            </li>
                        ))}
                    </ul>
                </>
            )}
        </Card>
    );
};
