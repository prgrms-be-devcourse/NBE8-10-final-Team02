'use client';

import { useRef } from 'react';
import type { Document, DocumentType } from '@/types/document';

const TYPE_LABELS: Record<DocumentType, string> = {
  resume: '이력서',
  award: '수상기록',
  certificate: '증빙',
  other: '기타',
};

const STATUS_STYLES: Record<string, { className: string; label: string }> = {
  pending: { className: 'bg-yellow-100 text-yellow-700', label: '추출 대기중' },
  success: { className: 'bg-green-100 text-green-700', label: '추출 완료' },
  failed: { className: 'bg-red-100 text-red-700', label: '추출 실패' },
};

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

interface DocumentCardProps {
  doc: Document;
  onDelete: (id: number) => void;
  onReupload: (id: number, file: File, type: DocumentType) => void;
  onViewDetails?: (doc: Document) => void;
}

export default function DocumentCard({ doc, onDelete, onReupload, onViewDetails }: DocumentCardProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const status = STATUS_STYLES[doc.extractStatus];

  return (
    <li className="rounded border border-zinc-200 px-4 py-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="truncate text-sm font-medium" title={doc.originalFileName}>
              {doc.originalFileName}
            </p>
            <span className="shrink-0 rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-600">
              {TYPE_LABELS[doc.documentType]}
            </span>
            <span className={`shrink-0 rounded px-1.5 py-0.5 text-xs ${status.className}`}>
              {status.label}
            </span>
          </div>
          <p className="mt-1 text-xs text-zinc-400">
            {formatFileSize(doc.fileSizeBytes)} · {formatDate(doc.uploadedAt)}
          </p>
          {doc.extractStatus === 'failed' && (
            <p className="mt-1 text-xs text-red-500">
              텍스트 추출에 실패했습니다. 스캔 PDF이거나 OCR이 필요한 파일일 수 있습니다.
            </p>
          )}
        </div>
        <div className="flex shrink-0 gap-2">
          {doc.extractStatus === 'success' && onViewDetails && (
            <button
              onClick={() => onViewDetails(doc)}
              className="rounded border border-zinc-300 px-3 py-1 text-xs text-zinc-600 hover:bg-zinc-50"
            >
              내용 보기
            </button>
          )}
          <button
            onClick={() => fileInputRef.current?.click()}
            className="rounded border border-zinc-300 px-3 py-1 text-xs text-zinc-600 hover:bg-zinc-50"
          >
            재업로드
          </button>
          <button
            onClick={() => onDelete(doc.id)}
            className="rounded border border-red-200 px-3 py-1 text-xs text-red-600 hover:bg-red-50"
          >
            삭제
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.docx,.md,.txt"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) {
                onReupload(doc.id, file, doc.documentType);
                e.target.value = '';
              }
            }}
          />
        </div>
      </div>
    </li>
  );
}