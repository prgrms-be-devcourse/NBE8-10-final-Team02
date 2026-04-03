'use client';

import type { WeakAreaEntry } from '@/types/activity';

interface Props {
  entries: WeakAreaEntry[];
}

function scoreColor(avg: number): string {
  if (avg >= 80) return 'bg-emerald-400';
  if (avg >= 60) return 'bg-yellow-400';
  return 'bg-red-400';
}

export default function WeakTagsChart({ entries }: Props) {
  if (entries.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-zinc-400">
        분석할 답변 데이터가 없습니다.
      </p>
    );
  }

  return (
    <ul className="space-y-3">
      {entries.map((e) => (
        <li key={e.tagName}>
          <div className="mb-1 flex items-center justify-between text-sm">
            <span className="font-medium text-zinc-800">
              {e.tagName}
              <span className="ml-1.5 text-xs font-normal text-zinc-400">
                ({e.category})
              </span>
            </span>
            <span className="text-xs text-zinc-500">
              {e.avgScore.toFixed(1)}점 · {e.count}회
            </span>
          </div>
          <div className="h-2 w-full overflow-hidden rounded-full bg-zinc-100">
            <div
              className={`h-full rounded-full ${scoreColor(e.avgScore)} transition-all`}
              style={{ width: `${e.avgScore}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}
