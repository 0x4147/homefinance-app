import React, { useState, useEffect } from 'react';
import { CheckCircle, AlertCircle, Clock, DollarSign, Calendar, Building } from 'lucide-react';
import { apiService } from '../services/api';

interface UncategorizedTransaction {
    id: number;
    merchant: string;
    details: string;
    amount: number;
    date: string;
    suggestedCategories: string;
    confidenceScore: number;
    createdAt: string;
    reviewed: boolean;
    assignedCategory?: string;
    reviewedAt?: string;
}

interface Category {
    categoryId: number;
    name: string;
    type: string;
    description?: string;
}

const CategorizationReview: React.FC = () => {
    const [uncategorizedTransactions, setUncategorizedTransactions] = useState<UncategorizedTransaction[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [selectedTransaction, setSelectedTransaction] = useState<UncategorizedTransaction | null>(null);
    const [selectedCategory, setSelectedCategory] = useState<string>('');
    const [isProcessing, setIsProcessing] = useState(false);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            setIsLoading(true);
            const [uncategorizedResponse, categoriesResponse] = await Promise.all([
                apiService.getUncategorizedTransactions(),
                apiService.getAllCategories()
            ]);
            
            setUncategorizedTransactions(uncategorizedResponse);
            setCategories(categoriesResponse);
        } catch (error) {
            console.error('Error loading data:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const handleReviewTransaction = async () => {
        if (!selectedTransaction || !selectedCategory) {
            alert('Please select a transaction and category');
            return;
        }

        try {
            setIsProcessing(true);
            await apiService.reviewUncategorizedTransaction(selectedTransaction.id, selectedCategory);
            
            // Remove the reviewed transaction from the list
            setUncategorizedTransactions(prev => 
                prev.filter(t => t.id !== selectedTransaction.id)
            );
            
            // Reset selection
            setSelectedTransaction(null);
            setSelectedCategory('');
            
            alert('Transaction categorized successfully!');
        } catch (error) {
            console.error('Error reviewing transaction:', error);
            alert('Failed to categorize transaction. Please try again.');
        } finally {
            setIsProcessing(false);
        }
    };

    const getConfidenceColor = (score: number) => {
        if (score >= 0.7) return 'text-green-600';
        if (score >= 0.4) return 'text-yellow-600';
        return 'text-red-600';
    };

    const getConfidenceIcon = (score: number) => {
        if (score >= 0.7) return <CheckCircle className="w-4 h-4" />;
        if (score >= 0.4) return <AlertCircle className="w-4 h-4" />;
        return <Clock className="w-4 h-4" />;
    };

    const formatCurrency = (amount: number) => {
        return new Intl.NumberFormat('en-CA', {
            style: 'currency',
            currency: 'CAD'
        }).format(Math.abs(amount));
    };

    const formatDate = (dateString: string) => {
        return new Date(dateString).toLocaleDateString('en-CA', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    };

    const parseSuggestedCategories = (categoriesJson: string): string[] => {
        try {
            return JSON.parse(categoriesJson);
        } catch {
            return [];
        }
    };

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
                <span className="ml-3 text-gray-600">Loading uncategorized transactions...</span>
            </div>
        );
    }

    return (
        <div className="animate-fade-in max-w-7xl mx-auto">
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-3xl font-bold text-gray-900">Transaction Categorization Review</h2>
                    <p className="mt-2 text-gray-600">
                        Review and categorize transactions that couldn't be automatically categorized
                    </p>
                </div>
                <div className="text-right">
                    <div className="text-2xl font-bold text-blue-600">{uncategorizedTransactions.length}</div>
                    <div className="text-sm text-gray-500">Pending Review</div>
                </div>
            </div>

            {uncategorizedTransactions.length === 0 ? (
                <div className="bg-white border border-gray-200 rounded-xl p-8 shadow-sm text-center">
                    <CheckCircle className="mx-auto h-12 w-12 text-green-500 mb-4" />
                    <h3 className="text-lg font-medium text-gray-900 mb-2">All Caught Up!</h3>
                    <p className="text-gray-600">
                        All transactions have been categorized. The system will flag new uncategorized transactions here.
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Transaction List */}
                    <div className="lg:col-span-2">
                        <div className="bg-white border border-gray-200 rounded-xl shadow-sm">
                            <div className="px-6 py-4 border-b border-gray-200">
                                <h3 className="text-lg font-semibold text-gray-900">
                                    Uncategorized Transactions
                                </h3>
                            </div>
                            <div className="divide-y divide-gray-200 max-h-96 overflow-y-auto">
                                {uncategorizedTransactions.map((transaction) => (
                                    <div
                                        key={transaction.id}
                                        onClick={() => setSelectedTransaction(transaction)}
                                        className={`p-4 cursor-pointer transition-colors duration-200 hover:bg-gray-50 ${
                                            selectedTransaction?.id === transaction.id ? 'bg-blue-50 border-r-4 border-blue-500' : ''
                                        }`}
                                    >
                                        <div className="flex items-start justify-between">
                                            <div className="flex-1">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <Building className="w-4 h-4 text-gray-400" />
                                                    <span className="font-medium text-gray-900">
                                                        {transaction.merchant}
                                                    </span>
                                                </div>
                                                {transaction.details && (
                                                    <p className="text-sm text-gray-600 mb-2">
                                                        {transaction.details}
                                                    </p>
                                                )}
                                                <div className="flex items-center gap-4 text-sm text-gray-500">
                                                    <div className="flex items-center gap-1">
                                                        <DollarSign className="w-4 h-4" />
                                                        <span className="font-medium text-gray-900">
                                                            {formatCurrency(transaction.amount)}
                                                        </span>
                                                    </div>
                                                    <div className="flex items-center gap-1">
                                                        <Calendar className="w-4 h-4" />
                                                        <span>{formatDate(transaction.date)}</span>
                                                    </div>
                                                </div>
                                            </div>
                                            <div className={`flex items-center gap-1 ${getConfidenceColor(transaction.confidenceScore)}`}>
                                                {getConfidenceIcon(transaction.confidenceScore)}
                                                <span className="text-xs font-medium">
                                                    {(transaction.confidenceScore * 100).toFixed(0)}%
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>

                    {/* Categorization Panel */}
                    <div className="lg:col-span-1">
                        <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-6">
                            <h3 className="text-lg font-semibold text-gray-900 mb-4">
                                Categorize Transaction
                            </h3>
                            
                            {selectedTransaction ? (
                                <div className="space-y-4">
                                    {/* Transaction Details */}
                                    <div className="p-4 bg-gray-50 rounded-lg">
                                        <h4 className="font-medium text-gray-900 mb-2">Selected Transaction</h4>
                                        <div className="space-y-2 text-sm">
                                            <div>
                                                <span className="text-gray-600">Merchant:</span>
                                                <span className="ml-2 font-medium">{selectedTransaction.merchant}</span>
                                            </div>
                                            <div>
                                                <span className="text-gray-600">Amount:</span>
                                                <span className="ml-2 font-medium">{formatCurrency(selectedTransaction.amount)}</span>
                                            </div>
                                            <div>
                                                <span className="text-gray-600">Date:</span>
                                                <span className="ml-2 font-medium">{formatDate(selectedTransaction.date)}</span>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Suggested Categories */}
                                    {selectedTransaction.suggestedCategories && (
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                                Suggested Categories
                                            </label>
                                            <div className="flex flex-wrap gap-2 mb-4">
                                                {parseSuggestedCategories(selectedTransaction.suggestedCategories).map((category, index) => (
                                                    <button
                                                        key={index}
                                                        onClick={() => setSelectedCategory(category)}
                                                        className={`px-3 py-1 text-xs rounded-full border transition-colors duration-200 ${
                                                            selectedCategory === category
                                                                ? 'bg-blue-100 border-blue-300 text-blue-700'
                                                                : 'bg-gray-100 border-gray-300 text-gray-700 hover:bg-gray-200'
                                                        }`}
                                                    >
                                                        {category}
                                                    </button>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    {/* Category Selection */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">
                                            Select Category
                                        </label>
                                        <select
                                            value={selectedCategory}
                                            onChange={(e) => setSelectedCategory(e.target.value)}
                                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                        >
                                            <option value="">Choose a category...</option>
                                            {categories.map((category) => (
                                                <option key={category.categoryId} value={category.name}>
                                                    {category.name}
                                                </option>
                                            ))}
                                        </select>
                                    </div>

                                    {/* Action Buttons */}
                                    <div className="space-y-2">
                                        <button
                                            onClick={handleReviewTransaction}
                                            disabled={!selectedCategory || isProcessing}
                                            className={`w-full px-4 py-2 rounded-lg font-medium transition-colors duration-200 ${
                                                selectedCategory && !isProcessing
                                                    ? 'bg-green-600 text-white hover:bg-green-700 shadow-md'
                                                    : 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                            }`}
                                        >
                                            {isProcessing ? 'Processing...' : 'Categorize Transaction'}
                                        </button>
                                        <button
                                            onClick={() => {
                                                setSelectedTransaction(null);
                                                setSelectedCategory('');
                                            }}
                                            className="w-full px-4 py-2 border border-gray-300 rounded-lg font-medium text-gray-700 hover:bg-gray-50 transition-colors duration-200"
                                        >
                                            Clear Selection
                                        </button>
                                    </div>
                                </div>
                            ) : (
                                <div className="text-center text-gray-500 py-8">
                                    <AlertCircle className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                                    <p className="text-sm">Select a transaction to categorize it</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default CategorizationReview;


