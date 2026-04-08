'use client';

import Link from 'next/link';
import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import { getApplication, getQuestions, generateAnswers } from '@/api/application';
import AiGenerationStatusCard from '@/components/AiGenerationStatusCard';
import type { ApplicationQuestion, GeneratedAnswerItem } from '@/types/application';

// 톤/길이 옵션 한글 라벨
const TONE_LABEL: Record<string, string> = {
  formal: '격식체',
  casual: '구어체',
  confident: '자신감',
};

const LENGTH_LABEL: Record<string, string> = {
  short: '짧게',
  medium: '보통',
  long: '길게',
};

type QuestionSnapshot = Pick<ApplicationQuestion, 'id' | 'generatedAnswer'>;

function hasGeneratedText(value: string | null) {
  return value !== null && value.trim().length > 0;
}

function buildQuestionSnapshot(questions: ApplicationQuestion[]): QuestionSnapshot[] {
  return questions.map((question) => ({
    id: question.id,
    generatedAnswer: question.generatedAnswer,
  }));
}

function countRecoveredGeneratedAnswers(
  snapshot: QuestionSnapshot[],
  nextQuestions: ApplicationQuestion[],
  regenerate: boolean,
) {
  const nextById = new Map(nextQuestions.map((question) => [question.id, question]));
  const targetQuestions = regenerate
    ? snapshot
    : snapshot.filter((question) => question.generatedAnswer === null);

  return targetQuestions.filter((question) => {
    const nextGeneratedAnswer = nextById.get(question.id)?.generatedAnswer ?? null;
    return hasGeneratedText(nextGeneratedAnswer) && nextGeneratedAnswer !== question.generatedAnswer;
  }).length;
}

function canRecoverGenerateSuccess(
  snapshot: QuestionSnapshot[],
  nextQuestions: ApplicationQuestion[],
  regenerate: boolean,
) {
  const nextById = new Map(nextQuestions.map((question) => [question.id, question]));
  const targetQuestions = regenerate
    ? snapshot
    : snapshot.filter((question) => question.generatedAnswer === null);

  if (targetQuestions.length === 0) {
    return false;
  }

  const allTargetsHaveAnswer = targetQuestions.every((question) =>
    hasGeneratedText(nextById.get(question.id)?.generatedAnswer ?? null),
  );

  if (!allTargetsHaveAnswer) {
    return false;
  }

  if (!regenerate) {
    return true;
  }

  return countRecoveredGeneratedAnswers(snapshot, nextQuestions, true) > 0;
}

function mergeGeneratedAnswersIntoQuestions(
  questions: ApplicationQuestion[],
  answers: GeneratedAnswerItem[],
) {
  const answersById = new Map(answers.map((answer) => [answer.questionId, answer]));

  return questions.map((question) => {
    const matched = answersById.get(question.id);
    if (!matched) {
      return question;
    }

    return {
      ...question,
      generatedAnswer: matched.generatedAnswer,
      toneOption: matched.toneOption ?? question.toneOption,
      lengthOption: matched.lengthOption ?? question.lengthOption,
    };
  });
}

function toGeneratedAnswerItems(questions: ApplicationQuestion[]): GeneratedAnswerItem[] {
  return questions.map((question) => ({
    questionId: question.id,
    questionText: question.questionText,
    generatedAnswer: question.generatedAnswer,
    toneOption: question.toneOption,
    lengthOption: question.lengthOption,
  }));
}

