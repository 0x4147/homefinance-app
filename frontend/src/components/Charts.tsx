import { useState } from 'react';

// Palette follows the dataviz skill's reference instance (light mode):
// categorical slots are assigned in fixed order; single-series charts use slot 1 only.
const SERIES = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7'];
const OTHER = '#898781';
const INK = '#0b0b0b';
const INK_2 = '#52514e';
const MUTED = '#898781';

type ChartData = { labels: string[]; values: number[] };

const money = (n: number) =>
    new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD', maximumFractionDigits: 0 }).format(n);

const Card = ({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) => (
    <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
        <div className="mb-5">
            <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
            {subtitle && <p className="text-sm mt-0.5" style={{ color: MUTED }}>{subtitle}</p>}
        </div>
        {children}
    </div>
);

// --- Donut with center total and a linked legend that doubles as the data table ---
export const DonutChartCard = ({ title, data, onSliceClick }: {
    title: string;
    data: ChartData;
    onSliceClick?: (label: string, index: number) => void;
}) => {
    const [active, setActive] = useState<number | null>(null);

    // Keep the top 7 entities in their own slot; fold the rest into "Other".
    const rows = data.labels
        .map((label, i) => ({ label, value: Math.abs(data.values[i] ?? 0), index: i }))
        .filter(r => r.value > 0)
        .sort((a, b) => b.value - a.value);
    const head = rows.slice(0, SERIES.length);
    const tail = rows.slice(SERIES.length);
    const slices = head.map((r, i) => ({ ...r, color: SERIES[i], clickable: true }));
    if (tail.length) {
        slices.push({
            label: `Other (${tail.length})`,
            value: tail.reduce((s, r) => s + r.value, 0),
            index: -1,
            color: OTHER,
            clickable: false,
        });
    }
    const total = slices.reduce((s, r) => s + r.value, 0);

    const R = 80;
    const C = 2 * Math.PI * R;
    const GAP = slices.length > 1 ? 3 : 0;
    let offset = 0;
    const arcs = slices.map(s => {
        const len = (s.value / total) * C;
        const arc = { start: offset, len: Math.max(len - GAP, 0.5) };
        offset += len;
        return arc;
    });

    const shown = active !== null ? slices[active] : null;

    return (
        <Card title={title} subtitle="Click a slice to see its transactions">
            {total === 0 ? (
                <p className="text-sm text-gray-500 py-12 text-center">No spending in this range</p>
            ) : (
                <div className="flex flex-col md:flex-row items-center gap-8">
                    <div className="relative shrink-0" style={{ width: 220, height: 220 }}>
                        <svg viewBox="0 0 220 220" width="220" height="220" role="img" aria-label={title}>
                            <g transform="rotate(-90 110 110)">
                                {slices.map((s, i) => (
                                    <circle
                                        key={s.label}
                                        cx="110" cy="110" r={R}
                                        fill="none"
                                        stroke={s.color}
                                        strokeWidth={active === i ? 30 : 24}
                                        strokeLinecap="butt"
                                        strokeDasharray={`${arcs[i].len} ${C - arcs[i].len}`}
                                        strokeDashoffset={-arcs[i].start}
                                        opacity={active === null || active === i ? 1 : 0.35}
                                        style={{ transition: 'all 150ms ease', cursor: s.clickable && onSliceClick ? 'pointer' : 'default' }}
                                        onMouseEnter={() => setActive(i)}
                                        onMouseLeave={() => setActive(null)}
                                        onClick={() => s.clickable && onSliceClick?.(s.label, s.index)}
                                    />
                                ))}
                            </g>
                        </svg>
                        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none text-center px-10">
                            <span className="text-xs truncate max-w-full" style={{ color: MUTED }}>
                                {shown ? shown.label : 'Total spent'}
                            </span>
                            <span className="text-2xl font-semibold" style={{ color: INK }}>
                                {money(shown ? shown.value : total)}
                            </span>
                            {shown && (
                                <span className="text-xs" style={{ color: INK_2 }}>
                                    {((shown.value / total) * 100).toFixed(1)}%
                                </span>
                            )}
                        </div>
                    </div>

                    <ul className="w-full flex-1 divide-y divide-gray-100">
                        {slices.map((s, i) => (
                            <li
                                key={s.label}
                                className={`flex items-center gap-3 py-2 px-2 rounded-lg transition-colors ${active === i ? 'bg-gray-50' : ''} ${s.clickable && onSliceClick ? 'cursor-pointer' : ''}`}
                                onMouseEnter={() => setActive(i)}
                                onMouseLeave={() => setActive(null)}
                                onClick={() => s.clickable && onSliceClick?.(s.label, s.index)}
                            >
                                <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: s.color }} />
                                <span className="flex-1 truncate text-sm" style={{ color: INK }}>{s.label}</span>
                                <span className="text-sm tabular-nums" style={{ color: INK }}>{money(s.value)}</span>
                                <span className="w-12 text-right text-xs tabular-nums" style={{ color: MUTED }}>
                                    {((s.value / total) * 100).toFixed(1)}%
                                </span>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </Card>
    );
};
