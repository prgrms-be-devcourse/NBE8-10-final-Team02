'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import type { Application, CreateApplicationRequest } from '@/types/application';
import { getApplications, createApplication, deleteApplication } from '@/api/application';

const STATUS_LABEL: Record<string, string> = {
  draft: '작성 중',
  ready: '준비 완료',
};

export default function ApplicationsPage() {
  const router = useRouter();

  const [applications, setApplications] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 새로 만들기 폼
  const [showForm, setShowForm] = useState(false);
  const [jobRole, setJobRole] = useState('');
  const [companyName, setCompanyName] = useState('');
  const [applicationTitle, setApplicationTitle] = useState('');
  const [creating, setCreating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // 삭제 확인
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);

  const loadApplications = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await getApplications();
      setApplications(data);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '목록을 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadApplications();
  }, [loadApplications]);

  async function handleCreate() {
    if (!jobRole.trim()) return;
    setCreating(true);
    setActionError(null);
    try {
      const request: CreateApplicationRequest = {
        jobRole: jobRole.trim(),
        ...(companyName.trim() && { companyName: companyName.trim() }),
        ...(applicationTitle.trim() && { applicationTitle: applicationTitle.trim() }),
      };
      const created = await createApplication(request);
      setApplications((prev) => [created, ...prev]);
      setShowForm(false);
      setJobRole('');
      setCompanyName('');
      setApplicationTitle('');
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '생성 중 오류가 발생했습니다.');
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(id: number) {
    setActionError(null);
    try {
      await deleteApplication(id);
      setApplications((prev) => prev.filter((a) => a.id !== id));
    } catch (err) {
      setActionError(err instanceof Error ? err.message : '삭제 중 오류가 발생했습니다.');
    } finally {
      setDeleteTarget(null);
    }
  }

  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <p className="text-sm text-zinc-400">불러오는 중...</p>
      </main>
    );
  }

  if (loadError) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {loadError}
        </div>
        <button onClick={loadApplications} className="mt-4 text-sm underline text-zinc-500">
          다시 시도
        </button>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      {/* 헤더 */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">지원 준비</h1>
          <p className="mt-1 text-sm text-zinc-500">
            지원할 회사와 직무를 등록하고 AI 자소서를 생성하세요.
          </p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="shrink-0 rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white"
        >
          {showForm ? '취소' : '새로 만들기'}
        </button>
      </div>

      {/* 에러 메시지 */}
      {actionError && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {actionError}
        </div>
      )}

      {/* 새로 만들기 폼 */}
      {showForm && (
        <div className="mb-6 rounded border border-zinc-200 px-5 py-4">
          <p className="mb-3 text-sm font-medium">새 지원 준비</p>
          <div className="flex flex-col gap-3">
            <div>
              <label className="block text-xs text-zinc-500 mb-1">
                직무 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                placeholder="예: 백엔드 개발자"
                value={jobRole}
                onChange={(e) => setJobRole(e.target.value)}
                className="w-full rounded border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-500 mb-1">회사명 (선택)</label>
              <input
                type="text"
                placeholder="예: 네이버"
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                className="w-full rounded border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-500 mb-1">제목 (선택)</label>
              <input
                type="text"
                placeholder="예: 2026 상반기 공채"
                value={applicationTitle}
                onChange={(e) => setApplicationTitle(e.target.value)}
                className="w-full rounded border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none"
              />
            </div>
            <button
              onClick={handleCreate}
              disabled={creating || !jobRole.trim()}
              className="mt-1 w-full rounded bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {creating ? '생성 중...' : '생성'}
            </button>
          </div>
        </div>
      )}

      {/* 목록 */}
      {applications.length === 0 ? (
        <div className="rounded border border-zinc-200 px-5 py-10 text-center">
          <p className="text-sm text-zinc-500">등록된 지원 준비가 없습니다.</p>
          <p className="mt-1 text-xs text-zinc-400">
            위의 &quot;새로 만들기&quot; 버튼을 눌러 시작하세요.
          </p>
        </div>
      ) : (
        <ul className="flex flex-col gap-3">
          {applications.map((app) => (
            <li key={app.id} className="rounded border border-zinc-200 px-5 py-4">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => router.push(`/applications/${app.id}`)}
                      className="text-sm font-medium text-zinc-900 truncate hover:underline text-left"
                    >
                      {app.applicationTitle || app.jobRole}
                    </button>
                    <span
                      className={`shrink-0 rounded px-1.5 py-0.5 text-xs font-medium ${
                        app.status === 'ready'
                          ? 'bg-green-50 text-green-700'
                          : 'bg-zinc-100 text-zinc-500'
                      }`}
                    >
                      {STATUS_LABEL[app.status] ?? app.status}
                    </span>
                  </div>
                  <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-zinc-500">
                    {app.companyName && <span>{app.companyName}</span>}
                    <span>{app.jobRole}</span>
                    <span>{new Date(app.createdAt).toLocaleDateString('ko-KR')}</span>
                  </div>
                </div>

                <div className="flex shrink-0 items-center gap-2">
                  {app.status === 'ready' && (
                    <button
                      onClick={() => router.push(`/applications/${app.id}/question-sets/new`)}
                      className="rounded border border-zinc-300 px-3 py-1.5 text-xs font-medium text-zinc-700"
                    >
                      질문 생성
                    </button>
                  )}
                  <button
                    onClick={() => router.push(`/applications/${app.id}/generate`)}
                    className="rounded bg-zinc-900 px-3 py-1.5 text-xs font-medium text-white"
                  >
                    자소서 생성
                  </button>
                  {deleteTarget === app.id ? (
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => handleDelete(app.id)}
                        className="rounded bg-red-600 px-2 py-1 text-xs text-white"
                      >
                        확인
                      </button>
                      <button
                        onClick={() => setDeleteTarget(null)}
                        className="rounded border border-zinc-300 px-2 py-1 text-xs text-zinc-600"
                      >
                        취소
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => setDeleteTarget(app.id)}
                      className="rounded border border-zinc-300 px-2 py-1.5 text-xs text-zinc-500 hover:text-red-600 hover:border-red-300"
                    >
                      삭제
                    </button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* 하단 네비게이션 */}
      <div className="mt-10 flex justify-between text-sm">
        <Link href="/portfolio/documents" className="text-zinc-500 hover:text-zinc-700">
          ← 문서 업로드
        </Link>
      </div>
    </main>
  );
}
