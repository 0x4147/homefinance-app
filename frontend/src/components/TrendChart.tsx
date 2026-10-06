import { Area, AreaChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

// Same palette as Charts.tsx: series slot 1 for the single series, recessive grid/axes.
const LINE = '#2a78d6';
const INK = '#0b0b0b';
const INK_2 = '#52514e';
const MUTED = '#898781';
const GRID = '#e1e0d9';

export type TrendPoint = { key: string; label: string; value: number };

const money = (n: number) =>
    new Intl.NumberFormat('en-CA', { style: 'currency', currency: 'CAD', maximumFractionDigits: 0 }).format(n);

const axisMoney = (n: number) => (n >= 1000 ? `$${+(n / 1000).toFixed(1)}k` : `$${n}`);

export const TrendChartCard = ({ title, data, onPointClick }: {
    title: string;
    data: TrendPoint[];
    onPointClick?: (point: TrendPoint) => void;
}) => {
    const total = data.reduce((s, p) => s + p.value, 0);
    const average = data.length ? total / data.length : 0;
    const last = data[data.length - 1];
    const prev = data[data.length - 2];
    const change = last && prev && prev.value > 0 ? ((last.value - prev.value) / prev.value) * 100 : null;

    return (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-8">
            <div className="flex flex-wrap items-start justify-between gap-4 mb-5">
                <div>
                    <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
                    <p className="text-sm mt-0.5" style={{ color: MUTED }}>
                        Average {money(average)} / month · click a point to see its transactions
                    </p>
                </div>
                {last && (
                    <div className="text-right">
                        <div className="text-2xl font-semibold" style={{ color: INK }}>{money(last.value)}</div>
                        <div className="text-xs" style={{ color: INK_2 }}>
                            {last.label}
                            {change !== null && (
                                <span> · {change >= 0 ? '▲' : '▼'} {Math.abs(change).toFixed(0)}% vs {prev.label}</span>
                            )}
                        </div>
                    </div>
                )}
            </div>

            {data.length === 0 ? (
                <p className="text-sm text-gray-500 py-12 text-center">No spending data yet</p>
            ) : (
                <div style={{ width: '100%', height: 280 }}>
                    <ResponsiveContainer>
                        <AreaChart
                            data={data}
                            margin={{ top: 8, right: 12, bottom: 0, left: 0 }}
                            onClick={(state) => {
                                const idx = Number(state?.activeTooltipIndex);
                                if (onPointClick && Number.isFinite(idx) && data[idx]) onPointClick(data[idx]);
                            }}
                            style={{ cursor: onPointClick ? 'pointer' : 'default' }}
                        >
                            <defs>
                                <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                                    <stop offset="0%" stopColor={LINE} stopOpacity={0.25} />
                                    <stop offset="100%" stopColor={LINE} stopOpacity={0} />
                                </linearGradient>
                            </defs>
                            <CartesianGrid vertical={false} stroke={GRID} />
                            <XAxis
                                dataKey="label"
                                tickLine={false}
                                axisLine={{ stroke: '#c3c2b7' }}
                                tick={{ fill: MUTED, fontSize: 12 }}
                                tickMargin={8}
                            />
                            <YAxis
                                tickLine={false}
                                axisLine={false}
                                tick={{ fill: MUTED, fontSize: 12 }}
                                tickFormatter={axisMoney}
                                width={48}
                            />
                            <ReferenceLine y={average} stroke={MUTED} strokeDasharray="4 4" />
                            <Tooltip
                                cursor={{ stroke: MUTED, strokeDasharray: '3 3' }}
                                content={({ active, payload }) => {
                                    if (!active || !payload?.length) return null;
                                    const p = payload[0].payload as TrendPoint;
                                    return (
                                        <div className="px-3 py-2 rounded-lg text-xs shadow-md" style={{ background: INK, color: '#fff' }}>
                                            <div style={{ opacity: 0.7 }}>{p.label}</div>
                                            <div className="text-sm font-semibold">{money(p.value)}</div>
                                        </div>
                                    );
                                }}
                            />
                            <Area
                                type="monotone"
                                dataKey="value"
                                stroke={LINE}
                                strokeWidth={2}
                                fill="url(#trendFill)"
                                dot={{ r: 4, fill: LINE, stroke: '#fcfcfb', strokeWidth: 2 }}
                                activeDot={{ r: 6, fill: LINE, stroke: '#fcfcfb', strokeWidth: 2 }}
                            />
                        </AreaChart>
                    </ResponsiveContainer>
                </div>
            )}
        </div>
    );
};
