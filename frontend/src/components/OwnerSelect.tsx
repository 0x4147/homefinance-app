import { useState } from 'react';
import { apiService } from '../services/api';

const OPTIONS = [
    { value: '', label: 'Split' },
    { value: 'ASANKA', label: 'Asanka only' },
    { value: 'DIVYA', label: 'Divya only' },
];

interface OwnerSelectProps {
    transactionId?: number;
    /** Code of the person who owes all of it; empty/undefined when it is split between both. */
    owner?: string | null;
    /** Called after the owner is saved, e.g. to refresh the balance. */
    onChanged?: () => void;
}

/**
 * Says who a transaction is for. "Split" (the default) shares it between both people; picking a
 * person makes them owe all of it even though the shared card's payer settled it.
 */
export function OwnerSelect({ transactionId, owner, onChanged }: OwnerSelectProps) {
    const [value, setValue] = useState(owner ?? '');
    const [error, setError] = useState(false);

    if (transactionId === undefined) return null;

    const change = async (next: string) => {
        const previous = value;
        setValue(next);
        setError(false);
        try {
            await apiService.setTransactionOwner(transactionId, next || null);
            onChanged?.();
        } catch (e) {
            console.error('Error updating owner:', e);
            setValue(previous);
            setError(true);
        }
    };

    return (
        <select
            value={value}
            onChange={e => change(e.target.value)}
            aria-label="Who owes this transaction"
            title={error ? 'Could not save. Please try again.' : 'Split between both, or owed entirely by one person'}
            className={`rounded-full border-0 px-2 py-0.5 text-xs font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${
                error ? 'bg-red-50 text-red-700'
                    : value ? 'bg-amber-50 text-amber-800' : 'bg-gray-100 text-gray-500'
            }`}
        >
            {OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
    );
}
