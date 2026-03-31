import Link from 'next/link';
import type { FeedbackTag, InterviewResult } from '@/types/interview';

const STATUS_LABEL: Record<InterviewResult['status'], string> = {
  completed: '결과 준비 중',
  feedback_completed: '피드백 완료',
};

const STATUS_TONE: Record<InterviewResult['status'], string> = {
  completed: 'bg-amber-50 text-amber-700',
  feedback_completed: 'bg-blue-50 text-blue-700',
};

const TAG_CATEGORY_LABEL: Record<FeedbackTag['tagCategory'], string> = {
  content: '내용',
  structure: '구조',
  evidence: '근거',
  communication: '전달',
  technical: '기술',
  other: '기타',
};

function formatDateTime(value: string | null) {
  if (!value) {
    return '기록 없음';
  }

  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

function summarizeTags(result: InterviewResult) {
  const counts = new Map<string, { count: number; category: FeedbackTag['tagCategory'] }>();

  for (const answer of result.answers) {
    for (const tag of answer.tags) {
      const current = counts.get(tag.tagName);
      counts.set(tag.tagName, {
        count: (current?.count ?? 0) + 1,
        category: tag.tagCategory,
      });
    }
  }

  return Array.from(counts.entries())
    .map(([tagName, value]) => ({
      tagName,
      tagCategory: value.category,
      count: value.count,
    }))
    .sort((left, right) => right.count - left.count || left.tagName.localeCompare(right.tagName, 'ko-KR'));
}

interface InterviewResultReportProps {
  result: InterviewResult;
  backHref: string;
  backLabel: string;
  title?: string;
  description?: string;
  refreshLabel?: string;
  onRefresh?: () => void;
  showHeader?: boolean;
}

export default function InterviewResultReport({
  result,
  backHref,
  backLabel,
  title = '면접 결과 리포트',
  description = '세션 종료 후 생성된 총평과 질문별 피드백을 확인합니다. 답변된 꼬리질문도 같은 흐름 안에 함께 표시됩니다.',
  refreshLabel = '다시 보기',
  onRefresh,
  showHeader = true,
}: InterviewResultReportProps) {
  const tagSummary = summarizeTags(result);

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      {showHeader && (
        <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
          <div>
            <Link href={backHref} className="text-xs text-zinc-400 hover:text-zinc-600">
              ← {backLabel}
            </Link>
            <h1 className="mt-2 text-2xl font-semibold text-zinc-900">{title}</h1>
            <p className="mt-2 text-sm text-zinc-500">{description}</p>
          </div>

          {onRefresh && (
            <button
              type="button"
              onClick={onRefresh}
              className="rounded-full border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700"
            >
              {refreshLabel}
            </button>
          )}
        </div>
      )}

      <section className="rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <div className="flex flex-wrap items-center gap-2">
          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_TONE[result.status]}`}>
            {STATUS_LABEL[result.status]}
          </span>
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
            세션 #{result.sessionId}
          </span>
          <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
            질문 세트 #{result.questionSetId}
          </span>
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">총점</p>
            <p className="mt-1 text-3xl font-semibold text-zinc-900">{result.totalScore}</p>
          </div>
          <div className="rounded-xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">검토된 답변 수</p>
            <p className="mt-1 text-3xl font-semibold text-zinc-900">{result.answers.length}</p>
            <p className="mt-2 text-xs leading-5 text-zinc-500">
              답변된 꼬리질문도 이 개수에 함께 포함됩니다.
            </p>
          </div>
          <div className="rounded-xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">시작 시각</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">{formatDateTime(result.startedAt)}</p>
          </div>
          <div className="rounded-xl bg-zinc-50 px-4 py-4">
            <p className="text-xs text-zinc-500">종료 시각</p>
            <p className="mt-1 text-sm font-medium text-zinc-900">{formatDateTime(result.endedAt)}</p>
          </div>
        </div>

        <div className="mt-5 rounded-2xl border border-zinc-200 px-4 py-4">
          <p className="text-xs font-medium text-zinc-500">총평</p>
          <p className="mt-2 text-sm leading-7 text-zinc-800">{result.summaryFeedback}</p>
        </div>
      </section>

      {tagSummary.length > 0 && (
        <section className="mt-6 rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
          <div className="mb-4">
            <p className="text-xs font-medium text-zinc-500">태그 요약</p>
            <h2 className="mt-1 text-lg font-semibold text-zinc-900">자주 나온 개선 포인트</h2>
          </div>

          <div className="flex flex-wrap gap-3">
            {tagSummary.map((tag) => (
              <div key={tag.tagName} className="rounded-full bg-zinc-100 px-3 py-2 text-sm text-zinc-800">
                {tag.tagName} · {tag.count}회 · {TAG_CATEGORY_LABEL[tag.tagCategory]}
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="mt-6 rounded-2xl border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <div className="mb-4">
          <p className="text-xs font-medium text-zinc-500">질문별 상세 피드백</p>
          <h2 className="mt-1 text-lg font-semibold text-zinc-900">질문 단위 리뷰</h2>
          <p className="mt-2 text-sm leading-6 text-zinc-500">
            아래 목록은 서버가 내려준 답변 순서를 그대로 따릅니다. 답변된 꼬리질문도 일반 질문과 같은 흐름 안에 포함됩니다.
          </p>
        </div>

        <div className="space-y-4">
          {result.answers.map((answer, index) => (
            <article key={answer.answerId} className="rounded-2xl border border-zinc-200 px-4 py-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700">
                  순서 {index + 1}
                </span>
                <div className="flex flex-wrap items-center gap-2">
                  {answer.answerText === null && (
                    <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600">
                      건너뜀
                    </span>
                  )}
                  {answer.questionType === 'follow_up' && (
                    <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">
                      꼬리 질문
                    </span>
                  )}
                  <span className="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                    점수 {answer.score}
                  </span>
                </div>
              </div>

              <p className="mt-3 text-sm font-medium leading-6 text-zinc-900">{answer.questionText}</p>

              <div className="mt-4 rounded-xl bg-zinc-50 px-4 py-4">
                <p className="text-xs font-medium text-zinc-500">내 답변</p>
                <p className="mt-2 text-sm leading-6 text-zinc-800">
                  {answer.answerText ?? '건너뛴 질문입니다.'}
                </p>
              </div>

              <div className="mt-4 rounded-xl border border-zinc-200 px-4 py-4">
                <p className="text-xs font-medium text-zinc-500">피드백</p>
                <p className="mt-2 text-sm leading-6 text-zinc-800">{answer.evaluationRationale}</p>

                {answer.tags.length > 0 && (
                  <div className="mt-4 flex flex-wrap gap-2">
                    {answer.tags.map((tag) => (
                      <span
                        key={`${answer.answerId}-${tag.tagId}`}
                        className="rounded-full bg-amber-50 px-2 py-1 text-xs font-medium text-amber-800"
                      >
                        {tag.tagName}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
