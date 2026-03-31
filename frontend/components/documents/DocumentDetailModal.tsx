'use client';

import { useState } from 'react';
import type { Document, DocumentType } from '@/types/document';
import { updateExtractedText } from '@/api/document';

const TYPE_LABELS: Record<DocumentType, string> = {
  resume: '이력서',
  award: '수상기록',
  certificate: '증빙',
  other: '기타',
};

interface DocumentDetailModalProps {
  open: boolean;
  doc: Document;
  userName?: string;
  onClose: () => void;
  onSave?: (updatedDoc: Document) => void;
}

export default function DocumentDetailModal({
  open,
  doc,
  userName,
  onClose,
  onSave,
}: DocumentDetailModalProps) {
  const [text, setText] = useState(() => {
    let content = doc.extractedText || '';
    // Apply name masking if name is provided
    if (userName && userName.trim()) {
      const nameRegex = new RegExp(userName, 'gi');
      content = content.replace(nameRegex, '[이름]');
    }
    return content;
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) return null;

  const handleSave = async () => {
    if (!text.trim()) {
      setError('내용을 입력해주세요.');
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const updated = await updateExtractedText(doc.id, text);
      onSave?.(updated);
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="flex h-[80vh] w-full max-w-2xl flex-col rounded bg-white shadow-lg">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-zinc-200 px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex-1">
              <p className="font-medium">{doc.originalFileName}</p>
              <span className="inline-block rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-600">
                {TYPE_LABELS[doc.documentType]}
              </span>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-600"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        {/* Info Banner */}
        <div className="border-b border-yellow-200 bg-yellow-50 px-6 py-3">
          <p className="text-sm text-yellow-800">
            추출이 완료됐습니다! 마음에 안 드는 부분은 직접 수정하세요.
          </p>
        </div>

        {/* Editor Area */}
        <div className="flex-1 overflow-hidden px-6 py-4">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="h-full w-full resize-none rounded border border-zinc-300 p-3 font-mono text-sm outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500"
            placeholder="여기에 텍스트를 입력하세요"
          />
        </div>

        {/* Character Count & Error */}
        <div className="border-t border-zinc-200 px-6 py-3">
          {error && (
            <p className="mb-2 text-xs text-red-500">{error}</p>
          )}
          <p className="text-xs text-zinc-400">
            {text.length.toLocaleString()} 글자
          </p>
        </div>

        {/* Footer Buttons */}
        <div className="flex justify-end gap-3 border-t border-zinc-200 px-6 py-4">
          <button
            onClick={onClose}
            className="rounded border border-zinc-300 px-4 py-2 text-sm font-medium text-zinc-700 hover:bg-zinc-50"
          >
            닫기
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className="rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800 disabled:opacity-50"
          >
            {saving ? '저장 중...' : '저장'}
          </button>
        </div>
      </div>
    </div>
  );
}
