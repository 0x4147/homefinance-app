import React, { useEffect } from 'react';
import { CheckCircle, AlertCircle, X } from 'lucide-react';

export interface ToastMessage {
    id: number;
    type: 'success' | 'error';
    text: string;
}

interface ToastStackProps {
    toasts: ToastMessage[];
    onDismiss: (id: number) => void;
}

const ToastItem: React.FC<{ toast: ToastMessage; onDismiss: (id: number) => void }> = ({ toast, onDismiss }) => {
    useEffect(() => {
        const timer = setTimeout(() => onDismiss(toast.id), toast.type === 'error' ? 6000 : 4000);
        return () => clearTimeout(timer);
    }, [toast.id, toast.type, onDismiss]);

    const isError = toast.type === 'error';
    return (
        <div
            role={isError ? 'alert' : 'status'}
            className={`animate-fade-in flex items-start gap-3 w-80 rounded-xl border bg-white px-4 py-3 shadow-lg ${
                isError ? 'border-red-200' : 'border-green-200'
            }`}
        >
            {isError
                ? <AlertCircle className="w-5 h-5 mt-0.5 shrink-0 text-red-500" />
                : <CheckCircle className="w-5 h-5 mt-0.5 shrink-0 text-green-500" />}
            <p className="flex-1 text-sm text-gray-800">{toast.text}</p>
            <button
                onClick={() => onDismiss(toast.id)}
                className="text-gray-400 hover:text-gray-600"
                aria-label="Dismiss"
            >
                <X className="w-4 h-4" />
            </button>
        </div>
    );
};

export const ToastStack: React.FC<ToastStackProps> = ({ toasts, onDismiss }) => (
    <div className="fixed top-4 right-4 z-50 flex flex-col gap-2">
        {toasts.map((toast) => (
            <ToastItem key={toast.id} toast={toast} onDismiss={onDismiss} />
        ))}
    </div>
);
