'use client';

import { BatchAnalysisProvider } from '@/context/BatchAnalysisContext';
import BatchProgressWidget from '@/components/BatchProgressWidget';
import type { ReactNode } from 'react';

export default function GlobalClientProviders({ children }: { children: ReactNode }) {
  return (
    <BatchAnalysisProvider>
      {children}
      <BatchProgressWidget />
    </BatchAnalysisProvider>
  );
}
