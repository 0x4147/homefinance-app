export type YearPreset = `year-${number}`;
export type RangePreset = 'ytd' | 'thisMonth' | 'lastMonth' | '3m' | '6m' | '12m' | YearPreset | 'custom';

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

// One preset per full calendar year for the previous 5 years, most recent first
export const yearPresets = (today = new Date()): { id: YearPreset; label: string }[] =>
    Array.from({ length: 5 }, (_, i) => {
        const year = today.getFullYear() - 1 - i;
        return { id: `year-${year}` as YearPreset, label: String(year) };
    });

export const rangeForPreset = (preset: Exclude<RangePreset, 'custom'>, today = new Date()): DateRange => {
    const y = today.getFullYear();
    const m = today.getMonth();
    if (preset.startsWith('year-')) {
        const year = Number(preset.slice(5));
        return { start: toIsoDate(new Date(year, 0, 1)), end: toIsoDate(new Date(year, 11, 31)) };
    }
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
        default:
            return { start: toIsoDate(new Date(y, 0, 1)), end: toIsoDate(today) };
    }
};
