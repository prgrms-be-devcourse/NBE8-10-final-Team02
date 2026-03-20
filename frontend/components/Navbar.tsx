'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { getMe, logout } from '@/api/auth';
import type { User } from '@/types/auth';

export default function Navbar() {
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    getMe().then(setUser);
  }, []);

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
        <Link href="/interview/history">면접 히스토리</Link>

        {user ? (
          <>
            <span className="text-zinc-500">{user.displayName}</span>
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