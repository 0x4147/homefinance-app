export type RangePreset = 'ytd' | 'thisMonth' | 'lastMonth' | '3m' | '6m' | '12m' | 'custom';

export interface DateRange {
    start: string; // YYYY-MM-DD
    end: string;   // YYYY-MM-DD
}

const pad = (n: number) => String(n).padStart(2, '0');

// Local-time formatting; toISOString() would shift the day for timezones behind UTC
export const toIsoDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

export const PRESETS: { id: Exclude<RangePreset, 'custom'>; label: string }[] = [
    { id: 'ytd', label: 'Year to date' },
    { id: 'thisMonth', label: 'This month' },
    { id: 'lastMonth', label: 'Last month' },
    { id: '3m', label: 'Last 3 months' },
    { id: '6m', label: 'Last 6 months' },
    { id: '12m', label: 'Last 12 months' },
];

export const rangeForPreset = (preset: Exclude<RangePreset, 'custom'>, today = new Date()): DateRange => {
    const y = today.getFullYear();
    const m = today.getMonth();
    switch (preset) {
        case 'ytd':
            return { start: toIsoDate(new Date(y, 0, 1)), end: toIsoDate(today) };
        case 'thisMonth':
            return { start: toIsoDate(new Date(y, m, 1)), end: toIsoDate(today) };
        case 'lastMonth':
            return { start: toIsoDate(new Date(y, m - 1, 1)), end: toIsoDate(new Date(y, m, 0)) };
        case '3m':
            return { start: toIsoDate(new Date(y, m - 2, 1)), end: toIsoDate(today) };
        case '6m':
            return { start: toIsoDate(new Date(y, m - 5, 1)), end: toIsoDate(today) };
        case '12m':
            return { start: toIsoDate(new Date(y, m - 11, 1)), end: toIsoDate(today) };
    }
};