export default function GeneratePage() {
  const params = useParams();
  const applicationId = Number(params.id);

  // 문항 목록
  const [questions, setQuestions] = useState<ApplicationQuestion[]>([]);
  const [applicationStatus, setApplicationStatus] = useState<'draft' | 'ready' | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 생성 상태
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [result, setResult] = useState<GeneratedAnswerItem[] | null>(null);
  const [generatedCount, setGeneratedCount] = useState(0);
  const [showLongWaitHint, setShowLongWaitHint] = useState(false);
  const [resultDetailOverride, setResultDetailOverride] = useState<string | null>(null);

  // 옵션
  const [regenerate, setRegenerate] = useState(false);

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

  useEffect(() => {
    loadPageData();
  }, [loadPageData]);

  useEffect(() => {
    if (!generating) {
      setShowLongWaitHint(false);
      return;
    }

    const timer = window.setTimeout(() => {
      setShowLongWaitHint(true);
    }, 8000);

    return () => window.clearTimeout(timer);
  }, [generating]);

  // ── AI 답변 생성 ──────────────────────────────────
  async function handleGenerate() {
    const questionSnapshot = buildQuestionSnapshot(questions);

    setGenerating(true);
    setGenerateError(null);
    setResult(null);
    setResultDetailOverride(null);
    try {
      const res = await generateAnswers(applicationId, {
        useTemplate: true,
        regenerate,
      });
      setResult(res.answers);
      setGeneratedCount(res.generatedCount);
      setQuestions((currentQuestions) => mergeGeneratedAnswersIntoQuestions(currentQuestions, res.answers));

      const [applicationResult, questionsResult] = await Promise.allSettled([
        getApplication(applicationId),
        getQuestions(applicationId),
      ]);

      if (applicationResult.status === 'fulfilled') {
        setApplicationStatus(applicationResult.value.status);
      }

      if (questionsResult.status === 'fulfilled') {
        setQuestions(questionsResult.value);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'AI 답변 생성 중 오류가 발생했습니다.';
      let recheckedQuestions: ApplicationQuestion[] | null = null;

      try {
        recheckedQuestions = await getQuestions(applicationId);
        setQuestions(recheckedQuestions);
      } catch {
        recheckedQuestions = null;
      }

      if (recheckedQuestions && canRecoverGenerateSuccess(questionSnapshot, recheckedQuestions, regenerate)) {
        setResult(toGeneratedAnswerItems(recheckedQuestions));
        setGeneratedCount(countRecoveredGeneratedAnswers(questionSnapshot, recheckedQuestions, regenerate));
        setResultDetailOverride('응답 확인 중 오류가 있었지만 생성 결과는 저장되었습니다.');
        void getApplication(applicationId)
          .then((application) => setApplicationStatus(application.status))
          .catch(() => undefined);
      } else {
        setGenerateError(msg);
      }
    } finally {
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
  const generateButtonLabel = hasAnyAnswer ? 'AI 답변 재생성' : 'AI 답변 생성';
  const generateStatusDetail = showLongWaitHint
    ? '조금 더 걸리고 있습니다. 곧 결과를 보여드립니다.'
    : '보통 10~30초 정도 걸립니다. 중복으로 누를 필요 없습니다.';

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      {/* 헤더 */}
      <div className="mb-6">
        <div className="mb-3 flex items-center justify-between gap-3">
          <Link href={`/applications/${applicationId}`} className="text-xs text-zinc-400 hover:text-zinc-600">
            ← 지원 준비 상세로
          </Link>
          {applicationStatus === 'ready' && (
            <Link
              href={`/applications/${applicationId}/question-sets/new`}
              className="rounded-full border border-zinc-300 px-3 py-1.5 text-xs font-medium text-zinc-700"
            >
              면접 준비로 이동
            </Link>
          )}
        </div>
        <h1 className="text-xl font-semibold">AI 자기소개서 생성</h1>
        <p className="mt-1 text-sm text-zinc-500">
          이 화면에서만 등록된 {questions.length}개 문항의 답변 생성과 전체 재생성을 진행합니다.
        </p>
      </div>

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

                {/* 옵션 태그 */}
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

                {/* 기존 생성 답변 */}
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

      {/* 생성 옵션 + 버튼 */}
      <div className="rounded border border-zinc-200 px-4 py-4">
        {hasAnyAnswer && (
          <label className="flex items-center gap-2 mb-4 cursor-pointer">
            <input
              type="checkbox"
              checked={regenerate}
              onChange={(e) => setRegenerate(e.target.checked)}
              disabled={generating}
              className="h-4 w-4 accent-zinc-800"
            />
            <span className="text-sm text-zinc-700">
              기존 답변 포함 전체 재생성
            </span>
            <span className="text-xs text-zinc-400">
              (체크 해제 시 답변이 없는 문항만 생성)
            </span>
          </label>
        )}

        <button
          onClick={handleGenerate}
          disabled={generating}
          className="w-full rounded bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
        >
          {generating ? `${generateButtonLabel} 중...` : generateButtonLabel}
        </button>

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
          />
        )}

        {result && (
          <AiGenerationStatusCard
            ariaLabel="AI 답변 생성 완료 상태"
            tone="success"
            eyebrow="AI 생성 완료"
            title="자기소개서 답변이 저장되었습니다"
            message={`${generatedCount}개 문항 생성 완료 · 저장됨`}
            detail={
              resultDetailOverride ?? (
                applicationStatus === 'ready'
                  ? '답변을 검토한 뒤 바로 면접 준비로 넘어갈 수 있습니다.'
                  : '답변을 검토하고 필요한 문항을 더 정리해 주세요.'
              )
            }
            actions={
              applicationStatus === 'ready' ? (
                <Link
                  href={`/applications/${applicationId}/question-sets/new`}
                  className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white"
                >
                  면접 준비로 이동
                </Link>
              ) : null
            }
          />
        )}

        {applicationStatus === 'draft' && hasAnyAnswer && (
          <p className="mt-2 text-xs text-zinc-500">
            질문 생성 단계는 연결 소스와 모든 문항 답변이 함께 준비된 뒤 사용할 수 있습니다.
          </p>
        )}
      </div>
    </main>
  );
}
