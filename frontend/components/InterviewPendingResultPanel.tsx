import Link from 'next/link';
import { PENDING_RESULT_PANEL_COPY } from '@/lib/interview-status-ui';

interface InterviewPendingResultPanelProps {
  message: string;
  onRefresh: () => void;
  refreshing?: boolean;
  backHref: string;
  backLabel: string;
}

export default function InterviewPendingResultPanel({
  message,
  onRefresh,
  refreshing = false,
  backHref,
  backLabel,
}: InterviewPendingResultPanelProps) {
  return (
    <section className="rounded-2xl border border-cyan-200 bg-cyan-50 px-5 py-5 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-[0.12em] text-cyan-700">
        {PENDING_RESULT_PANEL_COPY.eyebrow}
      </p>
      <p className="mt-2 text-base font-semibold text-cyan-950">
        {PENDING_RESULT_PANEL_COPY.title}
      </p>
      <p className="mt-2 text-sm leading-6 text-cyan-900">{message}</p>
      <p className="mt-2 text-sm leading-6 text-cyan-800">
        {PENDING_RESULT_PANEL_COPY.description}
      </p>
      <p className="mt-2 text-sm leading-6 text-cyan-800">
        {PENDING_RESULT_PANEL_COPY.detail}
      </p>

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
