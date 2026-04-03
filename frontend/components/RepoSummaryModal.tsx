'use client';

import { useEffect, useState } from 'react';
import { getRepoSummary } from '@/api/github';
import type { RepoSummaryData } from '@/types/github';

interface RepoSummaryModalProps {
  repositoryId: number;
  repoFullName: string;
  onClose: () => void;
}

function SectionHeader({ title }: { title: string }) {
  return (
    <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-400">{title}</h3>
  );
}

export default function RepoSummaryModal({
  repositoryId,
  repoFullName,
  onClose,
}: RepoSummaryModalProps) {
  const [data, setData] = useState<RepoSummaryData | null>(null);
  const [rawJson, setRawJson] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showRaw, setShowRaw] = useState(false);

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const res = await getRepoSummary(repositoryId);
        if (res) {
          setRawJson(res.data);
          try {
            setData(JSON.parse(res.data) as RepoSummaryData);
          } catch {
            setError('분석 데이터를 파싱할 수 없습니다.');
          }
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : '분석 결과를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, [repositoryId]);

  const p = data?.project;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/40 px-4 py-10">
      <div className="w-full max-w-xl rounded-2xl border border-zinc-200 bg-white shadow-xl">

        {/* 헤더 */}
        <div className="flex items-center justify-between border-b border-zinc-100 px-6 py-4">
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-zinc-900 truncate">{p?.projectName ?? repoFullName}</h2>
            <p className="text-xs text-zinc-400 truncate">{repoFullName}</p>
          </div>
          <div className="flex items-center gap-3 shrink-0 ml-4">
            <button
              onClick={() => setShowRaw((v) => !v)}
              title="원본 JSON 보기"
              className="rounded border border-zinc-200 px-2 py-1 font-mono text-xs text-zinc-500 hover:bg-zinc-50 cursor-pointer"
            >
              {'</>'}
            </button>
            <button
              onClick={onClose}
              className="text-zinc-400 hover:text-zinc-600 cursor-pointer text-lg leading-none"
            >
              ✕
            </button>
          </div>
        </div>

        {/* 바디 */}
        <div className="px-6 py-5">
          {loading && <p className="text-sm text-zinc-400">불러오는 중...</p>}
          {error && <p className="text-sm text-red-600">{error}</p>}
          {!loading && !error && !data && (
            <p className="text-sm text-zinc-400">아직 분석 결과가 없습니다.</p>
          )}

          {!loading && !error && p && !showRaw && (
            <div className="flex flex-col gap-6">

              {/* 요약 + 역할 */}
              <section>
                {p.summary && (
                  <p className="text-sm text-zinc-600 leading-relaxed">{p.summary}</p>
                )}
                {p.role && (
                  <p className="mt-1.5 text-xs text-zinc-400">역할: <span className="text-zinc-600">{p.role}</span></p>
                )}
              </section>

              {/* 기술 스택 */}
              {p.stack.length > 0 && (
                <section>
                  <SectionHeader title="기술 스택" />
                  <div className="flex flex-wrap gap-1.5">
                    {p.stack.map((s) => (
                      <span key={s} className="rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-700">
                        {s}
                      </span>
                    ))}
                  </div>
                </section>
              )}

              {/* 핵심 구현 */}
              {p.signals.length > 0 && (
                <section>
                  <SectionHeader title="핵심 구현" />
                  <ul className="flex flex-col gap-1">
                    {p.signals.map((s, i) => (
                      <li key={i} className="flex gap-2 text-sm text-zinc-700">
                        <span className="mt-0.5 text-zinc-300">•</span>
                        <span>{s}</span>
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              {/* 구현 근거 */}
              {p.evidenceBullets.length > 0 && (
                <section>
                  <SectionHeader title="구현 근거" />
                  <ul className="flex flex-col gap-1">
                    {p.evidenceBullets.map((e, i) => (
                      <li key={i} className="flex gap-2 text-sm text-zinc-700">
                        <span className="mt-0.5 text-zinc-300">•</span>
                        <span>{e.fact}</span>
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              {/* 트러블슈팅 */}
              {p.challenges.length > 0 && (
                <section>
                  <SectionHeader title="트러블슈팅" />
                  <div className="flex flex-col gap-3">
                    {p.challenges.map((c, i) => (
                      <div key={i} className="rounded-lg bg-zinc-50 px-4 py-3">
                        <p className="text-sm font-medium text-zinc-800">{c.what}</p>
                        {c.how && <p className="mt-1 text-sm text-zinc-600">{c.how}</p>}
                        {c.learning && (
                          <p className="mt-1 text-xs text-zinc-400">배운 점: {c.learning}</p>
                        )}
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {/* 기술적 의사결정 */}
              {p.techDecisions.length > 0 && (
                <section>
                  <SectionHeader title="기술적 의사결정" />
                  <div className="flex flex-col gap-3">
                    {p.techDecisions.map((t, i) => (
                      <div key={i} className="rounded-lg bg-zinc-50 px-4 py-3">
                        <p className="text-sm font-medium text-zinc-800">{t.decision}</p>
                        {t.reason && <p className="mt-1 text-sm text-zinc-600">{t.reason}</p>}
                        {t.tradeOff && (
                          <p className="mt-1 text-xs text-zinc-400">트레이드오프: {t.tradeOff}</p>
                        )}
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {/* 강점 */}
              {p.strengths.length > 0 && (
                <section>
                  <SectionHeader title="강점" />
                  <ul className="flex flex-col gap-1">
                    {p.strengths.map((s, i) => (
                      <li key={i} className="flex gap-2 text-sm text-zinc-700">
                        <span className="mt-0.5 text-emerald-400">✓</span>
                        <span>{s}</span>
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              {/* 리스크 */}
              {p.risks.length > 0 && (
                <section>
                  <SectionHeader title="리스크" />
                  <ul className="flex flex-col gap-1">
                    {p.risks.map((r, i) => (
                      <li key={i} className="flex gap-2 text-sm text-zinc-700">
                        <span className="mt-0.5 text-amber-400">!</span>
                        <span>{r}</span>
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              {/* 품질 플래그 */}
              {p.qualityFlags.length > 0 && (
                <section className="flex flex-wrap gap-1.5">
                  {p.qualityFlags.map((f) => (
                    <span key={f} className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-600">
                      {f}
                    </span>
                  ))}
                </section>
              )}

            </div>
          )}

          {/* Raw JSON 뷰어 */}
          {showRaw && rawJson && (
            <pre className="overflow-x-auto rounded-xl bg-zinc-950 p-4 text-xs text-green-400 leading-relaxed max-h-[60vh] overflow-y-auto">
              {JSON.stringify(JSON.parse(rawJson), null, 2)}
            </pre>
          )}
        </div>
      </div>
    </div>
  );
}
