import type { InterviewResult, InterviewSessionStatus } from '@/types/interview';

interface InterviewStatusBadgeMeta {
  label: string;
  tone: string;
}

interface InterviewStatusSummaryMeta {
  eyebrow: string;
  title: string;
  description: string;
  tone: string;
  eyebrowTone: string;
  titleTone: string;
  descriptionTone: string;
}

interface InterviewSessionSummaryOptions {
  status: InterviewSessionStatus;
  autoPauseLikely: boolean;
  justResumed: boolean;
}

export interface PendingResultPanelCopy {
  eyebrow: string;
  title: string;
  description: string;
  detail: string;
  actionLabel: string;
  autoRefreshLabel: string;
  manualReadyLabel: string;
  manualDetail: string;
}

export const PENDING_RESULT_AUTO_RECHECK_INTERVAL_MS = 4_000;
export const PENDING_RESULT_AUTO_RECHECK_MAX_ATTEMPTS = 3;

export const SESSION_STATUS_BADGE_META = {
  ready: {
    label: '준비',
    tone: 'bg-zinc-100 text-zinc-700',
  },
  in_progress: {
    label: '진행 중',
    tone: 'bg-emerald-50 text-emerald-700',
  },
  paused: {
    label: '일시정지',
    tone: 'bg-amber-50 text-amber-700',
  },
  completed: {
    label: '결과 재확인',
    tone: 'bg-cyan-50 text-cyan-700',
  },
  feedback_completed: {
    label: '피드백 완료',
    tone: 'bg-blue-50 text-blue-700',
  },
} satisfies Record<InterviewSessionStatus, InterviewStatusBadgeMeta>;

export const RESULT_STATUS_BADGE_META = {
  completed: {
    label: '결과 확인 가능',
    tone: 'bg-cyan-50 text-cyan-700',
  },
  feedback_completed: {
    label: '피드백 완료',
    tone: 'bg-blue-50 text-blue-700',
  },
} satisfies Record<InterviewResult['status'], InterviewStatusBadgeMeta>;

export const PENDING_RESULT_PANEL_COPY: PendingResultPanelCopy = {
  eyebrow: '결과 생성 대기',
  title: '결과를 준비하고 있습니다.',
  description: '세션 종료는 정상적으로 끝났습니다. 결과 리포트는 잠시 후 준비될 수 있습니다.',
  detail: '결과 화면에서 잠깐 자동으로 다시 확인하고, 더 오래 걸리면 직접 결과를 다시 확인할 수 있습니다.',
  actionLabel: '결과 재확인',
  autoRefreshLabel: '자동으로 다시 확인 중',
  manualReadyLabel: '결과 재확인 가능',
  manualDetail: '자동 재확인이 끝나면 결과 재확인으로 최신 상태를 다시 확인할 수 있습니다.',
};

export function getSessionStatusSummary({
  status,
  autoPauseLikely,
  justResumed,
}: InterviewSessionSummaryOptions): InterviewStatusSummaryMeta {
  if (status === 'paused') {
    return {
      eyebrow: autoPauseLikely ? '자동 일시정지' : '수동 일시정지',
      title: autoPauseLikely ? '자동 일시정지된 것으로 보입니다.' : '현재 일시정지된 세션입니다.',
      description: autoPauseLikely
        ? '마지막 활동 이후 30분 이상 지나 서버가 paused로 정규화했을 가능성이 높습니다. 재개 후 같은 질문부터 다시 이어서 진행하세요.'
        : '같은 세션에서 그대로 재개할 수 있습니다. 재개 버튼으로 같은 질문부터 이어서 답변을 제출하세요.',
      tone: 'border-amber-200 bg-amber-50',
      eyebrowTone: 'text-amber-700',
      titleTone: 'text-amber-950',
      descriptionTone: 'text-amber-800',
    };
  }

  if (status === 'completed') {
    return {
      eyebrow: '세션 종료',
      title: '세션은 종료되었고 결과를 다시 확인할 수 있습니다.',
      description: '결과 화면에서 잠깐 자동으로 다시 확인하고, 더 오래 걸리면 수동 재확인으로 최신 상태를 확인합니다.',
      tone: 'border-cyan-200 bg-cyan-50',
      eyebrowTone: 'text-cyan-700',
      titleTone: 'text-cyan-950',
      descriptionTone: 'text-cyan-800',
    };
  }

  if (status === 'feedback_completed') {
    return {
      eyebrow: '결과 준비 완료',
      title: '피드백 리포트가 준비되었습니다.',
      description: '결과 화면에서 종합 점수, 총평, 질문별 피드백을 다시 확인할 수 있습니다.',
      tone: 'border-blue-200 bg-blue-50',
      eyebrowTone: 'text-blue-700',
      titleTone: 'text-blue-950',
      descriptionTone: 'text-blue-800',
    };
  }

  if (status === 'ready') {
    return {
      eyebrow: '세션 준비',
      title: '면접을 시작할 준비가 되었습니다.',
      description: '현재 질문이 준비되면 진행 상태로 전환되고 답변을 이어서 제출할 수 있습니다.',
      tone: 'border-zinc-200 bg-zinc-50',
      eyebrowTone: 'text-zinc-500',
      titleTone: 'text-zinc-900',
      descriptionTone: 'text-zinc-600',
    };
  }

  return {
    eyebrow: justResumed ? '재개 완료' : '진행 상태',
    title: justResumed ? '세션을 재개했습니다.' : '현재 질문에 답변을 이어가고 있습니다.',
    description: justResumed
      ? '방금 재개되어 같은 질문부터 다시 이어서 제출할 수 있습니다. 다음 질문은 항상 서버가 내려주는 currentQuestion 기준으로 바뀝니다.'
      : '답변 저장 뒤에는 서버가 내려주는 currentQuestion 기준으로 다음 질문이 이어집니다. 필요하면 언제든 명시적으로 일시정지할 수 있습니다.',
    tone: 'border-emerald-200 bg-emerald-50',
    eyebrowTone: 'text-emerald-700',
    titleTone: 'text-emerald-950',
    descriptionTone: 'text-emerald-800',
  };
}

export function getSessionActionLabel(status: InterviewSessionStatus) {
  if (status === 'paused') {
    return '세션 재개';
  }

  if (status === 'in_progress') {
    return '세션 이어서 진행';
  }

  if (status === 'completed') {
    return PENDING_RESULT_PANEL_COPY.actionLabel;
  }

  if (status === 'feedback_completed') {
    return '결과 보기';
  }

  return '상세 보기';
}
