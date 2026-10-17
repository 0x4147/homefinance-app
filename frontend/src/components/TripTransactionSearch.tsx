import { useState } from 'react';
import type { FormEvent } from 'react';
import { Search } from 'lucide-react';
import { apiService } from '../services/api';
import type { TransactionDto, Trip } from '../services/api';
import { CategoryCell } from './CategoryCell';
import type { CategoryCorrectionState } from './useCategoryCorrection';

const currency = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });

const formatDate = (dateString: string) =>
    new Date(`${dateString}T00:00:00`).toLocaleDateString('en-CA', { year: 'numeric', month: 'short', day: 'numeric' });

const errorMessage = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong');

/** The trip's start/end shifted by whole months, as yyyy-mm-dd, to catch early bookings and late charges. */
function shiftMonths(dateString: string, months: number) {
    const d = new Date(`${dateString}T00:00:00`);
    d.setMonth(d.getMonth() + months);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const inputClass = 'rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-900 focus:border-blue-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20';

interface TripTransactionSearchProps {
    trip: Trip;
    /** Shared category-editing state (and toast) owned by the trip page. */
    correction: CategoryCorrectionState;
    /** Called after transactions are added, so the trip's summary and lists can reload. */
    onAdded: () => void;
}

/**
 * Finds transactions to add to a trip by hand, whatever their date (e.g. a hotel booked months earlier).
 * Ones already on another trip are shown with that trip's name; adding them moves them.
 */
export function TripTransactionSearch({ trip, correction, onAdded }: TripTransactionSearchProps) {
    const tripId = trip.tripId!;
    const [query, setQuery] = useState('');
    const [start, setStart] = useState(shiftMonths(trip.startDate, -6));
    const [end, setEnd] = useState(shiftMonths(trip.endDate, 1));
    const [results, setResults] = useState<TransactionDto[] | null>(null);
    const [picked, setPicked] = useState<Set<number>>(new Set());
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const search = async (e?: FormEvent) => {
        e?.preventDefault();
        setBusy(true);
        setError(null);
        try {
            setResults(await apiService.searchTripTransactions(tripId, query, start, end));
            setPicked(new Set());
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setBusy(false);
        }
    };

    const add = async () => {
        setBusy(true);
        setError(null);
        try {
            await apiService.assignTransactionsToTrip(tripId, [...picked]);
            onAdded();
            setResults(await apiService.searchTripTransactions(tripId, query, start, end));
            setPicked(new Set());
        } catch (err) {
            setError(errorMessage(err));
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

    return (
        <section aria-label="Find more transactions" className="space-y-3">
            <div>
                <h3 className="font-semibold text-gray-900">Find more transactions</h3>
                <p className="text-sm text-gray-500">
                    Search by merchant or date for anything not on this trip, such as a booking paid well before it.
                    Transactions on another trip move here when added.
                </p>
            </div>

            <form onSubmit={search} className="flex flex-wrap items-end gap-3">
                <label className="block min-w-[12rem] flex-1 text-xs font-medium text-gray-600">
                    Merchant or details
                    <div className="relative mt-1">
                        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" aria-hidden="true" />
                        <input
                            value={query}
                            onChange={e => setQuery(e.target.value)}
                            placeholder="e.g. Marriott"
                            className={`${inputClass} w-full pl-9`}
                        />
                    </div>
                </label>
                <label className="block text-xs font-medium text-gray-600">
                    From
                    <input type="date" value={start} onChange={e => setStart(e.target.value)} className={`${inputClass} mt-1 block`} />
                </label>
                <label className="block text-xs font-medium text-gray-600">
                    To
                    <input type="date" value={end} min={start} onChange={e => setEnd(e.target.value)} className={`${inputClass} mt-1 block`} />
                </label>
                <button
                    type="submit"
                    disabled={busy}
                    className="rounded-lg bg-gray-800 px-4 py-2 text-sm font-medium text-white hover:bg-gray-900 disabled:opacity-50"
                >
                    Search
                </button>
            </form>

            {error && <div role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

            {results && (results.length === 0 ? (
                <p className="text-sm text-gray-500">No matching transactions outside this trip.</p>
            ) : (
                <>
                    <div className="flex items-center justify-between">
                        <span className="text-sm text-gray-500">
                            {results.length}{results.length === 200 && '+'} found{results.length === 200 && ' (showing the newest 200; narrow the search)'}
                            {' · '}
                            <span className="rounded bg-purple-100 px-1.5 py-0.5 text-xs font-medium text-purple-800">Highlighted</span>
                            {' = within the trip dates'}
                        </span>
                        <button
                            disabled={busy || picked.size === 0}
                            onClick={add}
                            className="rounded-lg bg-purple-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50"
                        >
                            Add {picked.size > 0 ? picked.size : ''} to trip
                        </button>
                    </div>
                    <ul className="divide-y divide-gray-100 rounded-2xl border border-gray-200 bg-white shadow-sm">
                        {results.map(t => {
                            // ISO dates compare correctly as strings
                            const inTripDates = t.date >= trip.startDate && t.date <= trip.endDate;
                            return (
                            <li
                                key={t.transactionId}
                                className={`flex items-center gap-3 px-4 py-2.5 text-sm ${
                                    inTripDates ? 'border-l-4 border-purple-500 bg-purple-50/70' : 'border-l-4 border-transparent'
                                }`}
                            >
                                <input
                                    type="checkbox"
                                    aria-label={`Select ${t.entity}`}
                                    checked={picked.has(t.transactionId!)}
                                    onChange={() => togglePicked(t.transactionId!)}
                                    className="h-4 w-4 flex-shrink-0 rounded border-gray-300"
                                />
                                <span className="w-24 flex-shrink-0 text-gray-500">{formatDate(t.date)}</span>
                                <span className="flex min-w-0 flex-1 items-center gap-2">
                                    <span className="truncate font-medium text-gray-900" title={t.entity}>{t.entity}</span>
                                    <span className="flex-shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-700">{t.account}</span>
                                    {inTripDates && (
                                        <span className="flex-shrink-0 rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-purple-800">
                                            In trip dates
                                        </span>
                                    )}
                                    {t.tripName && (
                                        <span className="flex-shrink-0 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800" title="Adding moves it to this trip">
                                            On: {t.tripName}
                                        </span>
                                    )}
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
                            );
                        })}
                    </ul>
                </>
            ))}
        </section>
    );
}
