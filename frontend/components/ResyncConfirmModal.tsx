'use client';

interface ResyncConfirmModalProps {
  repoFullName: string;
  lastAnalyzedAt: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
  });
}

export default function ResyncConfirmModal({
  repoFullName,
  lastAnalyzedAt,
  onConfirm,
  onCancel,
}: ResyncConfirmModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-sm rounded-2xl border border-zinc-200 bg-white p-6 shadow-xl">
        <h2 className="text-base font-semibold text-zinc-900">분석 업데이트</h2>
        <p className="mt-1 text-sm font-medium text-zinc-700 truncate">{repoFullName}</p>
        {lastAnalyzedAt && (
          <p className="mt-0.5 text-xs text-zinc-400">마지막 분석: {formatDate(lastAnalyzedAt)}</p>
        )}
        <p className="mt-4 text-sm text-zinc-600">
          새로운 커밋 데이터를 가져오고 AI 분석을 다시 실행합니다.
          정말 업데이트하시겠습니까?
        </p>
        <div className="mt-5 flex gap-3">
          <button
            onClick={onConfirm}
            className="flex-1 rounded-full bg-zinc-900 py-2 text-sm font-medium text-white hover:bg-zinc-700 cursor-pointer"
          >
            업데이트
          </button>
          <button
            onClick={onCancel}
            className="flex-1 rounded-full border border-zinc-300 py-2 text-sm text-zinc-600 hover:bg-zinc-50 cursor-pointer"
          >
            취소
          </button>
        </div>
      </div>
    </div>
  );
}
