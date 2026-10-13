import { useCallback, useEffect, useRef, useState } from 'react';
import { apiService } from '../services/api';

const TOAST_MS = 8000;

interface Toast {
    message: string;
    transactionId: number;
    previous: boolean;
}

/**
 * Shared state for including a transaction in, or excluding it from, the shared balance.
 * Unlike a category correction this affects only the one transaction; `overrides` holds the
 * new flag for the rows on screen.
 */
export function useBalanceToggle(onChanged?: () => void) {
    const [overrides, setOverrides] = useState<Record<number, boolean>>({});
    const [toast, setToast] = useState<Toast | null>(null);
    const [error, setError] = useState('');
    const timer = useRef<number | undefined>(undefined);

    useEffect(() => () => window.clearTimeout(timer.current), []);

    const dismiss = useCallback(() => {
        window.clearTimeout(timer.current);
        setToast(null);
    }, []);

    const save = useCallback(async (transactionId: number, shared: boolean) => {
        const result = await apiService.setTransactionShared(transactionId, shared);
        setOverrides(prev => ({ ...prev, [transactionId]: result.shared ?? shared }));
        onChanged?.();
    }, [onChanged]);

    const setShared = useCallback(async (transactionId: number, entity: string, current: boolean, next: boolean) => {
        setError('');
        try {
            await save(transactionId, next);
            window.clearTimeout(timer.current);
            setToast({
                message: next ? `${entity} now counts toward the balance` : `${entity} left out of the balance`,
                transactionId,
                previous: current,
            });
            timer.current = window.setTimeout(() => setToast(null), TOAST_MS);
        } catch (e) {
            console.error('Error updating balance flag:', e);
            setError('Could not update the transaction. Please try again.');
        }
    }, [save]);

    const undo = useCallback(async () => {
        if (!toast) return;
        const { transactionId, previous } = toast;
        dismiss();
        try {
            await save(transactionId, previous);
        } catch (e) {
            console.error('Error undoing balance flag:', e);
            setError('Could not undo the change. Please try again.');
        }
    }, [toast, dismiss, save]);

    const sharedOf = useCallback(
        (transactionId: number | undefined, original: boolean) =>
            transactionId !== undefined && transactionId in overrides ? overrides[transactionId] : original,
        [overrides]);

    return { sharedOf, setShared, toast, error, undo, dismiss, clearError: () => setError('') };
}

export type BalanceToggleState = ReturnType<typeof useBalanceToggle>;
