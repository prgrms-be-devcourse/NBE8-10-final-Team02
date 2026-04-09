'use client';

import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import PortfolioReadinessWidget from '@/components/PortfolioReadinessWidget';

interface AppShellProps {
  children: ReactNode;
}

function shouldUseAppShell(pathname: string) {
  return pathname !== '/' && !pathname.startsWith('/login') && pathname !== '/portfolio/readiness';
}

export default function AppShell({ children }: AppShellProps) {
  const pathname = usePathname();

  if (!shouldUseAppShell(pathname)) {
    return <>{children}</>;
  }

  return (
    <div className="mx-auto w-full max-w-7xl px-4 lg:grid lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-6 lg:items-start">
      <div className="min-w-0">{children}</div>
      <aside className="mx-auto w-full max-w-2xl px-4 pb-6 lg:mx-0 lg:max-w-none lg:px-0 lg:pb-0">
        <div className="lg:sticky lg:top-16">
          <PortfolioReadinessWidget />
        </div>
      </aside>
    </div>
  );
}
