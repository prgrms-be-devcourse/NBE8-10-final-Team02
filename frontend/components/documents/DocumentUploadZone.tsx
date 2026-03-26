'use client';

import { useState, useCallback, type DragEvent, type ChangeEvent } from 'react';
import type { DocumentType } from '@/types/document';

const ALLOWED_EXTENSIONS = ['.pdf', '.docx', '.md', '.txt'];
const MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
const MAX_FILE_COUNT = 5;

interface DocumentUploadZoneProps {
  documentCount: number;
  onUpload: (file: File, type: DocumentType) => void;
  disabled?: boolean;
}

export default function DocumentUploadZone({
  documentCount,
  onUpload,
  disabled = false,
}: DocumentUploadZoneProps) {
  const [documentType, setDocumentType] = useState<DocumentType>('other');
  const [dragOver, setDragOver] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const atLimit = documentCount >= MAX_FILE_COUNT;

  const validateAndUpload = useCallback(
    (file: File) => {
      setError(null);

      if (atLimit) {
        setError(`최대 ${MAX_FILE_COUNT}개 파일까지 업로드할 수 있습니다.`);
        return;
      }

      const ext = file.name.slice(file.name.lastIndexOf('.')).toLowerCase();
      if (!ALLOWED_EXTENSIONS.includes(ext)) {
        setError('허용되지 않는 파일 형식입니다. PDF, DOCX, MD, TXT만 가능합니다.');
        return;
      }

      if (file.size > MAX_FILE_SIZE) {
        setError('파일 크기는 50MB를 초과할 수 없습니다.');
        return;
      }

      onUpload(file, documentType);
    },
    [atLimit, documentType, onUpload],
  );

  function handleDrop(e: DragEvent<HTMLLabelElement>) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file) validateAndUpload(file);
  }

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) validateAndUpload(file);
    e.target.value = '';
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <label className="text-xs text-zinc-500">문서 유형</label>
        <select
          value={documentType}
          onChange={(e) => setDocumentType(e.target.value as DocumentType)}
          className="rounded border border-zinc-300 px-2 py-1 text-sm"
        >
          <option value="resume">이력서</option>
          <option value="award">수상기록</option>
          <option value="certificate">증빙</option>
          <option value="other">기타</option>
        </select>
      </div>

      <label
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        className={`flex cursor-pointer flex-col items-center gap-2 rounded border-2 border-dashed px-6 py-10 text-center transition-colors ${
          atLimit || disabled
            ? 'cursor-not-allowed border-zinc-200 bg-zinc-50 text-zinc-400'
            : dragOver
              ? 'border-zinc-500 bg-zinc-50'
              : 'border-zinc-300 hover:border-zinc-400'
        }`}
      >
        <span className="text-sm font-medium">
          {atLimit
            ? `최대 ${MAX_FILE_COUNT}개 파일까지 업로드할 수 있습니다.`
            : '파일을 드래그하거나 클릭하여 선택하세요'}
        </span>
        <span className="text-xs text-zinc-400">
          PDF, DOCX, MD, TXT / 파일당 최대 50MB / 최대 5개
        </span>
        <input
          type="file"
          accept=".pdf,.docx,.md,.txt"
          className="hidden"
          disabled={atLimit || disabled}
          onChange={handleFileChange}
        />
      </label>

      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  );
}
