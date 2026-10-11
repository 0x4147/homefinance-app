import React, { useEffect, useState } from 'react';
import { apiService } from '../services/api';
import type { Category, Person } from '../services/api';

const ACCOUNTS = ['CIBC', 'AMEX', 'ASANKA', 'DIVYA'];
// Which way the money moved; the category decides how the balance and reports treat it.
const TRANSACTION_TYPES = ['EXPENSE', 'REFUND', 'INCOME', 'TRANSFER'];
const AUTO_CATEGORY = '';

const TYPE_STYLES: Record<string, string> = {
    EXPENSE: 'bg-red-50 text-red-700 ring-red-200',
    REFUND: 'bg-green-50 text-green-700 ring-green-200',
    INCOME: 'bg-green-50 text-green-700 ring-green-200',
    TRANSFER: 'bg-blue-50 text-blue-700 ring-blue-200',
};

const today = () => new Date().toLocaleDateString('en-CA');

const inputClass = 'w-full px-3 py-2 border border-gray-300 rounded-xl focus:ring-2 focus:ring-blue-500 focus:border-blue-500';
const labelClass = 'block text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2';

interface PillProps {
    active: boolean;
    onClick: () => void;
    children: React.ReactNode;
    activeClass?: string;
}

const Pill: React.FC<PillProps> = ({ active, onClick, children, activeClass = 'bg-blue-50 text-blue-700 ring-blue-200' }) => (
    <button
        type="button"
        onClick={onClick}
        className={`px-4 py-2 rounded-full text-sm font-medium ring-1 transition-colors duration-150 ${
            active ? activeClass : 'bg-white text-gray-600 ring-gray-200 hover:bg-gray-50'
        }`}
    >
        {children}
    </button>
);

