import type { Document, DocumentType } from '@/types/document';

const base = () =>
  (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000') + '/api/v1/documents';

// 공통 에러 파싱 — 백엔드 표준 에러 응답에서 메시지를 꺼낸다
async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body?.error?.message ?? `오류가 발생했습니다. (${res.status})`;
  } catch {
    return `오류가 발생했습니다. (${res.status})`;
  }
}

/**
 * POST /documents
 * 문서 업로드. multipart/form-data로 파일과 문서 유형을 전송한다.
 */
export async function uploadDocument(
  file: File,
  documentType: DocumentType,
): Promise<Document> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('documentType', documentType);

  const res = await fetch(base(), {
    method: 'POST',
    credentials: 'include',
    body: formData,
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as Document;
}

/**
 * GET /documents
 * 현재 사용자의 문서 목록 조회.
 */
export async function getDocuments(): Promise<Document[]> {
  const res = await fetch(base(), {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as Document[];
}

/**
 * GET /documents/{documentId}
 * 문서 상세 조회. 추출 상태 폴링에 사용한다.
 */
export async function getDocument(documentId: number): Promise<Document> {
  const res = await fetch(`${base()}/${documentId}`, {
    credentials: 'include',
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as Document;
}

/**
 * DELETE /documents/{documentId}
 * 문서 삭제. 지원서에서 참조 중이면 409를 반환한다.
 */
export async function deleteDocument(documentId: number): Promise<void> {
  const res = await fetch(`${base()}/${documentId}`, {
    method: 'DELETE',
    credentials: 'include',
  });

  if (res.status === 409) {
    throw new Error('이 문서는 지원서에서 참조 중이므로 삭제할 수 없습니다.');
  }

  if (!res.ok) {
    throw new Error(await parseError(res));
  }
}

/**
 * PATCH /documents/{documentId}/extracted-text
 * 추출된 텍스트를 업데이트한다.
 */
export async function updateExtractedText(
  documentId: number,
  extractedText: string,
): Promise<Document> {
  const res = await fetch(`${base()}/${documentId}/extracted-text`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ extractedText }),
  });

  if (!res.ok) {
    throw new Error(await parseError(res));
  }

  const body = await res.json();
  return body.data as Document;
}
