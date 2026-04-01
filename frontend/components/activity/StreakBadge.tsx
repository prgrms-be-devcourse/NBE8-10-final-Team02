'use client';

interface StreakBadgeProps {
  streak: number;
}

export default function StreakBadge({ streak }: StreakBadgeProps) {
  if (streak <= 0) return null;

  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-orange-50 px-2.5 py-1 text-sm font-medium text-orange-600">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 20 20"
        fill="currentColor"
        className="h-4 w-4"
      >
        <path
          fillRule="evenodd"
          d="M13.5 4.938a7 7 0 1 1-9.006 1.737c.28-.042.553.123.694.373.573 1.014 1.637 1.766 2.87 1.766.66 0 1.318-.216 1.878-.644A7.002 7.002 0 0 0 13.5 4.938ZM10 15a3 3 0 0 1-2.905-2.248 3.01 3.01 0 0 0 1.065.248c.702 0 1.327-.357 1.84-.895A5.96 5.96 0 0 0 12.905 10 3 3 0 0 1 10 15Z"
          clipRule="evenodd"
        />
      </svg>
      {streak}일
    </span>
  );
}
