'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  length: number;
  [index: number]: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionResultListLike {
  length: number;
  [index: number]: SpeechRecognitionResultLike;
}

interface SpeechRecognitionEventLike {
  results: SpeechRecognitionResultListLike;
}

interface SpeechRecognitionErrorEventLike {
  error?: string;
}

interface BrowserSpeechRecognitionLike {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  maxAlternatives: number;
  onstart: (() => void) | null;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}

interface BrowserSpeechRecognitionConstructor {
  new (): BrowserSpeechRecognitionLike;
}

declare global {
  interface Window {
    SpeechRecognition?: BrowserSpeechRecognitionConstructor;
    webkitSpeechRecognition?: BrowserSpeechRecognitionConstructor;
  }
}

export type BrowserSpeechSupport = 'supported' | 'unsupported';

interface BrowserSpeechSupportState {
  support: BrowserSpeechSupport;
  message: string | null;
}

export interface UseBrowserSpeechRecognitionResult {
  browserSupport: BrowserSpeechSupport;
  supportMessage: string | null;
  isListening: boolean;
  finalTranscript: string;
  interimTranscript: string;
  errorMessage: string | null;
  startListening: () => void;
  stopListening: () => void;
  cancelListening: () => void;
  resetTranscript: () => void;
}

function isDesktopChromiumUserAgent(userAgent: string) {
  const normalized = userAgent.toLowerCase();
  const isMobile =
    normalized.includes('android')
    || normalized.includes('iphone')
    || normalized.includes('ipad')
    || normalized.includes('mobile');
  const isChromiumFamily =
    normalized.includes('chrome')
    || normalized.includes('chromium')
    || normalized.includes('edg/');

  return isChromiumFamily && !isMobile;
}

function resolveBrowserSpeechSupport(): BrowserSpeechSupportState {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return { support: 'unsupported', message: '브라우저 환경에서만 음성 입력을 사용할 수 있습니다.' };
  }

  const RecognitionConstructor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
  if (!RecognitionConstructor) {
    return {
      support: 'unsupported',
      message: '이 브라우저는 v1 음성 입력 지원 대상이 아니어서 텍스트 입력으로 계속 진행합니다.',
    };
  }

  if (!isDesktopChromiumUserAgent(navigator.userAgent)) {
    return {
      support: 'unsupported',
      message: 'v1 음성 입력은 Chrome 계열 데스크톱을 우선 지원합니다. 같은 세션에서 텍스트 입력으로 계속 진행해주세요.',
    };
  }

  return { support: 'supported', message: null };
}

function mapSpeechRecognitionError(errorCode?: string) {
  switch (errorCode) {
    case 'audio-capture':
      return '마이크 입력을 가져오지 못했습니다. 장치 상태를 확인하거나 텍스트 입력으로 계속 진행해주세요.';
    case 'not-allowed':
    case 'service-not-allowed':
      return '음성 인식 권한이 허용되지 않았습니다. 권한을 다시 확인하거나 텍스트 입력으로 계속 진행해주세요.';
    case 'network':
      return '음성 인식 연결이 불안정합니다. 잠시 후 다시 시도하거나 텍스트 입력으로 계속 진행해주세요.';
    case 'no-speech':
      return '입력된 음성이 감지되지 않았습니다. 다시 시도하거나 텍스트 입력으로 계속 진행해주세요.';
    default:
      return '음성 인식을 계속할 수 없습니다. 텍스트 입력으로 계속 진행해주세요.';
  }
}

