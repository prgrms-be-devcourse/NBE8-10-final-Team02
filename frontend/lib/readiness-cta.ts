import type { InterviewSession, InterviewSessionStatus } from '@/types/interview';
import type { PortfolioNextRecommendedAction } from '@/types/portfolio';

export type ReadinessCtaSurface = 'dashboard' | 'widget';

interface ReadinessCtaAction {
  href: string;
  label: string;
  helper: string;
}

export interface ResolvedReadinessCta {
  primaryAction: ReadinessCtaAction;
  secondaryAction: ReadinessCtaAction | null;
  isSessionPriority: boolean;
  activeSession: InterviewSession | null;
  sessionNotice: string | null;
}

const ACTIVE_SESSION_PRIORITY: InterviewSessionStatus[] = ['in_progress', 'paused'];

const BASE_ACTIONS: Record<
  PortfolioNextRecommendedAction,
  Record<ReadinessCtaSurface, ReadinessCtaAction>
> = {
  connect_github: {
    dashboard: {
      href: '/portfolio/github',
      label: 'GitHub 연결하기',
      helper: 'GitHub 연동을 먼저 완료하면 repository와 커밋 데이터를 활용할 수 있습니다.',
    },
    widget: {
      href: '/portfolio/github',
      label: 'GitHub 연결',
      helper: 'GitHub 연동을 먼저 완료하면 repository와 커밋 데이터를 활용할 수 있습니다.',
    },
  },
  select_repository: {
    dashboard: {
      href: '/portfolio/repositories',
      label: 'repository 선택하기',
      helper: '활용할 repository를 선택해야 GitHub 소스를 지원 준비에 연결할 수 있습니다.',
    },
    widget: {
      href: '/portfolio/repositories',
      label: 'repository 선택',
      helper: '활용할 repository를 선택해야 GitHub 소스를 지원 준비에 연결할 수 있습니다.',
    },
  },
  upload_document: {
    dashboard: {
      href: '/portfolio/documents',
      label: '문서 업로드하기',
      helper: '문서 업로드 후 텍스트 추출이 완료되면 자소서와 면접 준비에 바로 사용할 수 있습니다.',
    },
    widget: {
      href: '/portfolio/documents',
      label: '문서 업로드',
      helper: '문서 업로드 후 텍스트 추출이 완료되면 자소서와 면접 준비에 바로 사용할 수 있습니다.',
    },
  },
  retry_document_extraction: {
    dashboard: {
      href: '/portfolio/documents',
      label: '문서 상태 확인하기',
      helper: '추출 성공 문서가 아직 없어 문서 업로드 화면에서 실패 문서를 다시 확인해야 합니다.',
    },
    widget: {
      href: '/portfolio/documents',
      label: '문서 상태 확인',
      helper: '추출 성공 문서가 아직 없어 문서 업로드 화면에서 실패 문서를 다시 확인해야 합니다.',
    },
  },
  start_application: {
    dashboard: {
      href: '/applications',
      label: '지원 준비 시작',
      helper: '현재 기준으로 바로 지원 준비 흐름으로 넘어갈 수 있습니다.',
    },
    widget: {
      href: '/applications',
      label: '지원 준비로 이동',
      helper: '지금 단계에서 지원 준비 흐름으로 이어서 넘어갈 수 있습니다.',
    },
  },
};

function resolveActiveSession(sessions?: InterviewSession[] | null) {
  if (!Array.isArray(sessions) || sessions.length === 0) {
    return null;
  }

  for (const status of ACTIVE_SESSION_PRIORITY) {
    const matched = sessions.find((session) => session.status === status);
    if (matched) {
      return matched;
    }
  }

  return null;
}

function createSessionPriorityAction(
  surface: ReadinessCtaSurface,
  activeSession: InterviewSession,
): ResolvedReadinessCta {
  const sessionNotice =
    activeSession.status === 'paused'
      ? '일시정지된 모의 면접 세션이 있어 먼저 재개할 수 있습니다.'
      : '진행 중인 모의 면접 세션이 있어 먼저 이어서 진행할 수 있습니다.';

  return {
    primaryAction: {
      href: `/interview/sessions/${activeSession.id}`,
      label: activeSession.status === 'paused' ? '세션 재개' : '세션 이어서 진행',
      helper: sessionNotice,
    },
    secondaryAction: {
      ...BASE_ACTIONS.start_application[surface],
    },
    isSessionPriority: true,
    activeSession,
    sessionNotice,
  };
}

export function resolveReadinessCta(options: {
  nextRecommendedAction: PortfolioNextRecommendedAction;
  surface: ReadinessCtaSurface;
  sessions?: InterviewSession[] | null;
}): ResolvedReadinessCta {
  const { nextRecommendedAction, surface, sessions } = options;
  const activeSession =
    nextRecommendedAction === 'start_application' ? resolveActiveSession(sessions) : null;

  if (activeSession) {
    return createSessionPriorityAction(surface, activeSession);
  }

  return {
    primaryAction: BASE_ACTIONS[nextRecommendedAction][surface],
    secondaryAction: null,
    isSessionPriority: false,
    activeSession: null,
    sessionNotice: null,
  };
}
