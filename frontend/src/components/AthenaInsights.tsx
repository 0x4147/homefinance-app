import { useEffect, useState } from 'react';
import { Brain, Cpu, ShieldCheck } from 'lucide-react';
import { apiService } from '../services/api';

type State = 'loading' | 'online' | 'offline' | 'paused';

// Shown when no local AI is reachable. Insights never fall back to a cloud API on their own.
const OFFLINE_LINES = [
    "404: Neural net not found. Have you tried turning the GPU off and on again?",
    "Athena is in airplane mode. Wake the local model and she'll crunch your numbers without anything leaving this machine.",
    "Quantum status: superposition of online and offline. Observation collapsed it to offline.",
    "My weights are asleep. Somewhere a 20-billion-parameter model is waiting for a CPU to believe in it.",
    "Connection refused. Even the electrons took a coffee break.",
];

const STYLES = `
@keyframes athena-scan { 0% { transform: translateX(-100%); } 100% { transform: translateX(100%); } }
@keyframes athena-pulse { 0%, 100% { opacity: .45; } 50% { opacity: 1; } }
@keyframes athena-blink { 0%, 49% { opacity: 1; } 50%, 100% { opacity: 0; } }
@media (prefers-reduced-motion: reduce) { .athena-anim { animation: none !important; } }
`;

const clean = (s: string) => s.replace(/\*\*(.+?)\*\*/g, '$1').replace(/^#+\s*/, '').trim();

const parse = (text: string) =>
    text.split('\n').map(l => l.trim()).filter(Boolean).map(l => {
        const bullet = /^([-*•]|\d+[.)])\s+/.test(l);
        return { bullet, text: clean(l.replace(/^([-*•]|\d+[.)])\s+/, '')) };
    });

export const AthenaInsights = () => {
    const [state, setState] = useState<State>('loading');
    const [analysis, setAnalysis] = useState('');
    const [offlineLine] = useState(() => OFFLINE_LINES[Math.floor(Math.random() * OFFLINE_LINES.length)]);

    useEffect(() => {
        let cancelled = false;
        apiService.getAthenaInsights()
            .then(res => {
                if (cancelled) return;
                if (res.status === 'paused') { setState('paused'); return; }
                const ok = res.status !== 'offline' && !!res.analysis;
                setAnalysis(res.analysis || '');
                setState(ok ? 'online' : 'offline');
            })
            .catch(e => {
                console.error('Failed to fetch Athena insights', e);
                if (!cancelled) setState('offline');
            });
        return () => { cancelled = true; };
    }, []);

    const dot = state === 'online' ? 'bg-emerald-400' : state === 'loading' ? 'bg-cyan-400' : state === 'paused' ? 'bg-violet-400' : 'bg-amber-400';
    const label = state === 'online' ? 'LOCAL AI · ONLINE' : state === 'loading' ? 'SCANNING' : state === 'paused' ? 'CALIBRATING' : 'OFFLINE';

    return (
        <section className="relative overflow-hidden rounded-2xl border border-gray-200 bg-gray-100 p-6 text-gray-700 shadow-sm mb-8">
            <style>{STYLES}</style>
            {/* grid + glow backdrop */}
            <div
                className="pointer-events-none absolute inset-0 opacity-[0.08]"
                style={{
                    backgroundImage: 'linear-gradient(#64748b 1px, transparent 1px), linear-gradient(90deg, #64748b 1px, transparent 1px)',
                    backgroundSize: '32px 32px',
                }}
            />
            <div className="pointer-events-none absolute -top-20 -left-10 h-56 w-56 rounded-full bg-cyan-400/15 blur-3xl" />
            <div className="pointer-events-none absolute -bottom-24 right-0 h-56 w-56 rounded-full bg-violet-400/15 blur-3xl" />

            <header className="relative flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                    <div className="grid h-10 w-10 place-items-center rounded-xl bg-white ring-1 ring-cyan-500/40 shadow-[0_0_16px_rgba(34,211,238,0.35)]">
                        <Brain className="h-5 w-5 text-cyan-600" />
                    </div>
                    <div>
                        <h2 className="text-xl font-semibold tracking-tight text-gray-900">Athena</h2>
                        <p className="font-mono text-xs text-cyan-700/80">insights // last 90 days</p>
                    </div>
                </div>
                <span className="inline-flex items-center gap-2 rounded-full bg-white px-3 py-1 font-mono text-xs ring-1 ring-gray-200 text-gray-600">
                    <span className={`h-2 w-2 rounded-full ${dot} athena-anim`} style={{ animation: 'athena-pulse 1.6s ease-in-out infinite' }} />
                    {label}
                </span>
            </header>

            <div className="relative mt-5 min-h-[96px]">
                {state === 'loading' && (
                    <div>
                        <p className="font-mono text-sm text-cyan-700">
                            &gt; analysing transactions<span className="athena-anim" style={{ animation: 'athena-blink 1s steps(1) infinite' }}>_</span>
                        </p>
                        <div className="relative mt-4 h-1 overflow-hidden rounded-full bg-gray-300">
                            <div
                                className="athena-anim absolute inset-y-0 w-1/3 rounded-full bg-gradient-to-r from-transparent via-cyan-400 to-transparent"
                                style={{ animation: 'athena-scan 1.4s linear infinite' }}
                            />
                        </div>
                    </div>
                )}

                {state === 'paused' && (
                    <div>
                        <p className="text-lg leading-relaxed text-gray-800">
                            Athena is in the lab getting her neurons fine-tuned. Neural upgrade in progress, check back soon.
                        </p>
                        <p className="mt-4 inline-block rounded-lg bg-white px-3 py-2 font-mono text-sm text-violet-700 ring-1 ring-violet-300">
                            $ athena --status calibrating
                        </p>
                    </div>
                )}

                {state === 'offline' && (
                    <div>
                        <p className="text-lg leading-relaxed text-gray-800">{offlineLine}</p>
                        <p className="mt-4 inline-block rounded-lg bg-white px-3 py-2 font-mono text-sm text-cyan-700 ring-1 ring-cyan-300">
                            $ ollama serve
                        </p>
                    </div>
                )}

                {state === 'online' && (
                    <ul className="space-y-3">
                        {parse(analysis).map((line, i) => (
                            <li key={i} className="flex gap-3 leading-relaxed">
                                {line.bullet ? (
                                    <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-cyan-400 shadow-[0_0_8px_rgba(34,211,238,0.9)]" />
                                ) : (
                                    <span className="mt-2 h-1.5 w-1.5 shrink-0" />
                                )}
                                <span className={line.bullet ? 'text-gray-700' : 'font-medium text-gray-900'}>{line.text}</span>
                            </li>
                        ))}
                    </ul>
                )}
            </div>

            <footer className="relative mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 border-t border-gray-300 pt-3 font-mono text-xs text-gray-500">
                <span className="inline-flex items-center gap-1.5"><ShieldCheck className="h-3.5 w-3.5 text-emerald-600" /> private: data stays on this machine</span>
                <span className="inline-flex items-center gap-1.5"><Cpu className="h-3.5 w-3.5 text-cyan-600" /> local model via Ollama</span>
            </footer>
        </section>
    );
};
