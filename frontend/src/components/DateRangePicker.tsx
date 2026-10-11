import React from 'react';
import { PRESETS, rangeForPreset } from '../utils/dateRange';
import type { RangePreset, DateRange } from '../utils/dateRange';

const formatDay = (iso: string) => {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
};

interface DateRangePickerProps {
    preset: RangePreset;
    range: DateRange;
    onChange: (preset: RangePreset, range: DateRange) => void;
}

export const DateRangePicker: React.FC<DateRangePickerProps> = ({ preset, range, onChange }) => {
    const invalid = !!range.start && !!range.end && range.start > range.end;
    const inputClass =
        'px-3 py-1.5 text-sm border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500';

    return (
        <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
                {PRESETS.map(p => (
                    <button
                        key={p.id}
                        type="button"
                        onClick={() => onChange(p.id, rangeForPreset(p.id))}
                        className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-colors duration-150 ${
                            preset === p.id
                                ? 'bg-blue-600 border-blue-600 text-white shadow-sm'
                                : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50 hover:border-gray-300'
                        }`}
                    >
                        {p.label}
                    </button>
                ))}
                <button
                    type="button"
                    onClick={() => onChange('custom', range)}
                    className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-colors duration-150 ${
                        preset === 'custom'
                            ? 'bg-blue-600 border-blue-600 text-white shadow-sm'
                            : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50 hover:border-gray-300'
                    }`}
                >
                    Custom
                </button>
            </div>

            {preset === 'custom' ? (
                <div className="flex flex-wrap items-center gap-2">
                    <input
                        type="date"
                        value={range.start}
                        max={range.end || undefined}
                        onChange={e => onChange('custom', { ...range, start: e.target.value })}
                        className={inputClass}
                        aria-label="Start date"
                    />
                    <span className="text-gray-400">→</span>
                    <input
                        type="date"
                        value={range.end}
                        min={range.start || undefined}
                        onChange={e => onChange('custom', { ...range, end: e.target.value })}
                        className={inputClass}
                        aria-label="End date"
                    />
                    {invalid && <span className="text-sm text-red-600">Start must be before end</span>}
                </div>
            ) : (
                range.start && range.end && (
                    <p className="text-sm text-gray-500">
                        {formatDay(range.start)} – {formatDay(range.end)}
                    </p>
                )
            )}
        </div>
    );
};
