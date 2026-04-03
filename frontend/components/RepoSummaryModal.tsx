'use client';

import { useEffect, useState } from 'react';
import { getRepoSummary } from '@/api/github';
import type { RepoSummaryData } from '@/types/github';

interface RepoSummaryModalProps {
  repositoryId: number;
  repoFullName: string;
  onClose: () => void;
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

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/40 px-4 py-10">
      <div className="w-full max-w-lg rounded-2xl border border-zinc-200 bg-white shadow-xl">
        {/* 헤더 */}
        <div className="flex items-center justify-between border-b border-zinc-100 px-6 py-4">
          <div>
            <h2 className="text-base font-semibold text-zinc-900">분석 결과</h2>
            <p className="text-xs text-zinc-500 truncate">{repoFullName}</p>
          </div>
          <div className="flex items-center gap-3">
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

          {!loading && !error && data && !showRaw && (
            <div className="flex flex-col gap-5">
              {/* 기술 스택 */}
              {data.project.stack.length > 0 && (
                <section>
                  <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-400">기술 스택</h3>
                  <div className="flex flex-wrap gap-1.5">
                    {data.project.stack.map((s) => (
                      <span
                        key={s}
                        className="rounded-full bg-indigo-50 px-2.5 py-0.5 text-xs font-medium text-indigo-700"
                      >
                        {s}
                      </span>
                    ))}
                  </div>
                </section>
              )}

              {/* 핵심 구현 */}
              {data.project.signals.length > 0 && (
                <section>
                  <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-400">핵심 구현</h3>
                  <ol className="list-decimal list-inside flex flex-col gap-1">
                    {data.project.signals.map((s, i) => (
                      <li key={i} className="text-sm text-zinc-700 leading-relaxed">{s}</li>
                    ))}
                  </ol>
                </section>
              )}

              {/* 트러블슈팅 */}
              {data.project.challenges.length > 0 && (
                <section>
                  <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-400">트러블슈팅</h3>
                  <ol className="list-decimal list-inside flex flex-col gap-1">
                    {data.project.challenges.map((c, i) => (
                      <li key={i} className="text-sm text-zinc-700 leading-relaxed">{c}</li>
                    ))}
                  </ol>
                </section>
              )}

              {/* 기술적 의사결정 */}
              {data.project.techDecisions.length > 0 && (
                <section>
                  <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-zinc-400">기술적 의사결정</h3>
                  <ol className="list-decimal list-inside flex flex-col gap-1">
                    {data.project.techDecisions.map((t, i) => (
                      <li key={i} className="text-sm text-zinc-700 leading-relaxed">{t}</li>
                    ))}
                  </ol>
                </section>
              )}

              {/* Role / Period */}
              {(data.project.role || data.project.period) && (
                <section className="flex gap-4 text-xs text-zinc-400">
                  {data.project.role && <span>역할: <span className="text-zinc-600">{data.project.role}</span></span>}
                  {data.project.period && <span>기간: <span className="text-zinc-600">{data.project.period}</span></span>}
                </section>
              )}
            </div>
          )}

          {/* Raw JSON 뷰어 */}
          {showRaw && rawJson && (
            <pre className="overflow-x-auto rounded-xl bg-zinc-950 p-4 text-xs text-green-400 leading-relaxed max-h-96 overflow-y-auto">
              {JSON.stringify(JSON.parse(rawJson), null, 2)}
            </pre>
          )}
        </div>
      </div>
    </div>
  );
}
