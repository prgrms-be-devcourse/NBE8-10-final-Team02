import { describe, expect, it } from 'vitest';
import { resolveReadinessCta } from './readiness-cta';
import type { InterviewSession } from '@/types/interview';

function createSession(overrides?: Partial<InterviewSession>): InterviewSession {
  return {
    id: 901,
    questionSetId: 701,
    status: 'paused',
    totalScore: null,
    summaryFeedback: null,
    startedAt: '2026-04-07T10:00:00Z',
    endedAt: null,
    ...overrides,
  };
}

describe('resolveReadinessCta', () => {
  it('keeps non-start_application actions mapped to readiness targets', () => {
    const resolved = resolveReadinessCta({
      nextRecommendedAction: 'upload_document',
      sessions: [createSession()],
      surface: 'dashboard',
    });

    expect(resolved.isSessionPriority).toBe(false);
    expect(resolved.primaryAction.href).toBe('/portfolio/documents');
    expect(resolved.primaryAction.label).toBe('문서 업로드하기');
    expect(resolved.secondaryAction).toBeNull();
  });

  it('keeps the applications CTA when there is no active session', () => {
    const resolved = resolveReadinessCta({
      nextRecommendedAction: 'start_application',
      sessions: [createSession({ status: 'feedback_completed' })],
      surface: 'widget',
    });

    expect(resolved.isSessionPriority).toBe(false);
    expect(resolved.primaryAction.href).toBe('/applications');
    expect(resolved.primaryAction.label).toBe('지원 준비로 이동');
  });

  it('promotes a paused session over the application CTA', () => {
    const resolved = resolveReadinessCta({
      nextRecommendedAction: 'start_application',
      sessions: [createSession({ status: 'paused' })],
      surface: 'dashboard',
    });

    expect(resolved.isSessionPriority).toBe(true);
    expect(resolved.primaryAction.href).toBe('/interview/sessions/901');
    expect(resolved.primaryAction.label).toBe('세션 재개');
    expect(resolved.secondaryAction?.href).toBe('/applications');
    expect(resolved.secondaryAction?.label).toBe('지원 준비 시작');
  });

  it('prefers an in-progress session over a paused session when both exist', () => {
    const resolved = resolveReadinessCta({
      nextRecommendedAction: 'start_application',
      sessions: [
        createSession({ id: 901, status: 'paused' }),
        createSession({ id: 902, status: 'in_progress' }),
      ],
      surface: 'widget',
    });

    expect(resolved.isSessionPriority).toBe(true);
    expect(resolved.primaryAction.href).toBe('/interview/sessions/902');
    expect(resolved.primaryAction.label).toBe('세션 이어서 진행');
    expect(resolved.secondaryAction?.label).toBe('지원 준비로 이동');
  });
});
