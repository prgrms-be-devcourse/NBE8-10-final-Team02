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
    <div className="mx-auto w-full max-w-7xl px-4 lg:grid lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-6">
      <div className="min-w-0">{children}</div>
      <aside className="pb-6 lg:pb-0">
        <div className="lg:sticky lg:top-6">
          <PortfolioReadinessWidget />
        </div>
      </aside>
    </div>
  );
}
