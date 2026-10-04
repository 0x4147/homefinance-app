import React, { useEffect, useState } from 'react';
import { apiService } from '../services/api';
import type { Person } from '../services/api';

const ACCOUNTS = ['CIBC', 'AMEX', 'ASANKA', 'DIVYA'];
const TRANSACTION_TYPES = ['EXPENSE', 'INCOME', 'CARDPAYMENT', 'REFUND', 'BILL', 'RENTALBILLINCOME', 'RENTALRENTINCOME'];

const today = () => new Date().toLocaleDateString('en-CA');

const inputClass = 'w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500';

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

    const [isSaving, setIsSaving] = useState(false);
    const [message, setMessage] = useState<{ kind: 'success' | 'error'; text: string } | null>(null);

    useEffect(() => {
        apiService.getPersons()
            .then(setPersons)
            .catch((error) => {
                console.error('Failed to load card holders', error);
                setLoadError('Could not load card holders. Refresh the page to try again.');
            });
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
        <div className="animate-fade-in max-w-3xl mx-auto">
            <h2 className="text-3xl font-bold text-gray-900 mb-6">Add Transaction</h2>

            {loadError && (
                <div className="mb-4 p-4 rounded-lg bg-red-50 border border-red-200 text-red-700">{loadError}</div>
            )}

            <form onSubmit={handleSubmit} className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm space-y-5">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Date</label>
                        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputClass} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Amount</label>
                        <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={amount}
                            onChange={(e) => setAmount(e.target.value)}
                            placeholder="0.00"
                            className={inputClass}
                        />
                    </div>
                </div>

                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Merchant</label>
                    <input
                        type="text"
                        value={merchant}
                        onChange={(e) => setMerchant(e.target.value)}
                        placeholder="e.g. TIM HORTONS"
                        className={inputClass}
                    />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Card holder</label>
                        <select value={personId} onChange={(e) => setPersonId(e.target.value)} className={inputClass}>
                            <option value="">Choose a person...</option>
                            {persons.map((person) => (
                                <option key={person.personId} value={person.personId}>
                                    {person.name}
                                </option>
                            ))}
                        </select>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Account</label>
                        <select value={account} onChange={(e) => setAccount(e.target.value)} className={inputClass}>
                            {ACCOUNTS.map((option) => (
                                <option key={option} value={option}>{option}</option>
                            ))}
                        </select>
                    </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Transaction type</label>
                        <select value={transactionType} onChange={(e) => setTransactionType(e.target.value)} className={inputClass}>
                            {TRANSACTION_TYPES.map((option) => (
                                <option key={option} value={option}>{option}</option>
                            ))}
                        </select>
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-2">Details (optional)</label>
                        <input type="text" value={details} onChange={(e) => setDetails(e.target.value)} className={inputClass} />
                    </div>
                </div>

                <p className="text-sm text-gray-500">
                    The category is assigned automatically from your merchant rules. Unmatched merchants go to the review queue.
                    Refunds and card payments are saved as negative amounts automatically. Enter the amount as a positive number.
                </p>

                {message && (
                    <div
                        className={`p-4 rounded-lg border ${
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
                    className={`px-6 py-2 rounded-lg font-medium transition-colors duration-200 ${
                        canSave
                            ? 'bg-blue-600 text-white hover:bg-blue-700 shadow-md'
                            : 'bg-gray-300 text-gray-500 cursor-not-allowed'
                    }`}
                >
                    {isSaving ? 'Saving...' : 'Save transaction'}
                </button>
            </form>
        </div>
    );
};

export default AddTransaction;
