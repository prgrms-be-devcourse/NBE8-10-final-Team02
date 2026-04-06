'use client';

import type { ScoreTrendEntry } from '@/types/activity';

interface Props {
  entries: ScoreTrendEntry[];
}

const WIDTH = 600;
const HEIGHT = 160;
const PAD_X = 32;
const PAD_Y = 16;

export default function ScoreTrendChart({ entries }: Props) {
  if (entries.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-zinc-400">
        완료된 면접 세션이 없습니다.
      </p>
    );
  }

  if (entries.length === 1) {
    return (
      <p className="py-8 text-center text-sm text-zinc-400">
        데이터가 2개 이상이면 추이가 표시됩니다.
      </p>
    );
  }

  const innerW = WIDTH - PAD_X * 2;
  const innerH = HEIGHT - PAD_Y * 2;
  const minScore = Math.min(...entries.map((e) => e.score));
  const maxScore = Math.max(...entries.map((e) => e.score));
  const scoreRange = maxScore - minScore || 1;

  const toX = (i: number) => PAD_X + (i / (entries.length - 1)) * innerW;
  const toY = (score: number) =>
    PAD_Y + innerH - ((score - minScore) / scoreRange) * innerH;

  const polyline = entries
    .map((e, i) => `${toX(i)},${toY(e.score)}`)
    .join(' ');

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      className="w-full overflow-visible"
      aria-label="면접 점수 추이"
    >
      {/* grid lines */}
      {[0, 0.5, 1].map((ratio) => {
        const y = PAD_Y + innerH * (1 - ratio);
        const label = Math.round(minScore + scoreRange * ratio);
        return (
          <g key={ratio}>
            <line
              x1={PAD_X}
              y1={y}
              x2={WIDTH - PAD_X}
              y2={y}
              stroke="#e4e4e7"
              strokeDasharray="4 3"
            />
            <text x={PAD_X - 6} y={y + 4} textAnchor="end" fontSize={10} fill="#a1a1aa">
              {label}
            </text>
          </g>
        );
      })}

      {/* line */}
      <polyline
        points={polyline}
        fill="none"
        stroke="#6366f1"
        strokeWidth={2}
        strokeLinejoin="round"
        strokeLinecap="round"
      />

      {/* dots */}
      {entries.map((e, i) => (
        <circle
          key={e.sessionId}
          cx={toX(i)}
          cy={toY(e.score)}
          r={4}
          fill="#6366f1"
          stroke="white"
          strokeWidth={1.5}
        >
          <title>{`세션 ${e.sessionId}: ${e.score}점`}</title>
        </circle>
      ))}
    </svg>
  );
}
