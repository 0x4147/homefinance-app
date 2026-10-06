import React, { useState, useEffect } from 'react';
import { CheckCircle, AlertCircle, DollarSign, Building, RefreshCw } from 'lucide-react';
import { apiService, type Category, type ReviewGroup } from '../services/api';

const CategorizationReview: React.FC = () => {
    const [groups, setGroups] = useState<ReviewGroup[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [selectedGroup, setSelectedGroup] = useState<ReviewGroup | null>(null);
    const [selectedCategory, setSelectedCategory] = useState<string>('');
    const [isProcessing, setIsProcessing] = useState(false);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            setIsLoading(true);
            const [groupsResponse, categoriesResponse] = await Promise.all([
                apiService.getUncategorizedGroups(),
                apiService.getAllCategories()
            ]);

            setGroups(groupsResponse);
            setCategories(categoriesResponse);
        } catch (error) {
            console.error('Error loading data:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const clearSelection = () => {
        setSelectedGroup(null);
        setSelectedCategory('');
    };

    // Saving a rule can clear other groups too (name variants of the same merchant), so reload.
    const handleReviewGroup = async () => {
        if (!selectedGroup || !selectedCategory) {
            alert('Please select a merchant and category');
            return;
        }

        try {
            setIsProcessing(true);
            const { resolved } = await apiService.reviewGroup(selectedGroup.key, selectedCategory);
            clearSelection();
            await loadData();
            alert(`Categorized ${resolved} transaction${resolved === 1 ? '' : 's'} as ${selectedCategory}`);
        } catch (error) {
            console.error('Error reviewing merchant group:', error);
            alert('Failed to categorize. Please try again.');
        } finally {
            setIsProcessing(false);
        }
    };

    const handleRecategorize = async () => {
        try {
            setIsProcessing(true);
            const { resolved, remaining } = await apiService.recategorizePending();
            clearSelection();
            await loadData();
            alert(`Rules resolved ${resolved} transactions; ${remaining} still need review`);
        } catch (error) {
            console.error('Error re-running categorization:', error);
            alert('Failed to re-run categorization. Please try again.');
        } finally {
            setIsProcessing(false);
        }
    };

    const formatCurrency = (amount: number) => {
        return new Intl.NumberFormat('en-CA', {
            style: 'currency',
            currency: 'CAD'
        }).format(Math.abs(amount));
    };

    const pendingTransactions = groups.reduce((sum, group) => sum + group.count, 0);

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
                        One decision categorizes every transaction from that merchant, now and on future imports
                    </p>
                </div>
                <div className="flex items-center gap-6">
                    <button
                        onClick={handleRecategorize}
                        disabled={isProcessing}
                        className="flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                    >
                        <RefreshCw className="w-4 h-4" />
                        Re-run rules
                    </button>
                    <div className="text-right">
                        <div className="text-2xl font-bold text-blue-600">{groups.length}</div>
                        <div className="text-sm text-gray-500">
                            Merchants ({pendingTransactions} transactions)
                        </div>
                    </div>
                </div>
            </div>

            {groups.length === 0 ? (
                <div className="bg-white border border-gray-200 rounded-xl p-8 shadow-sm text-center">
                    <CheckCircle className="mx-auto h-12 w-12 text-green-500 mb-4" />
                    <h3 className="text-lg font-medium text-gray-900 mb-2">All Caught Up!</h3>
                    <p className="text-gray-600">
                        All transactions have been categorized. The system will flag new uncategorized transactions here.
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Merchant List */}
                    <div className="lg:col-span-2">
                        <div className="bg-white border border-gray-200 rounded-xl shadow-sm">
                            <div className="px-6 py-4 border-b border-gray-200">
                                <h3 className="text-lg font-semibold text-gray-900">
                                    Uncategorized Merchants
                                </h3>
                                <p className="text-sm text-gray-500">Largest spend first</p>
                            </div>
                            <div className="divide-y divide-gray-200 max-h-[32rem] overflow-y-auto">
                                {groups.map((group) => (
                                    <div
                                        key={group.key}
                                        onClick={() => {
                                            setSelectedGroup(group);
                                            setSelectedCategory(group.suggestions[0] ?? '');
                                        }}
                                        className={`p-4 cursor-pointer transition-colors duration-200 hover:bg-gray-50 ${
                                            selectedGroup?.key === group.key ? 'bg-blue-50 border-r-4 border-blue-500' : ''
                                        }`}
                                    >
                                        <div className="flex items-start justify-between gap-4">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <Building className="w-4 h-4 text-gray-400 shrink-0" />
                                                    <span className="font-medium text-gray-900 truncate">
                                                        {group.merchant}
                                                    </span>
                                                </div>
                                                <div className="flex items-center gap-4 text-sm text-gray-500">
                                                    <span>{group.count} transaction{group.count === 1 ? '' : 's'}</span>
                                                    <div className="flex items-center gap-1">
                                                        <DollarSign className="w-4 h-4" />
                                                        <span className="font-medium text-gray-900">
                                                            {formatCurrency(group.total)}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                            {group.suggestions.length > 0 && (
                                                <span className="text-xs px-2 py-1 rounded-full bg-gray-100 text-gray-700 shrink-0">
                                                    {group.suggestions[0]}?
                                                </span>
                                            )}
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
                                Categorize Merchant
                            </h3>

                            {selectedGroup ? (
                                <div className="space-y-4">
                                    <div className="p-4 bg-gray-50 rounded-lg">
                                        <h4 className="font-medium text-gray-900 mb-2">Selected Merchant</h4>
                                        <div className="space-y-2 text-sm">
                                            <div>
                                                <span className="text-gray-600">Merchant:</span>
                                                <span className="ml-2 font-medium">{selectedGroup.merchant}</span>
                                            </div>
                                            <div>
                                                <span className="text-gray-600">Transactions:</span>
                                                <span className="ml-2 font-medium">{selectedGroup.count}</span>
                                            </div>
                                            <div>
                                                <span className="text-gray-600">Total:</span>
                                                <span className="ml-2 font-medium">{formatCurrency(selectedGroup.total)}</span>
                                            </div>
                                        </div>
                                    </div>

                                    {selectedGroup.suggestions.length > 0 && (
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                                Suggested Categories
                                            </label>
                                            <div className="flex flex-wrap gap-2 mb-4">
                                                {selectedGroup.suggestions.map((category) => (
                                                    <button
                                                        key={category}
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

                                    <div className="space-y-2">
                                        <button
                                            onClick={handleReviewGroup}
                                            disabled={!selectedCategory || isProcessing}
                                            className={`w-full px-4 py-2 rounded-lg font-medium transition-colors duration-200 ${
                                                selectedCategory && !isProcessing
                                                    ? 'bg-green-600 text-white hover:bg-green-700 shadow-md'
                                                    : 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                            }`}
                                        >
                                            {isProcessing
                                                ? 'Processing...'
                                                : `Categorize ${selectedGroup.count} transaction${selectedGroup.count === 1 ? '' : 's'}`}
                                        </button>
                                        <button
                                            onClick={clearSelection}
                                            className="w-full px-4 py-2 border border-gray-300 rounded-lg font-medium text-gray-700 hover:bg-gray-50 transition-colors duration-200"
                                        >
                                            Clear Selection
                                        </button>
                                    </div>
                                </div>
                            ) : (
                                <div className="text-center text-gray-500 py-8">
                                    <AlertCircle className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                                    <p className="text-sm">Select a merchant to categorize it</p>
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
