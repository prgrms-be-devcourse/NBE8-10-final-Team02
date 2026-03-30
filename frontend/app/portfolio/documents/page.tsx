'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import type { Document, DocumentType } from '@/types/document';
import {
  getDocuments,
  getDocument,
  uploadDocument,
  deleteDocument,
} from '@/api/document';
import DocumentUploadZone from '@/components/documents/DocumentUploadZone';
import DocumentList from '@/components/documents/DocumentList';
import ConfirmModal from '@/components/documents/ConfirmModal';
import DocumentDetailModal from '@/components/documents/DocumentDetailModal';

interface ConfirmState {
  type: 'delete' | 'overwrite';
  docId?: number;
  file?: File;
  docType?: DocumentType;
}

export default function DocumentsPage() {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmModal, setConfirmModal] = useState<ConfirmState | null>(null);
  const [pendingIds, setPendingIds] = useState<Set<number>>(new Set());
  const [userName, setUserName] = useState('');
  const [selectedDoc, setSelectedDoc] = useState<Document | null>(null);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const docs = await getDocuments();
      setDocuments(docs);
      const pending = new Set(docs.filter((d) => d.extractStatus === 'pending').map((d) => d.id));
      setPendingIds(pending);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : '문서 목록을 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  // 추출 상태 폴링
  useEffect(() => {
    if (pendingIds.size === 0) return;

    const interval = setInterval(async () => {
      for (const id of pendingIds) {
        try {
          const updated = await getDocument(id);
          if (updated.extractStatus !== 'pending') {
            setDocuments((prev) => prev.map((d) => (d.id === id ? updated : d)));
            setPendingIds((prev) => {
              const next = new Set(prev);
              next.delete(id);
              return next;
            });
          }
        } catch {
          // 폴링 실패는 무시하고 다음 주기에 재시도
        }
      }
    }, 3000);

    return () => clearInterval(interval);
  }, [pendingIds]);

  async function handleUpload(file: File, documentType: DocumentType) {
    setActionError(null);

    // 중복 파일 감지
    const existing = documents.find((d) => d.originalFileName === file.name);
    if (existing) {
      setConfirmModal({ type: 'overwrite', docId: existing.id, file, docType: documentType });
      return;
    }

    await performUpload(file, documentType);
  }

  async function performUpload(file: File, documentType: DocumentType) {
    setUploading(true);
    setActionError(null);
    try {
      const doc = await uploadDocument(file, documentType);
      setDocuments((prev) => [...prev, doc]);
      if (doc.extractStatus === 'pending') {
        setPendingIds((prev) => new Set(prev).add(doc.id));
      }
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '업로드에 실패했습니다.');
    } finally {
      setUploading(false);
    }
  }

  function handleDelete(id: number) {
    setActionError(null);
    setConfirmModal({ type: 'delete', docId: id });
  }

  async function performDelete(id: number) {
    setActionError(null);
    try {
      await deleteDocument(id);
      setDocuments((prev) => prev.filter((d) => d.id !== id));
      setPendingIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    } catch (e) {
      setActionError(e instanceof Error ? e.message : '삭제에 실패했습니다.');
    }
  }

  function handleReupload(id: number, file: File, type: DocumentType) {
    setActionError(null);
    setConfirmModal({ type: 'overwrite', docId: id, file, docType: type });
  }

  async function handleConfirm() {
    if (!confirmModal) return;

    if (confirmModal.type === 'delete' && confirmModal.docId != null) {
      await performDelete(confirmModal.docId);
    }

    if (confirmModal.type === 'overwrite' && confirmModal.file && confirmModal.docType) {
      // 기존 문서 삭제 후 새 파일 업로드
      if (confirmModal.docId != null) {
        try {
          await deleteDocument(confirmModal.docId);
          setDocuments((prev) => prev.filter((d) => d.id !== confirmModal.docId));
        } catch (e) {
          setActionError(e instanceof Error ? e.message : '기존 문서 삭제에 실패했습니다.');
          setConfirmModal(null);
          return;
        }
      }
      await performUpload(confirmModal.file, confirmModal.docType);
    }

    setConfirmModal(null);
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="mb-1 text-2xl font-semibold">문서 업로드</h1>
      <p className="mb-8 text-sm text-zinc-500">
        이력서, 수상기록 등 문서를 업로드하면 텍스트를 자동 추출합니다.
        스캔 PDF는 추출에 실패할 수 있습니다.
      </p>

      {actionError && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {actionError}
        </div>
      )}

      {/* PII Warning Banner */}
      <div className="mb-6 rounded border border-yellow-200 bg-yellow-50 px-4 py-3">
        <p className="text-sm text-yellow-800">
          업로드 전 이름, 주민번호, 연락처 등 민감한 개인정보를 미리 제거해 주세요. 업로드 후 자동으로 마스킹되지만 완벽하지 않을 수 있습니다.
        </p>
      </div>

      {/* Name Input for Masking */}
      <div className="mb-6 space-y-2">
        <label className="text-xs font-medium text-zinc-700">
          마스킹할 이름 (선택사항)
        </label>
        <input
          type="text"
          value={userName}
          onChange={(e) => setUserName(e.target.value)}
          placeholder="예: 김철수"
          className="w-full rounded border border-zinc-300 px-3 py-2 text-sm outline-none focus:border-zinc-500 focus:ring-1 focus:ring-zinc-500"
        />
        <p className="text-xs text-zinc-400">
          입력한 이름은 추출된 텍스트에서 [이름]으로 표시됩니다.
        </p>
      </div>

      <DocumentUploadZone
        documentCount={documents.length}
        onUpload={handleUpload}
        disabled={uploading}
      />

      {uploading && (
        <p className="mt-3 text-xs text-zinc-500">업로드 중...</p>
      )}

      <div className="mt-8">
        <h2 className="mb-3 text-sm font-medium text-zinc-700">
          업로드된 문서 ({documents.length}/5)
        </h2>
        {loading ? (
          <p className="py-8 text-center text-sm text-zinc-400">불러오는 중...</p>
        ) : loadError ? (
          <div className="py-8 text-center">
            <p className="text-sm text-red-500">{loadError}</p>
            <button
              onClick={loadDocuments}
              className="mt-2 text-sm text-zinc-600 underline hover:text-zinc-900"
            >
              다시 시도
            </button>
          </div>
        ) : (
          <DocumentList
            documents={documents}
            onDelete={handleDelete}
            onReupload={handleReupload}
            onViewDetails={setSelectedDoc}
          />
        )}
      </div>

      <div className="mt-10 flex justify-between text-sm">
        <Link href="/portfolio" className="text-zinc-500 hover:text-zinc-700">
          ← 포트폴리오 홈
        </Link>
        <Link href="/applications" className="text-zinc-900 font-medium hover:underline">
          지원 준비 →
        </Link>
      </div>

      <ConfirmModal
        open={confirmModal !== null}
        title={confirmModal?.type === 'delete' ? '문서 삭제' : '문서 덮어쓰기'}
        message={
          confirmModal?.type === 'delete'
            ? '이 문서를 삭제하시겠습니까? 삭제 후 복구할 수 없습니다.'
            : '동일한 이름의 문서가 이미 존재합니다. 기존 문서를 삭제하고 새 파일로 교체하시겠습니까?'
        }
        confirmLabel={confirmModal?.type === 'delete' ? '삭제' : '덮어쓰기'}
        confirmVariant={confirmModal?.type === 'delete' ? 'danger' : 'default'}
        onConfirm={handleConfirm}
        onCancel={() => setConfirmModal(null)}
      />

      {selectedDoc && (
        <DocumentDetailModal
          open={true}
          doc={selectedDoc}
          userName={userName}
          onClose={() => setSelectedDoc(null)}
          onSave={(updated) => {
            setDocuments((prev) =>
              prev.map((d) => (d.id === updated.id ? updated : d))
            );
            setSelectedDoc(null);
          }}
        />
      )}
    </main>
  );
}