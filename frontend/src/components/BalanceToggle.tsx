import { createPortal } from 'react-dom';
import { Check, Minus } from 'lucide-react';
import type { Category } from '../services/api';
import type { BalanceToggleState } from './useBalanceToggle';

/** Undo/error toast for {@link useBalanceToggle}. */
export function BalanceToast({ state }: { state: BalanceToggleState }) {
    const { toast, error, undo, dismiss, clearError } = state;
    if (!toast && !error) return null;
    return createPortal(
        <div
            role="status"
            aria-live="polite"
            style={{ position: 'fixed', bottom: 24, left: '50%', transform: 'translateX(-50%)', zIndex: 1000 }}
            className="flex max-w-[90vw] items-center gap-4 rounded-lg bg-gray-900 px-5 py-3 text-sm text-white shadow-2xl ring-1 ring-white/20"
        >
            <span>{error || toast?.message}</span>
            {error ? (
                <button onClick={clearError} className="font-semibold text-gray-300 hover:text-white">Dismiss</button>
            ) : (
                <>
                    <button onClick={undo} className="font-semibold text-blue-300 hover:text-blue-200">Undo</button>
                    <button onClick={dismiss} aria-label="Dismiss" className="text-gray-400 hover:text-white">✕</button>
                </>
            )}
        </div>,
        document.body);
}

interface BalanceToggleProps {
    transactionId?: number;
    /** The transaction's own flag; false means it was left out of the balance. */
    shared: boolean;
    category: string;
    categories: Category[];
    onToggle: (next: boolean) => void;
}

/**
 * Shows whether a transaction counts toward the shared balance and lets you include or exclude it.
 * It counts when it is shared and its category counts; a category that doesn't count can't be
 * overridden here, so the toggle is disabled with a hint to change the category instead.
 */
export function BalanceToggle({ transactionId, shared, category, categories, onToggle }: BalanceToggleProps) {
    const categoryCounts = categories.find(c => c.name === category)?.inBalance ?? false;
    const counts = shared && categoryCounts;

    const base = 'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium';

    if (!categoryCounts) {
        const reason = categories.some(c => c.name === category)
            ? `“${category}” doesn't count toward the balance. Change the category to include this transaction.`
            : 'Uncategorized transactions don\'t count until they are categorized.';
        return (
            <span
                title={reason}
                aria-disabled="true"
                className={`${base} cursor-not-allowed bg-gray-100 text-gray-400`}
            >
                <Minus className="h-3 w-3" aria-hidden="true" />
                Not counted
            </span>
        );
    }

    if (transactionId === undefined) {
        return (
            <span className={`${base} ${counts ? 'bg-emerald-50 text-emerald-700' : 'bg-gray-100 text-gray-500'}`}>
                {counts ? <Check className="h-3 w-3" aria-hidden="true" /> : <Minus className="h-3 w-3" aria-hidden="true" />}
                {counts ? 'Counted' : 'Excluded'}
            </span>
        );
    }

    return (
        <button
            type="button"
            role="switch"
            aria-checked={counts}
            onClick={() => onToggle(!shared)}
            title={counts ? 'Counts toward the balance. Click to leave it out.' : 'Left out of the balance. Click to include it.'}
            className={`${base} transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 ${
                counts
                    ? 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'
                    : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
            }`}
        >
            {counts ? <Check className="h-3 w-3" aria-hidden="true" /> : <Minus className="h-3 w-3" aria-hidden="true" />}
            {counts ? 'Counted' : 'Excluded'}
        </button>
    );
}
