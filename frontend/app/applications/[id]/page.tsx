'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import type { Application, ApplicationQuestion, QuestionItem } from '@/types/application';
import type { Document } from '@/types/document';
import type { GithubRepository } from '@/types/github';
import {
  getApplication,
  getQuestions,
  getSources,
  saveSources,
  saveQuestions,
  generateAnswers,
} from '@/api/application';
import { getDocuments } from '@/api/document';
import { getRepositories } from '@/api/github';

const TONE_OPTIONS = [
  { value: 'formal', label: '격식체' },
  { value: 'balanced', label: '균형' },
  { value: 'casual', label: '구어체' },
];

const LENGTH_OPTIONS = [
  { value: 'short', label: '짧게 (500자 내외)' },
  { value: 'medium', label: '보통 (900자 내외)' },
  { value: 'long', label: '길게 (1400자 내외)' },
];

export default function ApplicationDetailPage() {
  const params = useParams();
  const router = useRouter();
  const searchParams = useSearchParams();
  const applicationId = Number(params.id);

  const MAX_SOURCES = 5;

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

  // 공통 강조 메모
  const [userMemo, setUserMemo] = useState('');
  const [memoOpen, setMemoOpen] = useState(false);

  // 변경 감지 & 이동 상태
  const changedSinceLoad = useRef(false);
  const [navigating, setNavigating] = useState(false);

  // ── 데이터 로딩 ──────────────────────────────
  const loadAll = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [app, docs, repoResult, qs, sources] = await Promise.all([
        getApplication(applicationId),
        getDocuments(),
        getRepositories({ selected: true, size: 1000 }),
        getQuestions(applicationId),
        getSources(applicationId),
      ]);

      // ready 상태이고 edit 파라미터가 없으면 /generate로 리다이렉트
      if (app.status === 'ready' && !searchParams.get('edit')) {
        router.replace(`/applications/${applicationId}/generate`);
        return;
      }

      setApplication(app);
      setDocuments(docs.filter((d) => d.extractStatus === 'success'));
      setRepos(repoResult.data.filter((r) => r.isSelected));
      setQuestions(qs);
      setSelectedDocIds(new Set(sources.documentIds));
      setSelectedRepoIds(new Set(sources.repositoryIds));

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
        setEditQuestions([{ questionOrder: 1, questionText: '', toneOption: 'balanced', lengthOption: 'medium', emphasisPoint: null }]);
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

  // ── 소스 자동 저장 ──────────────────────────
  async function autoSaveSources(docIds: Set<number>, repoIds: Set<number>) {
    setSavingSource(true);
    setSourceMessage(null);
    try {
      const result = await saveSources(applicationId, {
        repositoryIds: Array.from(repoIds),
        documentIds: Array.from(docIds),
      });
      changedSinceLoad.current = true;
      setSourceMessage({ type: 'success', text: `소스 ${result.sourceCount}개가 연결되었습니다.` });
    } catch (err) {
      setSourceMessage({ type: 'error', text: err instanceof Error ? err.message : '소스 저장 실패' });
    } finally {
      setSavingSource(false);
    }
  }

  // ── 체크박스 토글 (자동 저장, 최대 5개 제한) ──────────────
  const totalSelected = selectedDocIds.size + selectedRepoIds.size;

  function toggleDoc(id: number) {
    if (!selectedDocIds.has(id) && totalSelected >= MAX_SOURCES) {
      setSourceMessage({ type: 'error', text: `소스는 최대 ${MAX_SOURCES}개까지 선택할 수 있습니다.` });
      return;
    }
    const next = new Set(selectedDocIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelectedDocIds(next);
    void autoSaveSources(next, selectedRepoIds);
  }

  function toggleRepo(id: number) {
    if (!selectedRepoIds.has(id) && totalSelected >= MAX_SOURCES) {
      setSourceMessage({ type: 'error', text: `소스는 최대 ${MAX_SOURCES}개까지 선택할 수 있습니다.` });
      return;
    }
    const next = new Set(selectedRepoIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelectedRepoIds(next);
    void autoSaveSources(selectedDocIds, next);
  }

  // ── 문항 자동 저장 ──────────────────────────
  async function autoSaveQuestions(qs: QuestionItem[]) {
    if (!qs.every((q) => q.questionText.trim())) return; // 빈 문항 있으면 스킵
    setSavingQuestions(true);
    setQuestionMessage(null);
    try {
      const saved = await saveQuestions(applicationId, { questions: qs });
      changedSinceLoad.current = true;
      setQuestions(saved);
      setQuestionMessage({ type: 'success', text: `${saved.length}개 문항이 저장되었습니다.` });
    } catch (err) {
      setQuestionMessage({ type: 'error', text: err instanceof Error ? err.message : '문항 저장 실패' });
    } finally {
      setSavingQuestions(false);
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
        toneOption: 'balanced',
        lengthOption: 'medium',
        emphasisPoint: null,
      },
    ]);
  }

  // ── 자소서 작성 화면 이동 (변경 감지 시 자동 생성) ──────────────
  async function handleGoToGenerate() {
    setNavigating(true);
    try {
      // userMemo가 있으면 각 문항의 emphasisPoint에 merge 후 저장
      const memo = userMemo.trim();
      if (memo && editQuestions.every((q) => q.questionText.trim())) {
        const merged = editQuestions.map((q) => ({
          ...q,
          emphasisPoint: mergeEmphasisPoint(memo, q.emphasisPoint),
        }));
        await saveQuestions(applicationId, { questions: merged });
        changedSinceLoad.current = true;
      }

      if (changedSinceLoad.current) {
        await generateAnswers(applicationId, { useTemplate: true, regenerate: true });
      }
    } catch {
      // 생성 실패해도 이동은 진행
    } finally {
      setNavigating(false);
    }
    router.push(`/applications/${applicationId}/generate`);
  }

  function mergeEmphasisPoint(memo: string, emphasisPoint: string | null): string | null {
    const hasPoint = emphasisPoint != null && emphasisPoint.trim().length > 0;
    if (hasPoint) return `공통: ${memo} / 문항별: ${emphasisPoint!.trim()}`;
    return memo;
  }

  function removeQuestion(index: number) {
    const next = editQuestions
      .filter((_, i) => i !== index)
      .map((q, i) => ({ ...q, questionOrder: i + 1 }));
    setEditQuestions(next);
    void autoSaveQuestions(next);
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
          {savingSource && (
            <span className="text-xs text-zinc-400">저장 중...</span>
          )}
        </div>
        <p className="mb-3 text-xs text-zinc-500">
          AI가 참고할 문서와 레포지토리를 선택하세요. 최대 {MAX_SOURCES}개까지 선택할 수 있으며, 선택 즉시 자동 저장됩니다.{' '}
          <span className={totalSelected >= MAX_SOURCES ? 'text-red-500 font-medium' : 'text-zinc-400'}>
            ({totalSelected}/{MAX_SOURCES})
          </span>
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
                    disabled={savingSource}
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
                    disabled={savingSource}
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

        {sourceMessage && (
          <p className={`mt-2 text-sm ${sourceMessage.type === 'success' ? 'text-green-700' : 'text-red-700'}`}>
            {sourceMessage.text}
          </p>
        )}
      </section>

      {/* ── 공통 강조 메모 (접이식) ──────────── */}
      <section className="mb-8">
        <button
          type="button"
          onClick={() => setMemoOpen((prev) => !prev)}
          className="flex w-full items-center justify-between rounded border border-zinc-200 px-4 py-2.5 text-left text-sm font-medium text-zinc-700 hover:bg-zinc-50"
        >
          <span>
            공통 강조 메모
            {userMemo.trim() && (
              <span className="ml-2 rounded bg-yellow-100 px-1.5 py-0.5 text-xs font-normal text-yellow-700">
                입력됨
              </span>
            )}
          </span>
          <span className="text-zinc-400">{memoOpen ? '▲' : '▼'}</span>
        </button>

        {memoOpen && (
          <div className="rounded-b border border-t-0 border-zinc-200 px-4 py-3">
            <p className="mb-2 text-xs text-zinc-500">
              모든 문항 생성 시 공통으로 반영할 강조 사항을 자유롭게 입력하세요. 이동 시 각 문항의 강조점에 합산됩니다.
            </p>
            <textarea
              value={userMemo}
              onChange={(e) => setUserMemo(e.target.value)}
              placeholder="예: AWS 자격증 보유, 최근 사이드 프로젝트에서 팀 리더 경험"
              rows={3}
              maxLength={200}
              className="w-full resize-none rounded border border-zinc-200 px-3 py-2 text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-1 focus:ring-zinc-400"
            />
            <p className="mt-1 text-right text-xs text-zinc-400">{userMemo.length}/200</p>
          </div>
        )}
      </section>

      {/* ── STEP 2: 문항 등록 ──────────────── */}
      <section className="mb-8">
        <div className="mb-3 flex items-center gap-2">
          <span className="rounded bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600">Step 2</span>
          <h2 className="text-sm font-semibold">자소서 문항 등록</h2>
          {savingQuestions && (
            <span className="text-xs text-zinc-400">저장 중...</span>
          )}
        </div>
        <p className="mb-3 text-xs text-zinc-500">
          자소서 문항과 옵션(톤, 길이, 강조점)을 설정하세요. 입력 완료 후 자동 저장됩니다.
        </p>

        <div className="flex flex-col gap-4">
          {editQuestions.map((q, idx) => (
            <div key={idx} className="rounded border border-zinc-200 px-4 py-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-xs font-medium text-zinc-500">문항 {q.questionOrder}</span>
                {editQuestions.length > 1 && (
                  <button
                    onClick={() => removeQuestion(idx)}
                    disabled={savingQuestions}
                    className="text-xs text-zinc-400 hover:text-red-500 disabled:opacity-50"
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
                onBlur={() => void autoSaveQuestions(editQuestions)}
                rows={2}
                className="w-full rounded border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none resize-none"
              />

              {/* 옵션 행 */}
              <div className="mt-2 flex flex-wrap gap-3">
                <div>
                  <label className="block text-xs text-zinc-400 mb-0.5">톤</label>
                  <select
                    value={q.toneOption ?? ''}
                    onChange={(e) => {
                      const val = e.target.value || null;
                      const updated = editQuestions.map((item, i) =>
                        i === idx ? { ...item, toneOption: val } : item,
                      );
                      setEditQuestions(updated);
                      void autoSaveQuestions(updated);
                    }}
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
                    onChange={(e) => {
                      const val = e.target.value || null;
                      const updated = editQuestions.map((item, i) =>
                        i === idx ? { ...item, lengthOption: val } : item,
                      );
                      setEditQuestions(updated);
                      void autoSaveQuestions(updated);
                    }}
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
                    placeholder="예: 팀워크"
                    value={q.emphasisPoint ?? ''}
                    onChange={(e) => updateQuestion(idx, 'emphasisPoint', e.target.value || null)}
                    onBlur={() => void autoSaveQuestions(editQuestions)}
                    maxLength={10}
                    className="w-full rounded border border-zinc-300 px-2 py-1 text-xs focus:border-zinc-500 focus:outline-none"
                  />
                  <p className="mt-0.5 text-right text-xs text-zinc-400">{(q.emphasisPoint ?? '').length}/10</p>
                </div>
              </div>
            </div>
          ))}
        </div>

        {editQuestions.length < 5 && (
          <button
            onClick={addQuestion}
            disabled={savingQuestions}
            className="mt-3 w-full rounded border border-dashed border-zinc-300 py-2 text-sm text-zinc-500 hover:border-zinc-400 hover:text-zinc-700 disabled:opacity-50"
          >
            + 문항 추가
          </button>
        )}

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
              onClick={handleGoToGenerate}
              disabled={navigating}
              className="w-full rounded bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
            >
              {navigating ? 'AI 답변 생성 중...' : '자소서 작성 화면으로 이동'}
            </button>
          </>
        )}
      </section>
    </main>
  );
}
