'use client';

import type { ReactNode } from 'react';

type AiGenerationStatusTone = 'pending' | 'success' | 'error';

interface AiGenerationStatusCardProps {
  ariaLabel: string;
  eyebrow: string;
  title: string;
  message: string;
  detail?: string;
  tone?: AiGenerationStatusTone;
  actions?: ReactNode;
}

const TONE_STYLES: Record<
  AiGenerationStatusTone,
  {
    container: string;
    eyebrow: string;
    title: string;
    body: string;
    icon: string;
  }
> = {
  pending: {
    container: 'border-cyan-200 bg-cyan-50',
    eyebrow: 'text-cyan-700',
    title: 'text-cyan-950',
    body: 'text-cyan-900',
    icon: 'border-cyan-600 border-t-transparent',
  },
  success: {
    container: 'border-green-200 bg-green-50',
    eyebrow: 'text-green-700',
    title: 'text-green-950',
    body: 'text-green-900',
    icon: 'bg-green-600 text-white',
  },
  error: {
    container: 'border-red-200 bg-red-50',
    eyebrow: 'text-red-700',
    title: 'text-red-950',
    body: 'text-red-900',
    icon: 'bg-red-600 text-white',
  },
};

export default function AiGenerationStatusCard({
  ariaLabel,
  eyebrow,
  title,
  message,
  detail,
  tone = 'pending',
  actions,
}: AiGenerationStatusCardProps) {
  const styles = TONE_STYLES[tone];
  const isPending = tone === 'pending';

  return (
    <section
      role="status"
      aria-label={ariaLabel}
      aria-live={tone === 'error' ? 'assertive' : 'polite'}
      aria-atomic="true"
      aria-busy={isPending}
      className={`mt-3 rounded-2xl border px-4 py-4 shadow-sm ${styles.container}`}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 shrink-0">
          {isPending ? (
            <span
              aria-hidden="true"
              className={`block h-5 w-5 animate-spin rounded-full border-2 ${styles.icon}`}
            />
          ) : (
            <span
              aria-hidden="true"
              className={`flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-semibold ${styles.icon}`}
            >
              {tone === 'success' ? 'OK' : '!'}
            </span>
          )}
        </div>

        <div className="min-w-0 flex-1">
          <p className={`text-xs font-semibold uppercase tracking-[0.12em] ${styles.eyebrow}`}>
            {eyebrow}
          </p>
          <p className={`mt-1 text-sm font-semibold ${styles.title}`}>{title}</p>
          <p className={`mt-2 text-sm leading-6 ${styles.body}`}>{message}</p>
          {detail && <p className={`mt-2 text-sm leading-6 ${styles.body}`}>{detail}</p>}
          {actions && <div className="mt-4 flex flex-wrap gap-3">{actions}</div>}
        </div>
      </div>
    </section>
  );
}
