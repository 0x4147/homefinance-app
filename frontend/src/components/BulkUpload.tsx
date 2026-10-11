import React, { useRef, useState } from 'react';
import {
    AlertCircle,
    CheckCircle2,
    CreditCard,
    FileSpreadsheet,
    Landmark,
    Loader2,
    Tag,
    UploadCloud,
    Users,
    X,
} from 'lucide-react';
import { apiService } from '../services/api';

// sourceType must match the backend's ALLOWED_SOURCES. Column layouts mirror BatchConfig.csvFileReader.
interface UploadSource {
    label: string;
    sourceType: string;
    description: string;
    icon: React.ElementType;
    header: string;
    columns: { name: string; format: string; required: boolean }[];
    example: string;
    categorization: string[];
}

const SHARED_COLUMNS = [
    { name: 'Date', format: 'M/D/YYYY, e.g. 04/12/2023', required: true },
    { name: 'Description', format: 'Merchant or bill name', required: true },
    { name: 'Amount', format: 'Unsigned, e.g. 142.50', required: true },
    { name: 'Details', format: 'Free-text note', required: false },
    { name: 'Category', format: 'Exact category name', required: false },
];

const sharedCategorization = [
    'No header row. Details and Category may be left off entirely.',
    'If a Category is given it must match an existing category exactly. An unknown name rejects the whole file.',
    'If Category is blank, it is categorized automatically; anything unrecognised goes to the review queue.',
    'Amounts are unsigned. Money in or out comes from the category (income categories such as Rental Bill Income are money in, anything else is money out). A negative amount reverses it, e.g. a refund.',
    'Every row is marked as a shared expense and counts toward the monthly balance once categorized.',
];

const UPLOAD_SOURCES: UploadSource[] = [
    {
        label: 'Amex',
        sourceType: 'amex',
        description: 'Card statement export',
        icon: CreditCard,
        header: 'Header row required (it is skipped)',
        columns: [
            { name: 'Date', format: 'e.g. 12 Apr. 2023 or 12-Apr-23', required: true },
            { name: 'Date Processed', format: 'Ignored', required: false },
            { name: 'Description', format: 'Merchant name', required: true },
            { name: 'Card Member', format: 'Name containing ASANKA or DIVYA', required: true },
            { name: 'Account #', format: 'Ignored', required: false },
            { name: 'Amount', format: 'Charges positive, refunds negative', required: true },
        ],
        example: '12 Apr. 2023,13 Apr. 2023,LOBLAWS #1021,ASANKA S,-1234,86.40',
        categorization: [
            'Categorized automatically from the merchant: exact match, then keyword match, then past corrections.',
            'Anything it cannot place confidently goes to the Categorization Review queue.',
            'The amount sign is flipped on import, so charges are stored as money out.',
            'The person comes from Card Member; a blank value defaults to Divya.',
        ],
    },
    {
        label: 'CIBC',
        sourceType: 'cibc',
        description: 'Bank account export',
        icon: Landmark,
        header: 'No header row',
        columns: [
            { name: 'Date', format: 'YYYY-MM-DD', required: true },
            { name: 'Description', format: 'Merchant name', required: true },
            { name: 'Amount out', format: 'Unsigned; leave blank if money in', required: false },
            { name: 'Amount in', format: 'Unsigned; leave blank if money out', required: false },
            { name: 'Card number', format: 'Used to find the card holder', required: true },
        ],
        example: '2023-04-12,SHELL C12345,64.20,,4500********1234',
        categorization: [
            'Categorized automatically from the merchant, same as Amex. Unplaced rows go to the review queue.',
            'Fill either Amount out or Amount in, not both. Out is stored as negative, in as positive.',
            'The card number is matched to a registered card to set the person. An unknown card still imports, but without a person, so it is left out of the monthly balance.',
        ],
    },
    {
        label: 'Asanka shared bills',
        sourceType: 'asanka-shared',
        description: 'Bills Asanka paid personally',
        icon: Users,
        header: 'No header row',
        columns: SHARED_COLUMNS,
        example: '04/12/2023,Hydro One,142.50,April bill,Utilities',
        categorization: sharedCategorization,
    },
    {
        label: 'Divya shared bills',
        sourceType: 'divya-shared',
        description: 'Bills Divya paid personally',
        icon: Users,
        header: 'No header row',
        columns: SHARED_COLUMNS,
        example: '04/12/2023,Hydro One,142.50,April bill,Utilities',
        categorization: sharedCategorization,
    },
];

