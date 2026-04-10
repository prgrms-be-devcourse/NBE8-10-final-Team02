'use client';

import Link from 'next/link';
import { useEffect, useState, useCallback, useRef } from 'react';
import { useParams } from 'next/navigation';
import { getApplication, getQuestions, generateAnswers, getGenerateAnswersStatus } from '@/api/application';
import AiGenerationStatusCard from '@/components/AiGenerationStatusCard';
import type { ApplicationQuestion } from '@/types/application';

const TONE_LABEL: Record<string, string> = {
  formal: '격식체',
  casual: '구어체',
  confident: '자신감',
};

const LENGTH_LABEL: Record<string, string> = {
  short: '짧게 (500자 내외)',
  medium: '보통 (900자 내외)',
  long: '길게 (1400자 내외)',
};

const POLL_INTERVAL_MS = 3000;

export default function GeneratePage() {
  const params = useParams();
  const applicationId = Number(params.id);

  const [questions, setQuestions] = useState<ApplicationQuestion[]>([]);
  const [applicationStatus, setApplicationStatus] = useState<'draft' | 'ready' | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 생성 상태
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [generationDone, setGenerationDone] = useState(false);
  const [showLongWaitHint, setShowLongWaitHint] = useState(false);
  const [showRegenerateConfirm, setShowRegenerateConfirm] = useState(false);

  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const generatingStartRef = useRef<number | null>(null);

  // ── 문항 목록 조회 ─────────────────────────────────
  const loadPageData = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [application, data] = await Promise.all([
        getApplication(applicationId),
        getQuestions(applicationId),
      ]);
      setApplicationStatus(application.status);
      setQuestions(data);
    } catch (err) {
      const msg = err instanceof Error ? err.message : '문항을 불러오지 못했습니다.';
      setLoadError(msg);
    } finally {
      setLoading(false);
    }
  }, [applicationId]);

  // ── 폴링 중단 ─────────────────────────────────────
  const stopPolling = useCallback(() => {
    if (pollingRef.current !== null) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
  }, []);

  // ── 폴링 시작 ─────────────────────────────────────
  // PENDING/IN_PROGRESS 동안 3초마다 상태 확인.
  // COMPLETED → 문항 목록 리로드 + 성공 표시
  // FAILED    → 에러 표시
  const startPolling = useCallback(() => {
    if (pollingRef.current !== null) return; // 이미 폴링 중

    pollingRef.current = setInterval(async () => {
      try {
        const job = await getGenerateAnswersStatus(applicationId);

        if (job === null) {
          // TTL 만료: 상태 알 수 없음 → 결과 조회로 판단
          stopPolling();
          setGenerating(false);
          await loadPageData();
          return;
        }

        if (job.status === 'COMPLETED') {
          stopPolling();
          setGenerating(false);
          setGenerationDone(true);
          await loadPageData();
          return;
        }

        if (job.status === 'FAILED') {
          stopPolling();
          setGenerating(false);
          setGenerateError(job.error ?? 'AI 답변 생성 중 오류가 발생했습니다.');
          return;
        }

        // PENDING / IN_PROGRESS: 경과 시간에 따라 힌트 표시
        if (generatingStartRef.current !== null) {
          const elapsed = Date.now() - generatingStartRef.current;
          if (elapsed > 8000) setShowLongWaitHint(true);
        }
      } catch {
        // 일시적 네트워크 오류는 무시하고 다음 폴링에서 재시도
      }
    }, POLL_INTERVAL_MS);
  }, [applicationId, loadPageData, stopPolling]);

  // ── 마운트 시: 진행 중인 작업 복구 ──────────────────
  // 사용자가 탭을 닫고 다시 돌아왔을 때 PENDING/IN_PROGRESS이면 자동으로 폴링 재개
  useEffect(() => {
    let cancelled = false;

    async function init() {
      await loadPageData();
      if (cancelled) return;

      try {
        const job = await getGenerateAnswersStatus(applicationId);
        if (cancelled) return;

        if (job?.status === 'PENDING' || job?.status === 'IN_PROGRESS') {
          generatingStartRef.current = Date.now();
          setGenerating(true);
          startPolling();
        } else if (job?.status === 'FAILED') {
          setGenerateError(job.error ?? 'AI 답변 생성 중 오류가 발생했습니다.');
        }
      } catch {
        // 상태 조회 실패는 무시: 일반 화면으로 표시
      }
    }

    init();
    return () => {
      cancelled = true;
      stopPolling();
    };
  }, [applicationId, loadPageData, startPolling, stopPolling]);

  // ── AI 답변 생성 시작 ─────────────────────────────
  async function handleGenerate() {
    setGenerating(true);
    setGenerateError(null);
    setGenerationDone(false);
    setShowLongWaitHint(false);
    generatingStartRef.current = Date.now();

    try {
      await generateAnswers(applicationId, { useTemplate: true, regenerate: false });
      startPolling();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'AI 답변 생성 요청 중 오류가 발생했습니다.';
      setGenerateError(msg);
      setGenerating(false);
    }
  }

  // ── 렌더링 ────────────────────────────────────────

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <p className="text-sm text-zinc-400">문항 목록을 불러오는 중...</p>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {loadError}
        </div>
        <button onClick={loadPageData} className="mt-4 text-sm underline text-zinc-500">
          다시 시도
        </button>
      </main>
    );
  }

  if (questions.length === 0) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <h1 className="text-xl font-semibold">AI 자기소개서 생성</h1>
        <p className="mt-4 text-sm text-zinc-500">
          등록된 문항이 없습니다. 먼저 자소서 문항을 추가해 주세요.
        </p>
      </main>
    );
  }

  const hasAnyAnswer = questions.some((q) => q.generatedAnswer !== null);

  const generateStatusDetail = showLongWaitHint
    ? '조금 더 걸리고 있습니다. 곧 결과를 보여드립니다.'
    : '보통 10~30초 정도 걸립니다. 창을 닫아도 생성은 계속됩니다.';

  const showSuccessCard =
    !generating && !generateError && (generationDone || (applicationStatus === 'ready' && hasAnyAnswer));

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      {/* 헤더 */}
      <div className="mb-6">
        <div className="mb-3 flex items-center justify-between gap-3">
          <Link href={`/applications/${applicationId}`} className="text-xs text-zinc-400 hover:text-zinc-600">
            ← 지원 준비 상세로
          </Link>
        </div>
        <h1 className="text-xl font-semibold">AI 자기소개서 생성</h1>
        <p className="mt-1 text-sm text-zinc-500">
          이 화면에서만 등록된 {questions.length}개 문항의 답변 생성과 전체 재생성을 진행합니다.
        </p>
      </div>

      {/* 생성 버튼 영역 */}
      <div className="mb-4 flex items-center justify-end gap-3">
        {hasAnyAnswer && (
          <button
            onClick={() => setShowRegenerateConfirm(true)}
            title="재생성"
            className="flex items-center gap-1 text-zinc-400 hover:text-zinc-600 transition-colors"
          >
            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            <span className="text-xs">재생성</span>
          </button>
        )}
        {!hasAnyAnswer && (
          <button
            onClick={handleGenerate}
            disabled={generating}
            className="rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {generating ? 'AI 답변 생성 중...' : 'AI 답변 생성'}
          </button>
        )}
      </div>

      {/* 재생성 확인 다이얼로그 */}
      {showRegenerateConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="w-full max-w-sm rounded-lg bg-white px-6 py-5 shadow-lg">
            <p className="text-sm font-medium text-zinc-900">AI 답변을 재생성하시겠습니까?</p>
            <p className="mt-2 text-xs text-zinc-500">
              연결된 소스와 문항을 변경하면 기존 답변이 모두 교체됩니다.
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                onClick={() => setShowRegenerateConfirm(false)}
                className="rounded px-3 py-1.5 text-sm text-zinc-500 hover:text-zinc-700"
              >
                취소
              </button>
              <button
                onClick={() => {
                  setShowRegenerateConfirm(false);
                  window.location.href = `/applications/${applicationId}?edit=true`;
                }}
                className="rounded bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white"
              >
                수정하러 가기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 문항 목록 */}
      <ul className="flex flex-col gap-4 mb-6">
        {questions.map((q) => (
          <li key={q.id} className="rounded border border-zinc-200 px-4 py-3">
            <div className="flex items-start gap-2">
              <span className="shrink-0 rounded bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600">
                Q{q.questionOrder}
              </span>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-zinc-900">{q.questionText}</p>

                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {q.toneOption && (
                    <span className="rounded bg-blue-50 px-1.5 py-0.5 text-xs text-blue-700">
                      {TONE_LABEL[q.toneOption] ?? q.toneOption}
                    </span>
                  )}
                  {q.lengthOption && (
                    <span className="rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">
                      {LENGTH_LABEL[q.lengthOption] ?? q.lengthOption}
                    </span>
                  )}
                  {q.emphasisPoint && (
                    <span className="rounded bg-yellow-50 px-1.5 py-0.5 text-xs text-yellow-700">
                      {q.emphasisPoint}
                    </span>
                  )}
                </div>

                {/* 생성 중일 때 skeleton */}
                {generating && !q.generatedAnswer && (
                  <div className="mt-3 rounded bg-zinc-100 px-3 py-2 animate-pulse">
                    <div className="h-3 w-1/3 rounded bg-zinc-200 mb-2" />
                    <div className="h-3 w-full rounded bg-zinc-200 mb-1" />
                    <div className="h-3 w-5/6 rounded bg-zinc-200" />
                  </div>
                )}

                {q.generatedAnswer && (
                  <div className="mt-3 rounded bg-zinc-50 px-3 py-2">
                    <p className="text-xs font-medium text-zinc-500 mb-1">AI 생성 답변</p>
                    <p className="text-sm text-zinc-700 whitespace-pre-wrap">{q.generatedAnswer}</p>
                  </div>
                )}
              </div>
            </div>
          </li>
        ))}
      </ul>

      {/* 상태 카드 */}
      {(generating || generateError || showSuccessCard) && (
        <div className="rounded border border-zinc-200 px-4 py-4">
          {generating && (
            <AiGenerationStatusCard
              ariaLabel="AI 답변 생성 진행 상태"
              tone="pending"
              eyebrow="AI 생성 진행 중"
              title="답변 초안을 생성하고 있습니다"
              message="AI가 문항별 답변 초안을 생성하는 중입니다."
              detail={generateStatusDetail}
            />
          )}

          {generateError && !generating && (
            <AiGenerationStatusCard
              ariaLabel="AI 답변 생성 오류 상태"
              tone="error"
              eyebrow="AI 생성 실패"
              title="답변 생성에 실패했습니다"
              message={generateError}
              detail="같은 화면에서 다시 생성할 수 있습니다."
              actions={
                <button
                  onClick={handleGenerate}
                  className="rounded-full bg-red-700 px-4 py-2.5 text-sm font-medium text-white"
                >
                  다시 시도
                </button>
              }
            />
          )}

          {showSuccessCard && (
            <AiGenerationStatusCard
              ariaLabel="AI 답변 생성 완료 상태"
              tone="success"
              eyebrow="AI 생성 완료"
              title="자기소개서 답변이 저장되었습니다"
              message="모든 문항 답변이 준비되었습니다."
              detail="답변을 검토한 뒤 바로 면접 준비로 넘어갈 수 있습니다."
              actions={
                <Link
                  href={`/applications/${applicationId}/question-sets/new`}
                  className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white"
                >
                  면접 준비로 이동
                </Link>
              }
            />
          )}
        </div>
      )}
    </main>
  );
}
