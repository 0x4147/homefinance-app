import React, { useState } from 'react';
import { LayoutDashboard, PlusSquare, List, BarChart2, Calendar, Receipt } from 'lucide-react';

// --- Type Definitions for TypeScript ---
// This defines the possible views our application can have.
type View =
  | 'Dashboard'
  | 'AddBulkTransactions'
  | 'ViewTransactions'
  | 'SpendingInsights'
  | 'MonthlyBalanceChecker'
  | 'AddReceipt';

// --- Placeholder Components for Each View ---
// In a larger application, each of these would be in its own .tsx file.

const Dashboard = () => (
    <div className="max-w-4xl mx-auto animate-fade-in">
        <h2 className="text-3xl font-bold text-gray-900 mb-6">Dashboard</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <div className="bg-gray-100 p-6 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                <h3 className="font-semibold text-gray-700">Account Balance</h3>
                <p className="text-3xl font-bold text-gray-900 mt-2">$12,450.78</p>
            </div>
            <div className="bg-gray-100 p-6 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                <h3 className="font-semibold text-gray-700">Monthly Spending</h3>
                <p className="text-3xl font-bold text-gray-900 mt-2">$2,134.50</p>
            </div>
            <div className="bg-gray-100 p-6 rounded-xl shadow-sm hover:shadow-md transition-shadow">
                <h3 className="font-semibold text-gray-700">Upcoming Bills</h3>
                <p className="text-3xl font-bold text-gray-900 mt-2">3</p>
            </div>
        </div>
    </div>
);

const AddBulkTransactions = () => (
    <div className="animate-fade-in">
        <h2 className="text-3xl font-bold text-gray-900">Add Bulk Transactions</h2>
        <p className="mt-2 text-gray-600">Upload a CSV or manually enter multiple transactions at once.</p>
    </div>
);

const ViewTransactions = () => (
    <div className="animate-fade-in">
        <h2 className="text-3xl font-bold text-gray-900">View Transactions</h2>
        <p className="mt-2 text-gray-600">Browse, search, and filter your transaction history.</p>
    </div>
);

const SpendingInsights = () => (
    <div className="animate-fade-in">
        <h2 className="text-3xl font-bold text-gray-900">Spending Insights</h2>
        <p className="mt-2 text-gray-600">Visualize your spending habits with charts and graphs.</p>
    </div>
);

const MonthlyBalanceChecker = () => (
    <div className="animate-fade-in">
        <h2 className="text-3xl font-bold text-gray-900">Monthly Balance Checker</h2>
        <p className="mt-2 text-gray-600">Compare your income and expenses month over month.</p>
    </div>
);

const AddReceipt = () => (
    <div className="animate-fade-in">
        <h2 className="text-3xl font-bold text-gray-900">Add Receipt</h2>
        <p className="mt-2 text-gray-600">Upload a receipt to automatically create a transaction.</p>
    </div>
);

// --- Sidebar Navigation Component ---
interface SidebarProps {
    activeView: View;
    setActiveView: (view: View) => void;
}

const Sidebar: React.FC<SidebarProps> = ({ activeView, setActiveView }) => {
    const menuItems: { id: number; name: View; icon: React.ElementType }[] = [
        { id: 1, name: 'Dashboard', icon: LayoutDashboard },
        { id: 2, name: 'AddBulkTransactions', icon: PlusSquare },
        { id: 3, name: 'ViewTransactions', icon: List },
        { id: 4, name: 'SpendingInsights', icon: BarChart2 },
        { id: 5, name: 'MonthlyBalanceChecker', icon: Calendar },
        { id: 6, name: 'AddReceipt', icon: Receipt },
    ];

    // Function to format the view name for display
    const formatViewName = (name: View) => name.replace(/([A-Z])/g, ' $1').trim();

    return (
        <aside className="w-64 flex-shrink-0 bg-gray-100/95 border-r border-gray-200 flex flex-col transition-all duration-300">
            <div className="h-16 flex items-center px-6 border-b border-gray-200">
                <h1 className="text-xl font-bold text-gray-900">Home Finance</h1>
            </div>
            <nav className="flex-1 px-4 py-4 space-y-1">
                {menuItems.map((item) => {
                    const isActive = activeView === item.name;
                    return (
                        <a
                            key={item.id}
                            href="#"
                            onClick={(e) => {
                                e.preventDefault();
                                setActiveView(item.name);
                            }}
                            className={`flex items-center px-4 py-2.5 text-sm font-medium rounded-lg transition-colors duration-200 ${
                                isActive
                                    ? 'bg-gray-200 text-gray-900 font-semibold'
                                    : 'text-gray-600 hover:bg-gray-200/70 hover:text-gray-800'
                            }`}
                        >
                            <item.icon className="w-5 h-5 mr-3" />
                            {formatViewName(item.name)}
                        </a>
                    );
                })}
            </nav>
        </aside>
    );
};


// --- Main App Component ---
const App: React.FC = () => {
    // State to manage the currently displayed view.
    const [activeView, setActiveView] = useState<View>('Dashboard');

    // This function returns the component that corresponds to the active view.
    const renderActiveView = () => {
        switch (activeView) {
            case 'Dashboard':
                return <Dashboard />;
            case 'AddBulkTransactions':
                return <AddBulkTransactions />;
            case 'ViewTransactions':
                return <ViewTransactions />;
            case 'SpendingInsights':
                return <SpendingInsights />;
            case 'MonthlyBalanceChecker':
                return <MonthlyBalanceChecker />;
            case 'AddReceipt':
                return <AddReceipt />;
            default:
                return <Dashboard />;
        }
    };

    return (
        <div className="flex h-screen bg-white font-sans">
            <style>{`
                @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
                body { font-family: 'Inter', sans-serif; }
                .animate-fade-in { animation: fadeIn 0.5s ease-in-out; }
                @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
            `}</style>
            <Sidebar activeView={activeView} setActiveView={setActiveView} />
            <main className="flex-1 p-6 sm:p-8 md:p-10 overflow-y-auto">
                {renderActiveView()}
            </main>
        </div>
    );
};

export default App;
