import { useEffect, useState } from 'react';
import { CalendarDays, TrendingDown, TrendingUp } from 'lucide-react';
import type { Transaction, TransactionDto } from '../services/api';

type Tx = TransactionDto | Transaction;

const money = (n: number) =>
    new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD', maximumFractionDigits: 0 }).format(n);

const monthKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;

const Stat = ({ label, value, children }: { label: string; value: string; children?: React.ReactNode }) => (
    <div className="rounded-xl bg-white/5 ring-1 ring-white/10 px-4 py-3 backdrop-blur-sm">
        <div className="text-xs uppercase tracking-wider text-slate-400">{label}</div>
        <div className="mt-1 text-2xl font-semibold tabular-nums text-white">{value}</div>
        {children && <div className="mt-0.5 text-xs">{children}</div>}
    </div>
);

export const DashboardBanner = ({ transactions = [] }: { transactions?: Tx[] }) => {
    const [now, setNow] = useState(new Date());

    useEffect(() => {
        const timer = setInterval(() => setNow(new Date()), 30_000);
        return () => clearInterval(timer);
    }, []);

    const hour = now.getHours();
    const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';

    // Spend per calendar month from expense transactions, net of refunds (the feed already covers the past 12 months).
    const spend = new Map<string, number>();
    for (const t of transactions) {
        const type = (t.transactionType || '').toUpperCase();
        if (type !== 'EXPENSE' && type !== 'REFUND') continue;
        const key = t.date.slice(0, 7);
        // Amounts are signed cash flow: money out is negative, so spend is the negated amount.
        spend.set(key, (spend.get(key) ?? 0) - t.amount);
    }
    const thisKey = monthKey(now);
    // The current month is usually not imported yet, so lead with the latest completed month that has data.
    const closedMonths = Array.from(spend.entries()).filter(([k]) => k < thisKey).sort(([a], [b]) => a.localeCompare(b));
    const [latestKey, latestSpend] = closedMonths.length ? closedMonths[closedMonths.length - 1] : [null, 0];
    const latestDate = latestKey ? new Date(Number(latestKey.slice(0, 4)), Number(latestKey.slice(5, 7)) - 1, 1) : null;
    const prevKey = latestDate ? monthKey(new Date(latestDate.getFullYear(), latestDate.getMonth() - 1, 1)) : null;
    const prevSpend = prevKey ? spend.get(prevKey) ?? 0 : 0;
    const monthName = (d: Date) => d.toLocaleDateString('en-US', { month: 'long' });
    const yearStart = `${now.getFullYear()}-01`;
    const ytd = Array.from(spend.entries()).filter(([k]) => k >= yearStart).reduce((s, [, v]) => s + v, 0);
    const avg = closedMonths.length ? closedMonths.reduce((s, [, v]) => s + v, 0) / closedMonths.length : 0;
    const change = prevSpend > 0 ? (latestSpend - prevSpend) / prevSpend : null;
    const hasData = transactions.length > 0;

    return (
        <div className="relative overflow-hidden rounded-2xl bg-slate-900 text-white p-8 shadow-lg mb-8">
            <div className="pointer-events-none absolute -top-24 -right-16 h-72 w-72 rounded-full bg-blue-500/30 blur-3xl" />
            <div className="pointer-events-none absolute -bottom-28 left-1/3 h-64 w-64 rounded-full bg-violet-500/20 blur-3xl" />

            <div className="relative flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
                <div>
                    <p className="flex items-center gap-2 text-sm text-slate-400">
                        <CalendarDays className="h-4 w-4" />
                        {now.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}
                        <span className="text-slate-600">·</span>
                        {now.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}
                    </p>
                    <h1 className="mt-2 text-4xl font-bold tracking-tight">{greeting}</h1>
                    <p className="mt-1 text-slate-300">Here's where your household money stands.</p>
                </div>
            </div>

            {hasData && (
                <div className="relative mt-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <Stat label={latestDate ? `Spent in ${monthName(latestDate)}` : "Last month"} value={money(latestSpend)}>
                        {change === null ? (
                            <span className="text-slate-400">No data for the month before</span>
                        ) : (
                            <span className={`inline-flex items-center gap-1 ${change > 0 ? 'text-orange-300' : 'text-emerald-300'}`}>
                                {change > 0 ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
                                {Math.abs(change * 100).toFixed(0)}% vs {prevKey && monthName(new Date(Number(prevKey.slice(0, 4)), Number(prevKey.slice(5, 7)) - 1, 1))}
                            </span>
                        )}
                    </Stat>
                    <Stat label="Year to date" value={money(ytd)}>
                        <span className="text-slate-400">Since Jan 1</span>
                    </Stat>
                    <Stat label="Monthly average" value={money(avg)}>
                        <span className="text-slate-400">Completed months, past 12</span>
                    </Stat>
                </div>
            )}
        </div>
    );
};
