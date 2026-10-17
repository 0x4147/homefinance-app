import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ArrowLeft } from 'lucide-react';
import { apiService } from '../services/api';
import type { TransactionDto, Trip, TripSummary } from '../services/api';
import { TripTransactionSearch } from './TripTransactionSearch';
import { CategoryCell, CorrectionToast } from './CategoryCell';
import { useCategoryCorrection } from './useCategoryCorrection';
import type { CategoryCorrectionState } from './useCategoryCorrection';

const currency = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });

const formatDate = (dateString: string) =>
    new Date(`${dateString}T00:00:00`).toLocaleDateString('en-CA', { year: 'numeric', month: 'short', day: 'numeric' });

const errorMessage = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong');

interface TripDetailProps {
    trip: Trip;
    onBack: () => void;
    /** Called after tagging or untagging, so the trip list can refresh. */
    onChanged: () => void;
}

function Breakdown({ title, amounts, format = (k: string) => k }: {
    title: string;
    amounts: Record<string, number>;
    format?: (key: string) => string;
}) {
    const entries = Object.entries(amounts);
    return (
        <div className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
            <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500">{title}</h4>
            {entries.length === 0 ? (
                <p className="text-sm text-gray-400">Nothing yet</p>
            ) : (
                <ul className="space-y-1.5 text-sm">
                    {entries.map(([key, amount]) => (
                        <li key={key} className="flex justify-between gap-3">
                            <span className="truncate text-gray-700">{format(key)}</span>
                            <span className="tabular-nums font-medium text-gray-900">{currency.format(amount)}</span>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}

function TransactionRows({ rows, action, correction }: {
    rows: TransactionDto[];
    action: (t: TransactionDto) => ReactNode;
    correction: CategoryCorrectionState;
}) {
    return (
        <ul className="divide-y divide-gray-100 rounded-2xl border border-gray-200 bg-white shadow-sm">
            {rows.map(t => (
                <li key={t.transactionId} className="flex items-center gap-3 px-4 py-2.5 text-sm">
                    {action(t)}
                    <span className="w-24 flex-shrink-0 text-gray-500">{formatDate(t.date)}</span>
                    <span className="flex min-w-0 flex-1 items-center gap-2">
                        <span className="truncate font-medium text-gray-900" title={t.entity}>{t.entity}</span>
                        <span className="flex-shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-700">{t.account}</span>
                    </span>
                    <span className="hidden w-44 flex-shrink-0 sm:block">
                        <CategoryCell
                            transactionId={t.transactionId}
                            entity={t.entity}
                            category={correction.categoryOf(t.transactionId, t.category ?? 'Unknown')}
                            categories={correction.categories}
                            onSelect={name => { if (t.transactionId !== undefined) correction.correct(t.transactionId, name); }}
                        />
                    </span>
                    <span className={`w-24 flex-shrink-0 text-right tabular-nums font-semibold ${t.amount >= 0 ? 'text-emerald-600' : 'text-gray-900'}`}>
                        {t.amount >= 0 ? '+' : '−'}{currency.format(Math.abs(t.amount))}
                    </span>
                </li>
            ))}
        </ul>
    );
}

export function TripDetail({ trip, onBack, onChanged }: TripDetailProps) {
    const tripId = trip.tripId!;
    const [summary, setSummary] = useState<TripSummary | null>(null);
    const [transactions, setTransactions] = useState<TransactionDto[]>([]);
    const [candidates, setCandidates] = useState<TransactionDto[]>([]);
    const [picked, setPicked] = useState<Set<number>>(new Set());
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(async () => {
        try {
            const [s, tx, c] = await Promise.all([
                apiService.getTripSummary(tripId),
                apiService.getTripTransactions(tripId),
                apiService.getTripCandidates(tripId),
            ]);
            setSummary(s);
            setTransactions(tx);
            setCandidates(c);
            setPicked(new Set());
        } catch (e) {
            setError(errorMessage(e));
        }
    }, [tripId]);

    useEffect(() => { load(); }, [load]);

    // Correcting a category can change what counts in the trip total, so reload after each change
    const correction = useCategoryCorrection(load);

    const run = async (action: () => Promise<unknown>) => {
        setBusy(true);
        setError(null);
        try {
            await action();
            await load();
            onChanged();
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(false);
        }
    };

    const togglePicked = (id: number) =>
        setPicked(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });

    const candidateIds = candidates.map(c => c.transactionId!).filter(id => id !== undefined);
    const allPicked = candidateIds.length > 0 && candidateIds.every(id => picked.has(id));

    return (
        <div className="mx-auto max-w-4xl space-y-6 p-6">
            <div>
                <button onClick={onBack} className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-gray-500 hover:text-gray-900">
                    <ArrowLeft className="h-4 w-4" aria-hidden="true" /> All trips
                </button>
                <h2 className="text-2xl font-bold text-gray-900">{trip.name}</h2>
                <p className="mt-1 text-sm text-gray-500">{formatDate(trip.startDate)} – {formatDate(trip.endDate)}</p>
            </div>

            {error && <div role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

            {summary && (
                <section aria-label="Summary" className="space-y-4">
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                        <div className="rounded-xl bg-purple-50 px-4 py-3">
                            <div className="text-xs font-medium uppercase tracking-wide text-purple-700/70">Total spent</div>
                            <div className="mt-0.5 text-lg font-semibold tabular-nums text-purple-700">{currency.format(summary.total)}</div>
                        </div>
                        {trip.budget != null && summary.budgetRemaining != null && (
                            <>
                                <div className="rounded-xl bg-gray-50 px-4 py-3">
                                    <div className="text-xs font-medium uppercase tracking-wide text-gray-500">Budget</div>
                                    <div className="mt-0.5 text-lg font-semibold tabular-nums text-gray-900">{currency.format(trip.budget)}</div>
                                </div>
                                <div className={`rounded-xl px-4 py-3 ${summary.budgetRemaining < 0 ? 'bg-red-50' : 'bg-emerald-50'}`}>
                                    <div className={`text-xs font-medium uppercase tracking-wide ${summary.budgetRemaining < 0 ? 'text-red-700/70' : 'text-emerald-700/70'}`}>
                                        {summary.budgetRemaining < 0 ? 'Over budget' : 'Remaining'}
                                    </div>
                                    <div className={`mt-0.5 text-lg font-semibold tabular-nums ${summary.budgetRemaining < 0 ? 'text-red-700' : 'text-emerald-700'}`}>
                                        {currency.format(Math.abs(summary.budgetRemaining))}
                                    </div>
                                </div>
                            </>
                        )}
                    </div>
                    {summary.uncountedTransactions > 0 && (
                        <p className="text-sm text-amber-700">
                            {summary.uncountedTransactions} tagged {summary.uncountedTransactions === 1 ? 'transaction is' : 'transactions are'} not
                            counted in the total (unshared or uncategorized).
                        </p>
                    )}
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                        <Breakdown title="By category" amounts={summary.byCategory} />
                        <Breakdown title="By payer" amounts={summary.byPayer} />
                        <Breakdown title="By day" amounts={summary.byDay} format={formatDate} />
                    </div>
                </section>
            )}

            <section aria-label="Trip transactions" className="space-y-2">
                <h3 className="font-semibold text-gray-900">Transactions on this trip ({transactions.length})</h3>
                {transactions.length === 0 ? (
                    <p className="text-sm text-gray-500">None yet. Add some from the suggestions below, or tag them from the transaction lists.</p>
                ) : (
                    <TransactionRows
                        rows={transactions}
                        correction={correction}
                        action={t => (
                            <button
                                disabled={busy}
                                onClick={() => run(() => apiService.setTransactionTrip(t.transactionId!, null))}
                                className="flex-shrink-0 text-xs font-medium text-gray-400 hover:text-red-600 disabled:opacity-50"
                            >
                                Remove
                            </button>
                        )}
                    />
                )}
            </section>

            <section aria-label="Suggested transactions" className="space-y-2">
                <div className="flex items-center justify-between">
                    <h3 className="font-semibold text-gray-900">Suggested ({candidates.length})</h3>
                    {candidates.length > 0 && (
                        <div className="flex items-center gap-3">
                            <button
                                onClick={() => setPicked(allPicked ? new Set() : new Set(candidateIds))}
                                className="text-sm font-medium text-gray-600 hover:text-gray-900"
                            >
                                {allPicked ? 'Clear selection' : 'Select all'}
                            </button>
                            <button
                                disabled={busy || picked.size === 0}
                                onClick={() => run(() => apiService.assignTransactionsToTrip(tripId, [...picked]))}
                                className="rounded-lg bg-purple-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50"
                            >
                                Add {picked.size > 0 ? picked.size : ''} to trip
                            </button>
                        </div>
                    )}
                </div>
                <p className="text-sm text-gray-500">Untagged transactions dated within the trip. Nothing is added until you confirm.</p>
                {candidates.length > 0 && (
                    <TransactionRows
                        rows={candidates}
                        correction={correction}
                        action={t => (
                            <input
                                type="checkbox"
                                aria-label={`Select ${t.entity}`}
                                checked={picked.has(t.transactionId!)}
                                onChange={() => togglePicked(t.transactionId!)}
                                className="h-4 w-4 flex-shrink-0 rounded border-gray-300"
                            />
                        )}
                    />
                )}
            </section>

            <TripTransactionSearch
                trip={trip}
                correction={correction}
                onAdded={() => { load(); onChanged(); }}
            />
            <CorrectionToast state={correction} />
        </div>
    );
}
