'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { getMe, logout } from '@/api/auth';
import { getStreak } from '@/api/activity';
import type { User } from '@/types/auth';
import StreakBadge from '@/components/activity/StreakBadge';

export default function Navbar() {
  const pathname = usePathname();
  const [user, setUser] = useState<User | null>(null);
  const [streak, setStreak] = useState(0);

  useEffect(() => {
    Promise.all([getMe(), getStreak()]).then(([u, s]) => {
      setUser(u);
      setStreak(s.currentStreak);
    });
  }, []);

  if (pathname === '/' || pathname.startsWith('/login')) {
    return null;
  }

  async function handleLogout() {
    await logout();
    setUser(null);
    window.location.href = '/login';
  }

  return (
    <nav className="flex items-center justify-between border-b border-zinc-200 px-6 py-3">
      <Link href="/" className="font-semibold">
        AI 면접 연습
      </Link>

      <div className="flex items-center gap-4 text-sm">
        <Link href="/portfolio">포트폴리오</Link>
        <Link href="/applications">지원 준비</Link>
        <Link href="/practice">문제 연습</Link>
        <Link href="/interview/history">면접 히스토리</Link>
        <Link href="/activity">활동</Link>
        <StreakBadge streak={streak} />

        {user ? (
          <>
            <Link href="/portfolio/readiness" className="text-zinc-500 hover:text-zinc-900">{user.displayName}</Link>
            <button onClick={handleLogout} className="underline">
              로그아웃
            </button>
          </>
        ) : (
          <Link href="/login" className="underline">
            로그인
          </Link>
        )}
      </div>
    </nav>
  );
}
