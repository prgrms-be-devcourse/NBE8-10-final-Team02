import Link from 'next/link';
import { PENDING_RESULT_PANEL_COPY } from '@/lib/interview-status-ui';

interface InterviewPendingResultPanelProps {
  message: string;
  onRefresh: () => void;
  refreshing?: boolean;
  backHref: string;
  backLabel: string;
  autoRefreshActive?: boolean;
  autoRefreshAttempt?: number;
  autoRefreshMaxAttempts?: number;
}

export default function InterviewPendingResultPanel({
  message,
  onRefresh,
  refreshing = false,
  backHref,
  backLabel,
  autoRefreshActive = false,
  autoRefreshAttempt = 0,
  autoRefreshMaxAttempts = 0,
}: InterviewPendingResultPanelProps) {
  const autoRefreshStatus =
    autoRefreshActive && autoRefreshAttempt > 0 && autoRefreshMaxAttempts > 0
      ? `${PENDING_RESULT_PANEL_COPY.autoRefreshLabel} ${autoRefreshAttempt}/${autoRefreshMaxAttempts}`
      : PENDING_RESULT_PANEL_COPY.manualReadyLabel;
  const detail = autoRefreshActive
    ? `${PENDING_RESULT_PANEL_COPY.detail} 자동 재확인 ${autoRefreshAttempt}/${autoRefreshMaxAttempts}를 진행하고 있습니다.`
    : PENDING_RESULT_PANEL_COPY.manualDetail;
  const busy = refreshing || autoRefreshActive;

  return (
    <section className="rounded-2xl border border-cyan-200 bg-cyan-50 px-5 py-5 shadow-sm">
      <div className="flex items-start gap-3">
        <div className="mt-0.5 shrink-0">
          {busy ? (
            <span
              aria-hidden="true"
              className="block h-5 w-5 animate-spin rounded-full border-2 border-cyan-600 border-t-transparent"
            />
          ) : (
            <span
              aria-hidden="true"
              className="flex h-5 w-5 items-center justify-center rounded-full bg-cyan-600 text-[10px] font-semibold text-white"
            >
              OK
            </span>
          )}
        </div>

        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-cyan-700">
            {PENDING_RESULT_PANEL_COPY.eyebrow}
          </p>
          <p className="mt-1 text-sm font-semibold text-cyan-950">
            {PENDING_RESULT_PANEL_COPY.title}
          </p>

          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-white px-2.5 py-1 text-[11px] font-medium text-cyan-800">
              {autoRefreshStatus}
            </span>
          </div>

          <p className="mt-3 text-sm leading-6 text-cyan-900">{message}</p>
          <p className="mt-2 text-sm leading-6 text-cyan-800">
            {PENDING_RESULT_PANEL_COPY.description}
          </p>
          <p className="mt-2 text-sm leading-6 text-cyan-800">{detail}</p>
        </div>
      </div>

      <div className="mt-5 flex flex-wrap gap-3">
        <button
          type="button"
          onClick={onRefresh}
          disabled={refreshing}
          className="rounded-full bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
        >
          {refreshing ? '재확인 중...' : PENDING_RESULT_PANEL_COPY.actionLabel}
        </button>
        <Link
          href={backHref}
          className="rounded-full border border-zinc-300 px-4 py-2.5 text-sm font-medium text-zinc-700"
        >
          {backLabel}
        </Link>
      </div>
    </section>
  );
}
