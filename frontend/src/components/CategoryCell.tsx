import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Pencil, Search } from 'lucide-react';
import type { Category } from '../services/api';
import type { CategoryCorrectionState } from './useCategoryCorrection';

const POPOVER_WIDTH = 256;
const POPOVER_HEIGHT = 300;

/** Undo/confirmation toast for {@link useCategoryCorrection}. */
export function CorrectionToast({ state }: { state: CategoryCorrectionState }) {
    const { toast, error, undo, dismiss, clearError } = state;
    if (!toast && !error) return null;
    // Portal to <body> so no modal/drawer ancestor (stacking context, transform, overflow) can hide it
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

interface CategoryCellProps {
    transactionId?: number;
    entity: string;
    category: string;
    categories: Category[];
    onSelect: (categoryName: string) => void;
}

/** A category chip that opens a searchable picker; plain text when the transaction has no id. */
export function CategoryCell({ transactionId, entity, category, categories, onSelect }: CategoryCellProps) {
    const [anchor, setAnchor] = useState<DOMRect | null>(null);
    const [query, setQuery] = useState('');
    const buttonRef = useRef<HTMLButtonElement>(null);
    const popoverRef = useRef<HTMLDivElement>(null);

    const close = useCallback(() => {
        setAnchor(null);
        setQuery('');
    }, []);

    useEffect(() => {
        if (!anchor) return;
        const onPointerDown = (event: MouseEvent) => {
            const target = event.target as Node;
            if (!popoverRef.current?.contains(target) && !buttonRef.current?.contains(target)) close();
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                // Capture phase + stop: Escape closes only the picker, not a dialog it sits in
                event.stopPropagation();
                close();
                buttonRef.current?.focus();
            }
        };
        document.addEventListener('mousedown', onPointerDown);
        document.addEventListener('keydown', onKeyDown, true);
        return () => {
            document.removeEventListener('mousedown', onPointerDown);
            document.removeEventListener('keydown', onKeyDown, true);
        };
    }, [anchor, close]);

    const matches = useMemo(() => {
        const needle = query.trim().toLowerCase();
        return categories
            .filter(c => c.name.toLowerCase().includes(needle))
            .sort((a, b) => a.name.localeCompare(b.name));
    }, [categories, query]);

    if (transactionId === undefined) {
        return <>{category}</>;
    }

    const position = anchor && {
        left: Math.max(8, Math.min(anchor.left, window.innerWidth - POPOVER_WIDTH - 8)),
        top: anchor.bottom + POPOVER_HEIGHT + 8 > window.innerHeight
            ? Math.max(8, anchor.top - POPOVER_HEIGHT - 4)
            : anchor.bottom + 4,
    };

    return (
        <>
            <button
                ref={buttonRef}
                type="button"
                onClick={() => (anchor ? close() : setAnchor(buttonRef.current?.getBoundingClientRect() ?? null))}
                aria-haspopup="listbox"
                aria-expanded={anchor !== null}
                title="Change category"
                className="group inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 -mx-2 text-sm text-gray-900 hover:bg-blue-50 hover:text-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            >
                {category}
                <Pencil className="h-3 w-3 opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100" aria-hidden="true" />
            </button>
            {position && createPortal(
                <div
                    ref={popoverRef}
                    style={{ position: 'fixed', left: position.left, top: position.top, width: POPOVER_WIDTH }}
                    className="z-[60] rounded-lg border border-gray-200 bg-white shadow-xl"
                >
                    <div className="flex items-center gap-2 border-b border-gray-100 px-3 py-2">
                        <Search className="h-4 w-4 text-gray-400" aria-hidden="true" />
                        <input
                            autoFocus
                            value={query}
                            onChange={e => setQuery(e.target.value)}
                            onKeyDown={e => {
                                if (e.key === 'Enter' && matches.length > 0) {
                                    onSelect(matches[0].name);
                                    close();
                                }
                            }}
                            placeholder="Search categories"
                            aria-label="Search categories"
                            className="w-full text-sm outline-none"
                        />
                    </div>
                    <ul role="listbox" className="max-h-52 overflow-y-auto py-1">
                        {matches.length === 0 && <li className="px-3 py-2 text-sm text-gray-500">No matching category</li>}
                        {matches.map(c => (
                            <li key={c.categoryId} role="option" aria-selected={c.name === category}>
                                <button
                                    type="button"
                                    onClick={() => { onSelect(c.name); close(); }}
                                    className={`w-full px-3 py-1.5 text-left text-sm hover:bg-blue-50 ${c.name === category ? 'font-semibold text-blue-700' : 'text-gray-900'}`}
                                >
                                    {c.name}
                                </button>
                            </li>
                        ))}
                    </ul>
                    <p className="border-t border-gray-100 px-3 py-2 text-xs text-gray-500">
                        Applies to all “{entity}” transactions, past and future.
                    </p>
                </div>,
                document.body)}
        </>
    );
}
