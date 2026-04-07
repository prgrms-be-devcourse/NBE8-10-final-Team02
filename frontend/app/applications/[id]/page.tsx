'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import type { Application, ApplicationQuestion, QuestionItem } from '@/types/application';
import type { Document } from '@/types/document';
import type { GithubRepository } from '@/types/github';
import {
  getApplication,
  getQuestions,
  saveSources,
  saveQuestions,
} from '@/api/application';
import { getDocuments } from '@/api/document';
import { getRepositories } from '@/api/github';

const TONE_OPTIONS = [
  { value: 'formal', label: '격식체' },
  { value: 'balanced', label: '균형' },
  { value: 'casual', label: '구어체' },
];

const LENGTH_OPTIONS = [
  { value: 'short', label: '짧게' },
  { value: 'medium', label: '보통' },
  { value: 'long', label: '길게' },
];

export default function ApplicationDetailPage() {
  const params = useParams();
  const router = useRouter();
  const applicationId = Number(params.id);

  // 기본 정보
  const [application, setApplication] = useState<Application | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 소스 선택
  const [documents, setDocuments] = useState<Document[]>([]);
  const [repos, setRepos] = useState<GithubRepository[]>([]);
  const [selectedDocIds, setSelectedDocIds] = useState<Set<number>>(new Set());
  const [selectedRepoIds, setSelectedRepoIds] = useState<Set<number>>(new Set());
  const [savingSource, setSavingSource] = useState(false);
  const [sourceMessage, setSourceMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // 문항
  const [questions, setQuestions] = useState<ApplicationQuestion[]>([]);
  const [editQuestions, setEditQuestions] = useState<QuestionItem[]>([]);
  const [savingQuestions, setSavingQuestions] = useState(false);
  const [questionMessage, setQuestionMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // ── 데이터 로딩 ──────────────────────────────
  const loadAll = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [app, docs, repoResult, qs] = await Promise.all([
        getApplication(applicationId),
        getDocuments(),
        getRepositories({ selected: true, size: 1000 }),
        getQuestions(applicationId),
      ]);

      setApplication(app);
      setDocuments(docs.filter((d) => d.extractStatus === 'success'));
      setRepos(repoResult.data.filter((r) => r.isSelected));
      setQuestions(qs);

      // 기존 문항 → 편집용으로 변환
      if (qs.length > 0) {
        setEditQuestions(
          qs.map((q) => ({
            questionOrder: q.questionOrder,
            questionText: q.questionText,
            toneOption: q.toneOption,
            lengthOption: q.lengthOption,
            emphasisPoint: q.emphasisPoint,
          })),
        );
      } else {
        setEditQuestions([{ questionOrder: 1, questionText: '', toneOption: null, lengthOption: null, emphasisPoint: null }]);
      }
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '데이터를 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  }, [applicationId]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  // ── 소스 저장 ──────────────────────────────
  async function handleSaveSources() {
    setSavingSource(true);
    setSourceMessage(null);
    try {
      const result = await saveSources(applicationId, {
        repositoryIds: Array.from(selectedRepoIds),
        documentIds: Array.from(selectedDocIds),
      });
      setSourceMessage({ type: 'success', text: `소스 ${result.sourceCount}개가 연결되었습니다.` });
    } catch (err) {
      setSourceMessage({ type: 'error', text: err instanceof Error ? err.message : '소스 저장 실패' });
    } finally {
      setSavingSource(false);
    }
  }

  // ── 문항 편집 헬퍼 ──────────────────────────
  function updateQuestion(index: number, field: keyof QuestionItem, value: string | null) {
    setEditQuestions((prev) =>
      prev.map((q, i) => (i === index ? { ...q, [field]: value } : q)),
    );
  }

  function addQuestion() {
    setEditQuestions((prev) => [
      ...prev,
      {
        questionOrder: prev.length + 1,
        questionText: '',
        toneOption: null,
        lengthOption: null,
        emphasisPoint: null,
      },
    ]);
  }

  function removeQuestion(index: number) {
    setEditQuestions((prev) =>
      prev
        .filter((_, i) => i !== index)
        .map((q, i) => ({ ...q, questionOrder: i + 1 })),
    );
  }

  // ── 문항 저장 ──────────────────────────────
  async function handleSaveQuestions() {
    const valid = editQuestions.every((q) => q.questionText.trim());
    if (!valid) {
      setQuestionMessage({ type: 'error', text: '모든 문항에 질문 텍스트를 입력해주세요.' });
      return;
    }

    setSavingQuestions(true);
    setQuestionMessage(null);
    try {
      const saved = await saveQuestions(applicationId, { questions: editQuestions });
      setQuestions(saved);
      setQuestionMessage({ type: 'success', text: `${saved.length}개 문항이 저장되었습니다.` });
    } catch (err) {
      setQuestionMessage({ type: 'error', text: err instanceof Error ? err.message : '문항 저장 실패' });
    } finally {
      setSavingQuestions(false);
    }
  }

  // ── 체크박스 토글 ──────────────────────────
  function toggleDoc(id: number) {
    setSelectedDocIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleRepo(id: number) {
    setSelectedRepoIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  // ── 렌더링 ──────────────────────────────────
  if (loading) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <p className="text-sm text-zinc-400">불러오는 중...</p>
      </main>
    );
  }

  if (loadError || !application) {
    return (
      <main className="mx-auto max-w-2xl px-4 py-12">
        <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {loadError ?? '지원 준비를 찾을 수 없습니다.'}
        </div>
        <Link href="/applications" className="mt-4 inline-block text-sm underline text-zinc-500">
          목록으로
        </Link>
      </main>
    );
  }

  const hasQuestions = questions.length > 0;
  const hasSources = selectedDocIds.size > 0 || selectedRepoIds.size > 0;

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      {/* 헤더 */}
      <div className="mb-8">
        <Link href="/applications" className="text-xs text-zinc-400 hover:text-zinc-600">
          ← 지원 준비 목록
        </Link>
        <h1 className="mt-2 text-xl font-semibold">
          {application.applicationTitle || application.jobRole}
        </h1>
        <div className="mt-1 flex flex-wrap gap-x-3 text-sm text-zinc-500">
          {application.companyName && <span>{application.companyName}</span>}
          <span>{application.jobRole}</span>
        </div>
        <p className="mt-3 text-sm text-zinc-500">
          이 화면에서 소스를 연결하고 문항을 정리한 뒤, 자소서 작성 화면으로 이동합니다.
        </p>
      </div>

      {/* ── STEP 1: 소스 연결 ──────────────── */}
      <section className="mb-8">
        <div className="mb-3 flex items-center gap-2">
          <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600">Step 1</span>
          <h2 className="text-sm font-semibold">소스 연결</h2>
        </div>
        <p className="mb-3 text-xs text-zinc-500">
          AI가 참고할 문서와 레포지토리를 선택하세요.
        </p>

        {/* 문서 목록 */}
        {documents.length > 0 && (
          <div className="mb-3">
            <p className="mb-1.5 text-xs font-medium text-zinc-600">문서</p>
            <div className="flex flex-col gap-1.5">
              {documents.map((doc) => (
                <label key={doc.id} className="flex items-center gap-2 cursor-pointer rounded border border-zinc-200 px-3 py-2">
                  <input
                    type="checkbox"
                    checked={selectedDocIds.has(doc.id)}
                    onChange={() => toggleDoc(doc.id)}
                    className="h-4 w-4 accent-zinc-800"
                  />
                  <span className="text-sm text-zinc-700 truncate">{doc.originalFileName}</span>
                  <span className="ml-auto shrink-0 text-xs text-zinc-400">{doc.documentType}</span>
                </label>
              ))}
            </div>
          </div>
        )}

        {/* 레포 목록 */}
        {repos.length > 0 && (
          <div className="mb-3">
            <p className="mb-1.5 text-xs font-medium text-zinc-600">레포지토리</p>
            <div className="flex flex-col gap-1.5">
              {repos.map((repo) => (
                <label key={repo.id} className="flex items-center gap-2 cursor-pointer rounded border border-zinc-200 px-3 py-2">
                  <input
                    type="checkbox"
                    checked={selectedRepoIds.has(repo.id)}
                    onChange={() => toggleRepo(repo.id)}
                    className="h-4 w-4 accent-zinc-800"
                  />
                  <span className="text-sm text-zinc-700 truncate">{repo.fullName}</span>
                  {repo.language && (
                    <span className="ml-auto shrink-0 text-xs text-zinc-400">{repo.language}</span>
                  )}
                </label>
              ))}
            </div>
          </div>
        )}

        {documents.length === 0 && repos.length === 0 && (
          <p className="text-sm text-zinc-400">
            연결할 소스가 없습니다.{' '}
            <Link href="/portfolio" className="underline">포트폴리오</Link>에서 문서를 업로드하거나 레포를 선택해주세요.
          </p>
        )}

        <button
          onClick={handleSaveSources}
          disabled={savingSource || !hasSources}
          className="mt-2 w-full rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {savingSource ? '저장 중...' : '소스 저장'}
        </button>

        {sourceMessage && (
          <p className={`mt-2 text-sm ${sourceMessage.type === 'success' ? 'text-green-700' : 'text-red-700'}`}>
            {sourceMessage.text}
          </p>
        )}
      </section>

      {/* ── STEP 2: 문항 등록 ──────────────── */}
      <section className="mb-8">
        <div className="mb-3 flex items-center gap-2">
          <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600">Step 2</span>
          <h2 className="text-sm font-semibold">자소서 문항 등록</h2>
        </div>
        <p className="mb-3 text-xs text-zinc-500">
          자소서 문항과 옵션(톤, 길이, 강조점)을 설정하세요.
        </p>

        <div className="flex flex-col gap-4">
          {editQuestions.map((q, idx) => (
            <div key={idx} className="rounded border border-zinc-200 px-4 py-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-zinc-500">문항 {q.questionOrder}</span>
                {editQuestions.length > 1 && (
                  <button
                    onClick={() => removeQuestion(idx)}
                    className="text-xs text-zinc-400 hover:text-red-500"
                  >
                    삭제
                  </button>
                )}
              </div>

              {/* 질문 텍스트 */}
              <textarea
                placeholder="자소서 문항을 입력하세요"
                value={q.questionText}
                onChange={(e) => updateQuestion(idx, 'questionText', e.target.value)}
                rows={2}
                className="w-full rounded border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none resize-none"
              />

              {/* 옵션 행 */}
              <div className="mt-2 flex flex-wrap gap-3">
                <div>
                  <label className="block text-xs text-zinc-400 mb-0.5">톤</label>
                  <select
                    value={q.toneOption ?? ''}
                    onChange={(e) => updateQuestion(idx, 'toneOption', e.target.value || null)}
                    className="rounded border border-zinc-300 px-2 py-1 text-xs"
                  >
                    <option value="">선택 안 함</option>
                    {TONE_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>{opt.label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-zinc-400 mb-0.5">길이</label>
                  <select
                    value={q.lengthOption ?? ''}
                    onChange={(e) => updateQuestion(idx, 'lengthOption', e.target.value || null)}
                    className="rounded border border-zinc-300 px-2 py-1 text-xs"
                  >
                    <option value="">선택 안 함</option>
                    {LENGTH_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>{opt.label}</option>
                    ))}
                  </select>
                </div>
                <div className="flex-1 min-w-[120px]">
                  <label className="block text-xs text-zinc-400 mb-0.5">강조점</label>
                  <input
                    type="text"
                    placeholder="예: 팀워크, 리더십"
                    value={q.emphasisPoint ?? ''}
                    onChange={(e) => updateQuestion(idx, 'emphasisPoint', e.target.value || null)}
                    className="w-full rounded border border-zinc-300 px-2 py-1 text-xs focus:border-zinc-500 focus:outline-none"
                  />
                </div>
              </div>
            </div>
          ))}
        </div>

        {editQuestions.length < 10 && (
          <button
            onClick={addQuestion}
            className="mt-3 w-full rounded border border-dashed border-zinc-300 py-2 text-sm text-zinc-500 hover:border-zinc-400 hover:text-zinc-700"
          >
            + 문항 추가
          </button>
        )}

        <button
          onClick={handleSaveQuestions}
          disabled={savingQuestions}
          className="mt-3 w-full rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {savingQuestions ? '저장 중...' : '문항 저장'}
        </button>

        {questionMessage && (
          <p className={`mt-2 text-sm ${questionMessage.type === 'success' ? 'text-green-700' : 'text-red-700'}`}>
            {questionMessage.text}
          </p>
        )}
      </section>

      {/* ── STEP 3: 자소서 생성 이동 ──────── */}
      <section className="rounded border border-zinc-200 px-5 py-5">
        <div className="mb-3 flex items-center gap-2">
          <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600">Step 3</span>
          <h2 className="text-sm font-semibold">자소서 작성 화면 이동</h2>
        </div>

        {!hasQuestions ? (
          <p className="text-sm text-zinc-400">
            소스를 연결하고 문항을 등록하면 AI 자소서를 생성할 수 있습니다.
          </p>
        ) : (
          <>
            <p className="mb-3 text-sm text-zinc-500">
              등록된 {questions.length}개 문항을 기준으로 자소서 작성 전용 화면에서 생성과 재생성을 진행합니다.
            </p>
            <button
              onClick={() => router.push(`/applications/${applicationId}/generate`)}
              className="w-full rounded bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white"
            >
              자소서 작성 화면으로 이동
            </button>
          </>
        )}
      </section>
    </main>
  );
}
