import React, { useState } from 'react';
import { Pencil, Plane, Plus, Trash2 } from 'lucide-react';
import { apiService } from '../services/api';
import type { Trip } from '../services/api';
import ConfirmDialog from './ConfirmDialog';
import { TripDetail } from './TripDetail';
import { useTrips } from './useTrips';

const currency = new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD' });

const formatDate = (dateString: string) =>
    new Date(`${dateString}T00:00:00`).toLocaleDateString('en-CA', { year: 'numeric', month: 'short', day: 'numeric' });

const errorMessage = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong');

const EMPTY_TRIP: Trip = { name: '', startDate: '', endDate: '', budget: null };

const inputClass = 'w-full rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-900 focus:border-blue-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20';

export default function TripsView() {
    const { trips, loading, reload } = useTrips();
    const [selected, setSelected] = useState<Trip | null>(null);
    const [editing, setEditing] = useState<Trip | null>(null);
    const [deleting, setDeleting] = useState<Trip | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    const save = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!editing) return;
        setSaving(true);
        setError(null);
        try {
            await apiService.saveTrip(editing);
            setEditing(null);
            await reload();
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setSaving(false);
        }
    };

    const remove = async () => {
        if (!deleting?.tripId) return;
        try {
            await apiService.deleteTrip(deleting.tripId);
            setDeleting(null);
            await reload();
        } catch (err) {
            setDeleting(null);
            setError(errorMessage(err));
        }
    };

    if (selected) {
        return <TripDetail trip={selected} onBack={() => { setSelected(null); reload(); }} onChanged={reload} />;
    }

    return (
        <div className="mx-auto max-w-3xl p-6">
            <div className="mb-6 flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold text-gray-900">Trips</h2>
                    <p className="mt-1 text-sm text-gray-500">Group transactions by trip to see what each one cost.</p>
                </div>
                {!editing && (
                    <button
                        onClick={() => { setError(null); setEditing(EMPTY_TRIP); }}
                        className="inline-flex items-center gap-2 rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-700"
                    >
                        <Plus className="h-4 w-4" aria-hidden="true" /> New trip
                    </button>
                )}
            </div>

            {error && <div role="alert" className="mb-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

            {editing && (
                <form onSubmit={save} className="mb-6 space-y-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                    <h3 className="font-semibold text-gray-900">{editing.tripId ? 'Edit trip' : 'New trip'}</h3>
                    <label className="block text-sm font-medium text-gray-700">
                        Name
                        <input
                            className={`${inputClass} mt-1`}
                            value={editing.name}
                            onChange={e => setEditing({ ...editing, name: e.target.value })}
                            placeholder="e.g. Japan 2026"
                            required
                        />
                    </label>
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                        <label className="block text-sm font-medium text-gray-700">
                            Start date
                            <input type="date" className={`${inputClass} mt-1`} value={editing.startDate} required
                                   onChange={e => setEditing({ ...editing, startDate: e.target.value })} />
                        </label>
                        <label className="block text-sm font-medium text-gray-700">
                            End date
                            <input type="date" className={`${inputClass} mt-1`} value={editing.endDate} required min={editing.startDate}
                                   onChange={e => setEditing({ ...editing, endDate: e.target.value })} />
                        </label>
                        <label className="block text-sm font-medium text-gray-700">
                            Budget (optional)
                            <input type="number" min="0" step="0.01" className={`${inputClass} mt-1`} value={editing.budget ?? ''}
                                   onChange={e => setEditing({ ...editing, budget: e.target.value === '' ? null : Number(e.target.value) })} />
                        </label>
                    </div>
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={() => setEditing(null)}
                                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
                            Cancel
                        </button>
                        <button type="submit" disabled={saving}
                                className="rounded-lg bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-50">
                            {saving ? 'Saving…' : 'Save'}
                        </button>
                    </div>
                </form>
            )}

            {loading ? (
                <div className="py-16 text-center text-gray-500">Loading…</div>
            ) : trips.length === 0 ? (
                <div className="py-16 text-center text-gray-500">No trips yet. Create one to start tracking.</div>
            ) : (
                <ul className="space-y-3">
                    {trips.map(t => (
                        <li key={t.tripId} className="flex items-center gap-2 rounded-2xl border border-gray-200 bg-white shadow-sm">
                            <button
                                onClick={() => setSelected(t)}
                                className="flex min-w-0 flex-1 items-center gap-4 rounded-2xl p-4 text-left hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                            >
                                <Plane className="h-5 w-5 flex-shrink-0 text-purple-600" aria-hidden="true" />
                                <span className="min-w-0">
                                    <span className="block truncate font-semibold text-gray-900">{t.name}</span>
                                    <span className="block text-sm text-gray-500">
                                        {formatDate(t.startDate)} – {formatDate(t.endDate)}
                                        {t.budget != null && ` · Budget ${currency.format(t.budget)}`}
                                    </span>
                                </span>
                                <span className="ml-auto flex-shrink-0 text-right">
                                    <span className="block text-xs font-medium uppercase tracking-wide text-gray-400">Spent</span>
                                    <span className={`block text-lg font-semibold tabular-nums ${
                                        t.budget != null && (t.total ?? 0) > t.budget ? 'text-red-600' : 'text-gray-900'
                                    }`}>
                                        {currency.format(t.total ?? 0)}
                                    </span>
                                </span>
                            </button>
                            <button onClick={() => { setError(null); setEditing(t); }} aria-label={`Edit ${t.name}`}
                                    className="rounded-full p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-700">
                                <Pencil className="h-4 w-4" aria-hidden="true" />
                            </button>
                            <button onClick={() => setDeleting(t)} aria-label={`Delete ${t.name}`}
                                    className="mr-2 rounded-full p-2 text-gray-400 hover:bg-red-50 hover:text-red-600">
                                <Trash2 className="h-4 w-4" aria-hidden="true" />
                            </button>
                        </li>
                    ))}
                </ul>
            )}

            {deleting && (
                <ConfirmDialog
                    title={`Delete ${deleting.name}?`}
                    message="Its transactions are kept; they just stop being tagged with this trip."
                    confirmLabel="Delete trip"
                    onConfirm={remove}
                    onCancel={() => setDeleting(null)}
                />
            )}
        </div>
    );
}
