import { BatchAnalysisProvider } from '@/context/BatchAnalysisContext';
import BatchProgressWidget from '@/components/BatchProgressWidget';

export default function PortfolioLayout({ children }: { children: React.ReactNode }) {
  return (
    <BatchAnalysisProvider>
      {children}
      <BatchProgressWidget />
    </BatchAnalysisProvider>
  );
}