export function useBrowserSpeechRecognition(): UseBrowserSpeechRecognitionResult {
  const recognitionRef = useRef<BrowserSpeechRecognitionLike | null>(null);
  const initialSupport = resolveBrowserSpeechSupport();

  const [browserSupport, setBrowserSupport] = useState<BrowserSpeechSupport>(initialSupport.support);
  const [supportMessage, setSupportMessage] = useState<string | null>(initialSupport.message);
  const [isListening, setIsListening] = useState(false);
  const [finalTranscript, setFinalTranscript] = useState('');
  const [interimTranscript, setInterimTranscript] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const resetTranscript = useCallback(() => {
    setFinalTranscript('');
    setInterimTranscript('');
    setErrorMessage(null);
  }, []);

  const detachRecognition = useCallback(() => {
    if (!recognitionRef.current) {
      return;
    }

    recognitionRef.current.onstart = null;
    recognitionRef.current.onresult = null;
    recognitionRef.current.onerror = null;
    recognitionRef.current.onend = null;
    recognitionRef.current = null;
  }, []);

  const cancelListening = useCallback(() => {
    const recognition = recognitionRef.current;
    if (recognition) {
      try {
        recognition.abort();
      } catch {
        // abort가 실패해도 로컬 상태는 즉시 정리해 다음 질문에 stale transcript가 남지 않게 한다.
      }
    }

    detachRecognition();
    setIsListening(false);
    resetTranscript();
  }, [detachRecognition, resetTranscript]);

  const ensureRecognition = useCallback(() => {
    if (browserSupport !== 'supported') {
      return null;
    }

    if (recognitionRef.current) {
      return recognitionRef.current;
    }

    const RecognitionConstructor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
    if (!RecognitionConstructor) {
      setBrowserSupport('unsupported');
      setSupportMessage('이 브라우저는 v1 음성 입력 지원 대상이 아니어서 텍스트 입력으로 계속 진행합니다.');
      return null;
    }

    const recognition = new RecognitionConstructor();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = 'ko-KR';
    recognition.maxAlternatives = 1;
    recognition.onstart = () => {
      setIsListening(true);
      setErrorMessage(null);
    };
    recognition.onresult = (event) => {
      let nextFinalTranscript = '';
      let nextInterimTranscript = '';

      for (let index = 0; index < event.results.length; index += 1) {
        const transcript = event.results[index]?.[0]?.transcript?.trim();
        if (!transcript) {
          continue;
        }

        if (event.results[index].isFinal) {
          nextFinalTranscript = `${nextFinalTranscript} ${transcript}`.trim();
        } else {
          nextInterimTranscript = `${nextInterimTranscript} ${transcript}`.trim();
        }
      }

      setFinalTranscript(nextFinalTranscript);
      setInterimTranscript(nextInterimTranscript);
      setErrorMessage(null);
    };
    recognition.onerror = (event) => {
      setErrorMessage(mapSpeechRecognitionError(event.error));
    };
    recognition.onend = () => {
      setIsListening(false);
    };

    recognitionRef.current = recognition;
    return recognition;
  }, [browserSupport]);

  const startListening = useCallback(() => {
    const recognition = ensureRecognition();
    if (!recognition) {
      return;
    }

    resetTranscript();

    try {
      recognition.start();
    } catch {
      setIsListening(false);
      setErrorMessage('음성 인식을 시작하지 못했습니다. 텍스트 입력으로 계속 진행해주세요.');
    }
  }, [ensureRecognition, resetTranscript]);

  const stopListening = useCallback(() => {
    const recognition = recognitionRef.current;
    if (!recognition) {
      return;
    }

    try {
      recognition.stop();
    } catch {
      setIsListening(false);
    }
  }, []);

  useEffect(() => () => {
    const recognition = recognitionRef.current;
    if (!recognition) {
      return;
    }

    try {
      recognition.abort();
    } catch {
      // 브라우저가 abort를 거부해도 unmount 시점에는 이벤트 핸들러만 정리하면 충분하다.
    }

    detachRecognition();
  }, [detachRecognition]);

  return {
    browserSupport,
    supportMessage,
    isListening,
    finalTranscript,
    interimTranscript,
    errorMessage,
    startListening,
    stopListening,
    cancelListening,
    resetTranscript,
  };
}