const AddTransaction: React.FC = () => {
    const [persons, setPersons] = useState<Person[]>([]);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [date, setDate] = useState(today());
    const [merchant, setMerchant] = useState('');
    const [amount, setAmount] = useState('');
    const [details, setDetails] = useState('');
    const [account, setAccount] = useState(ACCOUNTS[0]);
    const [transactionType, setTransactionType] = useState(TRANSACTION_TYPES[0]);
    const [personId, setPersonId] = useState('');
    const [categories, setCategories] = useState<Category[]>([]);
    const [category, setCategory] = useState(AUTO_CATEGORY);

    const [isSaving, setIsSaving] = useState(false);
    const [message, setMessage] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);

    useEffect(() => {
        apiService.getPersons()
            .then(setPersons)
            .catch((error) => {
                console.error('Failed to load card holders', error);
                setLoadError('Could not load card holders. Refresh the page to try again.');
            });
        apiService.getAllCategories()
            .then(setCategories)
            .catch((error) => console.error('Failed to load categories', error));
    }, []);

    const parsedAmount = Number(amount);
    const canSave =
        date !== '' &&
        merchant.trim() !== '' &&
        parsedAmount > 0 &&
        personId !== '' &&
        !isSaving;

    const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!canSave) return;

        setIsSaving(true);
        setMessage(null);
        const transaction = {
            amount: parsedAmount,
            date,
            entity: merchant.trim(),
            details: details.trim() || undefined,
            account,
            transactionType,
            category: category || undefined,
            person: personId,
        };
        try {
            try {
                const saved = await apiService.saveTransaction(transaction);
                const outcome = saved.uncategorizedTransaction
                    ? 'It was not matched to a category and is in the review queue.'
                    : 'It was categorized automatically.';
                setMessage({ kind: 'success', text: `Saved ${merchant.trim()} for $${parsedAmount.toFixed(2)}. ${outcome}` });
            } catch (error) {
                if (!(error instanceof Error) || !error.message.includes('409')) throw error;
                const confirmed = window.confirm(
                    'A transaction with the same account, date, merchant and amount already exists. Save anyway?'
                );
                if (!confirmed) {
                    setMessage({ kind: 'error', text: 'Not saved: duplicate of an existing transaction.' });
                    return;
                }
                await apiService.saveTransaction(transaction, true);
                setMessage({ kind: 'success', text: `Saved ${merchant.trim()} for $${parsedAmount.toFixed(2)}.` });
            }
            setMerchant('');
            setAmount('');
            setDetails('');
        } catch (error) {
            console.error('Failed to save transaction', error);
            setMessage({ kind: 'error', text: 'Could not save the transaction. Check the fields and try again.' });
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="animate-fade-in max-w-2xl mx-auto">
            <h2 className="text-3xl font-bold text-gray-900">Add Transaction</h2>
            <p className="mt-1 mb-6 text-gray-500">Record a single transaction by hand.</p>

            {loadError && (
                <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 text-red-700">{loadError}</div>
            )}

            <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
                {/* Hero amount + type */}
                <div className="px-6 pt-8 pb-6 bg-gradient-to-b from-gray-50 to-white border-b border-gray-100 text-center">
                    <label className={labelClass}>Amount</label>
                    <div className="flex items-center justify-center gap-1">
                        <span className="text-4xl font-semibold text-gray-400">$</span>
                        <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={amount}
                            onChange={(e) => setAmount(e.target.value)}
                            placeholder="0.00"
                            className="w-48 text-5xl font-bold text-gray-900 bg-transparent text-center outline-none placeholder-gray-300"
                        />
                    </div>
                    <div className="mt-5 flex flex-wrap justify-center gap-2">
                        {TRANSACTION_TYPES.map((option) => (
                            <Pill
                                key={option}
                                active={transactionType === option}
                                onClick={() => setTransactionType(option)}
                                activeClass={TYPE_STYLES[option]}
                            >
                                {option.charAt(0) + option.slice(1).toLowerCase()}
                            </Pill>
                        ))}
                    </div>
                </div>

                <div className="p-6 space-y-6">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label className={labelClass}>Merchant</label>
                            <input
                                type="text"
                                value={merchant}
                                onChange={(e) => setMerchant(e.target.value)}
                                placeholder="e.g. TIM HORTONS"
                                className={inputClass}
                            />
                        </div>
                        <div>
                            <label className={labelClass}>Date</label>
                            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputClass} />
                        </div>
                    </div>

                    <div>
                        <label className={labelClass}>Account</label>
                        <div className="flex flex-wrap gap-2">
                            {ACCOUNTS.map((option) => (
                                <Pill key={option} active={account === option} onClick={() => setAccount(option)}>
                                    {option}
                                </Pill>
                            ))}
                        </div>
                    </div>

                    <div>
                        <label className={labelClass}>Card holder</label>
                        <div className="flex flex-wrap gap-2">
                            {persons.map((person) => (
                                <Pill
                                    key={person.personId}
                                    active={personId === String(person.personId)}
                                    onClick={() => setPersonId(String(person.personId))}
                                >
                                    {person.name}
                                </Pill>
                            ))}
                            {persons.length === 0 && !loadError && <span className="text-sm text-gray-400">Loading…</span>}
                        </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label className={labelClass}>Category</label>
                            <select value={category} onChange={(e) => setCategory(e.target.value)} className={inputClass}>
                                <option value={AUTO_CATEGORY}>Automatic (from merchant rules)</option>
                                {categories.map((option) => (
                                    <option key={option.categoryId} value={option.name}>{option.name}</option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label className={labelClass}>Details (optional)</label>
                            <input type="text" value={details} onChange={(e) => setDetails(e.target.value)} className={inputClass} />
                        </div>
                    </div>

                    <p className="text-sm text-gray-500 bg-gray-50 rounded-xl p-3">
                        Leave the category on Automatic to use your merchant rules; unmatched merchants go to the review queue.
                        Enter the amount as a positive number: an expense is saved as money out, and a refund, income or
                        transfer as money in.
                    </p>

                    {message && (
                        <div
                            className={`p-4 rounded-xl border ${
                                message.kind === 'success'
                                    ? 'bg-green-50 border-green-200 text-green-700'
                                    : 'bg-red-50 border-red-200 text-red-700'
                            }`}
                        >
                            {message.text}
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={!canSave}
                        className={`w-full py-3 rounded-xl font-semibold transition-colors duration-200 ${
                            canSave
                                ? 'bg-blue-600 text-white hover:bg-blue-700 shadow-md'
                                : 'bg-gray-200 text-gray-400 cursor-not-allowed'
                        }`}
                    >
                        {isSaving ? 'Saving...' : 'Save transaction'}
                    </button>
                </div>
            </form>
        </div>
    );
};

export default AddTransaction;