type UploadState =
    | { status: 'idle' }
    | { status: 'uploading' }
    | { status: 'success'; message: string; imported?: number; duplicates?: number }
    | { status: 'error'; message: string };

const formatSize = (bytes: number) =>
    bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;

const isCsv = (file: File) => file.name.toLowerCase().endsWith('.csv');

// Backend replies "Imported N transactions from X (M duplicates skipped)"
const parseResult = (text: string) => {
    const match = text.match(/Imported (\d+) transactions?.*\((\d+) duplicates? skipped\)/i);
    return match ? { imported: Number(match[1]), duplicates: Number(match[2]) } : {};
};

// Backend errors arrive as "File upload failed: 500 ... - <body>"; show just the body
const cleanError = (err: unknown) => {
    const text = err instanceof Error ? err.message : String(err);
    const idx = text.indexOf(' - ');
    return idx >= 0 ? text.slice(idx + 3) : text;
};

const BulkUpload = () => {
    const [source, setSource] = useState<UploadSource | null>(null);
    const [file, setFile] = useState<File | null>(null);
    const [fileError, setFileError] = useState('');
    const [dragging, setDragging] = useState(false);
    const [state, setState] = useState<UploadState>({ status: 'idle' });
    const inputRef = useRef<HTMLInputElement>(null);

    const uploading = state.status === 'uploading';

    const acceptFile = (candidate: File | undefined) => {
        if (!candidate) return;
        if (!isCsv(candidate)) {
            setFile(null);
            setFileError('Only .csv files are supported.');
            return;
        }
        setFileError('');
        setFile(candidate);
        setState({ status: 'idle' });
    };

    const clearFile = () => {
        setFile(null);
        setFileError('');
        if (inputRef.current) inputRef.current.value = '';
    };

    const handleUpload = async () => {
        if (!source || !file) return;
        setState({ status: 'uploading' });
        try {
            const message = await apiService.uploadTransactionFile(file, source.sourceType);
            setState({ status: 'success', message, ...parseResult(message) });
            clearFile();
        } catch (err) {
            setState({ status: 'error', message: cleanError(err) });
        }
    };

    return (
        <div className="animate-fade-in max-w-3xl mx-auto">
            <h2 className="text-3xl font-bold text-gray-900 mb-1">Upload Transactions</h2>
            <p className="text-gray-500 mb-8">Import a CSV export. Uploads are all-or-nothing, so one bad row imports nothing.</p>

            {/* Step 1: source */}
            <section className="mb-8">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">1. Choose a source</h3>
                <div role="radiogroup" aria-label="Source" className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {UPLOAD_SOURCES.map((s) => {
                        const Icon = s.icon;
                        const selected = source?.sourceType === s.sourceType;
                        return (
                            <button
                                key={s.sourceType}
                                type="button"
                                role="radio"
                                aria-checked={selected}
                                disabled={uploading}
                                onClick={() => setSource(s)}
                                className={`flex items-center gap-3 text-left p-4 rounded-xl border-2 transition-all duration-200 disabled:opacity-60 ${
                                    selected
                                        ? 'border-blue-600 bg-blue-50 shadow-sm'
                                        : 'border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm'
                                }`}
                            >
                                <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${selected ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600'}`}>
                                    <Icon className="h-5 w-5" />
                                </span>
                                <span className="flex-1 min-w-0">
                                    <span className="block font-semibold text-gray-900">{s.label}</span>
                                    <span className="block text-sm text-gray-500">{s.description}</span>
                                </span>
                                {selected && <CheckCircle2 className="h-5 w-5 text-blue-600 shrink-0" />}
                            </button>
                        );
                    })}
                </div>
            </section>

            {/* File format guide for the chosen source */}
            {source && (
                <section className="mb-8 bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden animate-fade-in">
                    <div className="px-5 py-3 bg-gray-50 border-b border-gray-200 flex items-center gap-2">
                        <FileSpreadsheet className="h-4 w-4 text-gray-500" />
                        <h4 className="text-sm font-semibold text-gray-800">{source.label} file format</h4>
                        <span className="ml-auto text-xs text-gray-500">{source.header}</span>
                    </div>
                    <div className="p-5 space-y-5">
                        <div className="overflow-x-auto">
                            <table className="min-w-full text-sm">
                                <thead>
                                    <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
                                        <th className="pb-2 pr-3 font-medium">#</th>
                                        <th className="pb-2 pr-4 font-medium">Column</th>
                                        <th className="pb-2 font-medium">Expected value</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {source.columns.map((c, i) => (
                                        <tr key={c.name}>
                                            <td className="py-2 pr-3 text-gray-400 tabular-nums">{i + 1}</td>
                                            <td className="py-2 pr-4 font-medium text-gray-900 whitespace-nowrap">
                                                {c.name}
                                                {!c.required && <span className="ml-2 text-xs font-normal text-gray-400">optional</span>}
                                            </td>
                                            <td className="py-2 text-gray-600">{c.format}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        <div>
                            <div className="text-xs font-medium uppercase tracking-wider text-gray-500 mb-1.5">Example row</div>
                            <code className="block overflow-x-auto whitespace-nowrap rounded-lg bg-gray-900 px-3 py-2 text-xs text-green-300">
                                {source.example}
                            </code>
                        </div>

                        <div>
                            <div className="flex items-center gap-1.5 text-sm font-semibold text-gray-800 mb-2">
                                <Tag className="h-4 w-4 text-gray-500" /> How it is categorized
                            </div>
                            <ul className="space-y-1.5 text-sm text-gray-600 list-disc pl-5">
                                {source.categorization.map((line) => (
                                    <li key={line}>{line}</li>
                                ))}
                            </ul>
                        </div>
                    </div>
                </section>
            )}

            {/* Step 2: file */}
            <section className="mb-6">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">2. Add your CSV file</h3>
                <input
                    ref={inputRef}
                    id="file-upload"
                    type="file"
                    accept=".csv,text/csv"
                    className="hidden"
                    disabled={uploading}
                    onChange={(e) => acceptFile(e.target.files?.[0])}
                />
                {!file ? (
                    <label
                        htmlFor="file-upload"
                        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
                        onDragLeave={() => setDragging(false)}
                        onDrop={(e) => {
                            e.preventDefault();
                            setDragging(false);
                            acceptFile(e.dataTransfer.files?.[0]);
                        }}
                        className={`flex flex-col items-center gap-2 rounded-xl border-2 border-dashed p-10 text-center cursor-pointer transition-colors duration-200 ${
                            dragging ? 'border-blue-500 bg-blue-50' : 'border-gray-300 bg-white hover:border-blue-400 hover:bg-gray-50'
                        }`}
                    >
                        <UploadCloud className={`h-10 w-10 ${dragging ? 'text-blue-600' : 'text-gray-400'}`} />
                        <div className="text-gray-700">
                            <span className="font-semibold text-blue-600">Click to browse</span> or drag a file here
                        </div>
                        <div className="text-sm text-gray-500">CSV files only</div>
                    </label>
                ) : (
                    <div className="flex items-center gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
                        <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 text-green-700">
                            <FileSpreadsheet className="h-5 w-5" />
                        </span>
                        <div className="flex-1 min-w-0">
                            <div className="truncate font-medium text-gray-900">{file.name}</div>
                            <div className="text-sm text-gray-500">{formatSize(file.size)}</div>
                        </div>
                        <button
                            type="button"
                            onClick={clearFile}
                            disabled={uploading}
                            aria-label="Remove file"
                            className="rounded-lg p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 disabled:opacity-50"
                        >
                            <X className="h-5 w-5" />
                        </button>
                    </div>
                )}
                {fileError && (
                    <p className="mt-2 flex items-center gap-1.5 text-sm text-red-600">
                        <AlertCircle className="h-4 w-4" /> {fileError}
                    </p>
                )}
            </section>

            {/* Submit */}
            <button
                type="button"
                onClick={handleUpload}
                disabled={!source || !file || uploading}
                className="w-full flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-6 py-3 font-semibold text-white shadow-sm transition-colors duration-200 hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-gray-300 disabled:text-gray-500"
            >
                {uploading ? (
                    <><Loader2 className="h-5 w-5 animate-spin" /> Uploading…</>
                ) : (
                    <><UploadCloud className="h-5 w-5" /> {source ? `Import ${source.label} transactions` : 'Import transactions'}</>
                )}
            </button>

            {/* Progress */}
            {uploading && (
                <div className="mt-4 animate-fade-in" role="status" aria-live="polite">
                    <div className="h-2 w-full overflow-hidden rounded-full bg-blue-100">
                        <div className="h-full w-1/3 rounded-full bg-blue-600 animate-[bulk-upload-slide_1.2s_ease-in-out_infinite]" />
                    </div>
                    <p className="mt-2 text-sm text-gray-500 text-center">
                        Uploading and categorizing transactions. This can take a moment for large files.
                    </p>
                </div>
            )}

            {/* Result */}
            {state.status === 'success' && (
                <div className="mt-6 rounded-xl border border-green-200 bg-green-50 p-5 animate-fade-in" role="status">
                    <div className="flex items-start gap-3">
                        <CheckCircle2 className="h-6 w-6 shrink-0 text-green-600" />
                        <div className="flex-1">
                            <div className="font-semibold text-green-900">Import complete</div>
                            {state.imported !== undefined ? (
                                <div className="mt-3 grid grid-cols-2 gap-3">
                                    <div className="rounded-lg bg-white/70 p-3">
                                        <div className="text-2xl font-bold text-green-700 tabular-nums">{state.imported}</div>
                                        <div className="text-xs text-gray-600">transactions imported</div>
                                    </div>
                                    <div className="rounded-lg bg-white/70 p-3">
                                        <div className="text-2xl font-bold text-gray-700 tabular-nums">{state.duplicates}</div>
                                        <div className="text-xs text-gray-600">duplicates skipped</div>
                                    </div>
                                </div>
                            ) : (
                                <p className="mt-1 text-sm text-green-800">{state.message}</p>
                            )}
                        </div>
                        <button
                            type="button"
                            aria-label="Dismiss"
                            onClick={() => setState({ status: 'idle' })}
                            className="text-green-700/60 hover:text-green-800"
                        >
                            <X className="h-5 w-5" />
                        </button>
                    </div>
                </div>
            )}

            {state.status === 'error' && (
                <div className="mt-6 rounded-xl border border-red-200 bg-red-50 p-5 animate-fade-in" role="alert">
                    <div className="flex items-start gap-3">
                        <AlertCircle className="h-6 w-6 shrink-0 text-red-600" />
                        <div className="flex-1 min-w-0">
                            <div className="font-semibold text-red-900">Import failed</div>
                            <p className="mt-1 text-sm text-red-800 break-words">{state.message}</p>
                            <p className="mt-2 text-xs text-red-700/80">Nothing was imported. Fix the file and try again.</p>
                        </div>
                        <button
                            type="button"
                            aria-label="Dismiss"
                            onClick={() => setState({ status: 'idle' })}
                            className="text-red-700/60 hover:text-red-800"
                        >
                            <X className="h-5 w-5" />
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default BulkUpload;
