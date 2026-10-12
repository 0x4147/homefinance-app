import { useCallback, useEffect, useRef, useState } from 'react';
import { apiService } from '../services/api';
import type { Category, CategoryCorrection } from '../services/api';

const TOAST_MS = 10000;

interface Toast {
    message: string;
    correction: CategoryCorrection;
}

/**
 * Shared state for editing categories in place. Correcting one transaction applies to every transaction
 * of the same merchant (and future imports); `overrides` holds the new names for the rows on screen.
 */
export function useCategoryCorrection(onChanged?: () => void) {
    const [categories, setCategories] = useState<Category[]>([]);
    const [overrides, setOverrides] = useState<Record<number, string>>({});
    const [toast, setToast] = useState<Toast | null>(null);
    const [error, setError] = useState('');
    const timer = useRef<number | undefined>(undefined);

    useEffect(() => {
        apiService.getAllCategories().then(setCategories).catch(() => setError('Could not load categories'));
        return () => window.clearTimeout(timer.current);
    }, []);

    const dismiss = useCallback(() => {
        window.clearTimeout(timer.current);
        setToast(null);
    }, []);

    const correct = useCallback(async (transactionId: number, categoryName: string) => {
        setError('');
        try {
            const result = await apiService.correctCategory(transactionId, categoryName);
            if (result.updated === 0) return;
            setOverrides(prev => {
                const next = { ...prev };
                result.changes.forEach(change => { next[change.transactionId] = categoryName; });
                return next;
            });
            window.clearTimeout(timer.current);
            setToast({
                message: `${result.merchant} → ${result.category}: ${result.updated} transaction${result.updated === 1 ? '' : 's'} updated`,
                correction: result,
            });
            timer.current = window.setTimeout(() => setToast(null), TOAST_MS);
            onChanged?.();
        } catch (e) {
            console.error('Error correcting category:', e);
            setError('Could not update the category. Please try again.');
        }
    }, [onChanged]);

    const undo = useCallback(async () => {
        if (!toast) return;
        const { correction } = toast;
        dismiss();
        try {
            await apiService.undoCorrection(correction);
            setOverrides(prev => {
                const next = { ...prev };
                correction.changes.forEach(change => {
                    next[change.transactionId] =
                        categories.find(c => c.categoryId === change.previousCategoryId)?.name ?? 'Unknown';
                });
                return next;
            });
            onChanged?.();
        } catch (e) {
            console.error('Error undoing correction:', e);
            setError('Could not undo the change. Please try again.');
        }
    }, [toast, categories, dismiss, onChanged]);

    const categoryOf = useCallback(
        (transactionId: number | undefined, original: string) =>
            (transactionId !== undefined && overrides[transactionId]) || original,
        [overrides]);

    return { categories, categoryOf, correct, toast, error, undo, dismiss, clearError: () => setError('') };
}

export type CategoryCorrectionState = ReturnType<typeof useCategoryCorrection>;
