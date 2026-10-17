import { useState } from 'react';
import { apiService } from '../services/api';
import type { Trip } from '../services/api';

interface TripPickerProps {
    transactionId?: number;
    /** Id of the trip the transaction is tagged with; empty/undefined when it is not on a trip. */
    tripId?: number | null;
    trips: Trip[];
    /** Called after the tag is saved, with the new trip id (null when cleared). */
    onChanged?: (tripId: number | null) => void;
}

/** Tags a transaction with a trip, or clears the tag. A reporting label only: it never changes the balance. */
export function TripPicker({ transactionId, tripId, trips, onChanged }: TripPickerProps) {
    const [value, setValue] = useState(tripId != null ? String(tripId) : '');
    const [error, setError] = useState(false);

    if (transactionId === undefined) return null;

    const change = async (next: string) => {
        const previous = value;
        setValue(next);
        setError(false);
        try {
            const id = next ? Number(next) : null;
            await apiService.setTransactionTrip(transactionId, id);
            onChanged?.(id);
        } catch (e) {
            console.error('Error updating trip:', e);
            setValue(previous);
            setError(true);
        }
    };

    return (
        <select
            value={value}
            onChange={e => change(e.target.value)}
            aria-label="Trip this transaction belongs to"
            title={error ? 'Could not save. Please try again.' : 'Tag this transaction with a trip'}
            className={`max-w-[10rem] truncate rounded-full border-0 px-2 py-0.5 text-xs font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${
                error ? 'bg-red-50 text-red-700'
                    : value ? 'bg-sky-50 text-sky-800' : 'bg-gray-100 text-gray-500'
            }`}
        >
            <option value="">No trip</option>
            {trips.map(t => <option key={t.tripId} value={t.tripId}>{t.name}</option>)}
        </select>
    );
}
