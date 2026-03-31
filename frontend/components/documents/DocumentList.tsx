'use client';

import type { Document, DocumentType } from '@/types/document';
import DocumentCard from './DocumentCard';

interface DocumentListProps {
  documents: Document[];
  onDelete: (id: number) => void;
  onReupload: (id: number, file: File, type: DocumentType) => void;
  onViewDetails?: (doc: Document) => void;
}

export default function DocumentList({ documents, onDelete, onReupload, onViewDetails }: DocumentListProps) {
  if (documents.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-zinc-400">
        업로드된 문서가 없습니다. 위에서 파일을 업로드하세요.
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-3">
      {documents.map((doc) => (
        <DocumentCard
          key={doc.id}
          doc={doc}
          onDelete={onDelete}
          onReupload={onReupload}
          onViewDetails={onViewDetails}
        />
      ))}
    </ul>
  );
}