'use client';

import type { ActivityEntry } from '@/types/activity';

interface ActivityHeatmapProps {
  entries: ActivityEntry[];
  days: number;
}

const INTENSITY_COLORS = [
  'bg-zinc-100',
  'bg-green-200',
  'bg-green-400',
  'bg-green-600',
  'bg-green-800',
] as const;

const MONTH_LABELS = [
  '1월', '2월', '3월', '4월', '5월', '6월',
  '7월', '8월', '9월', '10월', '11월', '12월',
];

function getIntensity(count: number): number {
  if (count === 0) return 0;
  if (count === 1) return 1;
  if (count === 2) return 2;
  if (count <= 4) return 3;
  return 4;
}

function buildDateGrid(days: number, entryMap: Map<string, number>) {
  const today = new Date();
  const cells: { date: string; count: number; dayOfWeek: number }[] = [];

  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const dateStr = d.toISOString().slice(0, 10);
    cells.push({
      date: dateStr,
      count: entryMap.get(dateStr) ?? 0,
      dayOfWeek: d.getDay(),
    });
  }

  return cells;
}

function getMonthMarkers(cells: { date: string }[]) {
  const markers: { label: string; index: number }[] = [];
  let lastMonth = -1;

  cells.forEach((cell, i) => {
    const month = parseInt(cell.date.slice(5, 7), 10) - 1;
    if (month !== lastMonth) {
      markers.push({ label: MONTH_LABELS[month], index: i });
      lastMonth = month;
    }
  });

  return markers;
}

export default function ActivityHeatmap({ entries, days }: ActivityHeatmapProps) {
  const entryMap = new Map(entries.map((e) => [e.date, e.count]));
  const cells = buildDateGrid(days, entryMap);

  // Grid: 7 rows (Sun-Sat), N columns (weeks)
  const firstDayOfWeek = cells[0]?.dayOfWeek ?? 0;
  const paddedCells = [
    ...Array.from({ length: firstDayOfWeek }, () => null),
    ...cells,
  ];
  const totalWeeks = Math.ceil(paddedCells.length / 7);

  const weeks: (typeof cells[0] | null)[][] = [];
  for (let w = 0; w < totalWeeks; w++) {
    weeks.push(paddedCells.slice(w * 7, w * 7 + 7));
  }

  const monthMarkers = getMonthMarkers(cells);
  // Convert cell index to week column index
  const monthLabels = monthMarkers.map((m) => ({
    label: m.label,
    weekIndex: Math.floor((m.index + firstDayOfWeek) / 7),
  }));

  const totalActiveDays = cells.filter((c) => c.count > 0).length;
  const totalActivities = cells.reduce((sum, c) => sum + c.count, 0);

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-4 text-sm text-zinc-500">
        <span>최근 {days}일 중 {totalActiveDays}일 활동</span>
        <span>총 {totalActivities}회</span>
      </div>

      <div className="overflow-x-auto">
        <div className="inline-block">
          {/* Month labels */}
          <div className="flex text-xs text-zinc-400 mb-1" style={{ paddingLeft: '28px' }}>
            {monthLabels.map((m, i) => (
              <span
                key={i}
                className="absolute"
                style={{
                  position: 'relative',
                  left: `${m.weekIndex * 14}px`,
                  width: 0,
                  whiteSpace: 'nowrap',
                }}
              >
                {m.label}
              </span>
            ))}
          </div>

          <div className="flex gap-0.5">
            {/* Day labels */}
            <div className="flex flex-col gap-0.5 text-xs text-zinc-400 pr-1">
              {['', '월', '', '수', '', '금', ''].map((label, i) => (
                <div key={i} className="h-[12px] w-5 flex items-center justify-end text-[10px]">
                  {label}
                </div>
              ))}
            </div>

            {/* Grid */}
            {weeks.map((week, wi) => (
              <div key={wi} className="flex flex-col gap-0.5">
                {week.map((cell, di) => (
                  <div
                    key={di}
                    className={`h-[12px] w-[12px] rounded-sm ${
                      cell ? INTENSITY_COLORS[getIntensity(cell.count)] : 'bg-transparent'
                    }`}
                    title={cell ? `${cell.date}: ${cell.count}회` : ''}
                  />
                ))}
                {/* Pad week if incomplete */}
                {week.length < 7 &&
                  Array.from({ length: 7 - week.length }, (_, i) => (
                    <div key={`pad-${i}`} className="h-[12px] w-[12px]" />
                  ))}
              </div>
            ))}
          </div>

          {/* Legend */}
          <div className="flex items-center justify-end gap-1 mt-2 text-xs text-zinc-400">
            <span>적음</span>
            {INTENSITY_COLORS.map((color, i) => (
              <div key={i} className={`h-[12px] w-[12px] rounded-sm ${color}`} />
            ))}
            <span>많음</span>
          </div>
        </div>
      </div>
    </div>
  );
}
