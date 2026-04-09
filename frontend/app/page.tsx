'use client';

import Link from "next/link";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { getMe, logout } from "@/api/auth";

const FEATURES = [
  {
    number: "01",
    title: "GitHub 활동 정밀 분석",
    color: "from-violet-500 to-indigo-500",
    bgLight: "bg-violet-50",
    borderLight: "border-violet-100",
    items: [
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
        ),
        label: "Public 레포지토리 분석",
        desc: "GitHub 계정을 연동하면 공개된 저장소의 코드를 분석합니다.",
      },
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        ),
        label: "오픈소스 기여 내역",
        desc: "PR·Issue 등 오픈소스 기여 내역까지 포함해 기술적 역량을 추출합니다.",
      },
    ],
  },
  {
    number: "02",
    title: "안전한 서류 관리",
    badge: "Privacy First",
    color: "from-emerald-500 to-teal-500",
    bgLight: "bg-emerald-50",
    borderLight: "border-emerald-100",
    items: [
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
          </svg>
        ),
        label: "자동 마스킹 시스템",
        desc: "기존 자소서·수상 기록 업로드 시 AI가 개인정보를 자동으로 마스킹합니다.",
      },
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8" />
          </svg>
        ),
        label: "데이터 자산화",
        desc: "업로드된 문서는 나만의 데이터가 되어 향후 자소서 생성의 강력한 근거가 됩니다.",
      },
    ],
  },
  {
    number: "03",
    title: "데이터 기반 맞춤형 자소서 생성",
    color: "from-orange-500 to-amber-500",
    bgLight: "bg-orange-50",
    borderLight: "border-orange-100",
    items: [
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
          </svg>
        ),
        label: "정밀 큐레이션",
        desc: "분석된 레포지토리와 서류 중 강조하고 싶은 항목을 직접 선택합니다.",
      },
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
          </svg>
        ),
        label: "직무 및 기업 최적화",
        desc: "지원 기업과 직무에 맞춰, 선별된 데이터가 논리적으로 연결된 고퀄리티 자소서를 생성합니다.",
      },
    ],
  },
  {
    number: "04",
    title: "실전 같은 AI 모의 면접 & 피드백",
    color: "from-rose-500 to-pink-500",
    bgLight: "bg-rose-50",
    borderLight: "border-rose-100",
    items: [
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 10l4.553-2.069A1 1 0 0121 8.82v6.36a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
          </svg>
        ),
        label: "자소서 기반 면접",
        desc: "생성된 자기소개서를 바탕으로 예상 질문을 도출해 실시간 모의 면접을 진행합니다.",
      },
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
        ),
        label: "심층 피드백",
        desc: "면접 답변 분석과 개선 방향을 즉시 확인해 답변의 완성도를 높일 수 있습니다.",
      },
    ],
  },
  {
    number: "05",
    title: "유형별 집중 대비 탭",
    color: "from-sky-500 to-blue-500",
    bgLight: "bg-sky-50",
    borderLight: "border-sky-100",
    items: [
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
        ),
        label: "인성 면접 대비",
        desc: "협업·소통·문제 해결 능력 등 개발자에게 필요한 인성 질문 리스트를 제공합니다.",
      },
      {
        icon: (
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17H3a2 2 0 01-2-2V5a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2h-2" />
          </svg>
        ),
        label: "CS 지식 면접 대비",
        desc: "OS·네트워크·DB 등 직무별 필수 CS 질문과 모범 답안을 학습할 수 있습니다.",
      },
    ],
  },
];

const STEPS = [
  {
    step: "01",
    title: "GitHub 연동",
    desc: "분석하고 싶은 Public 레포지토리를 선택하세요.",
    color: "bg-violet-500",
  },
  {
    step: "02",
    title: "서류 업로드",
    desc: "기존 자소서나 상장을 올려 보세요. (자동 마스킹 지원)",
    color: "bg-emerald-500",
  },
  {
    step: "03",
    title: "커스터마이징",
    desc: "강조하고 싶은 이력과 지원할 회사를 골라 자소서를 완성하세요.",
    color: "bg-orange-500",
  },
  {
    step: "04",
    title: "트레이닝",
    desc: "생성된 자소서를 확인하고 바로 모의 면접을 시작하세요!",
    color: "bg-rose-500",
  },
];

export default function Home() {
  const router = useRouter();
  const [loggedIn, setLoggedIn] = useState<boolean | null>(null);

  useEffect(() => {
    getMe().then((user) => {
      if (user) {
        setLoggedIn(true); // Set loggedIn to true if user exists
      } else {
        setLoggedIn(false);
      }
    });
  }, []); // Removed router from dependency array as it's not used for redirection anymore

  async function handleSwitchAccount() {
    await logout();
    window.location.href = '/login';
  }

  if (loggedIn === null) return null;

  return (
    <main className="min-h-screen bg-white">
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-br from-zinc-950 via-zinc-900 to-zinc-800 px-6 py-24 text-white">
        <div className="absolute inset-0 opacity-10"
          style={{
            backgroundImage:
              "radial-gradient(circle at 20% 50%, #8b5cf6 0%, transparent 50%), radial-gradient(circle at 80% 20%, #3b82f6 0%, transparent 50%)",
          }}
        />
        <div className="relative mx-auto max-w-3xl text-center">
          <span className="mb-4 inline-flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-4 py-1.5 text-sm font-medium text-white/80 backdrop-blur-sm">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
            AI 기반 취업 준비 플랫폼
          </span>
          <p className="mt-6 text-xl text-white/60 font-light">
            GitHub 포트폴리오 분석부터 자소서 생성, AI 모의 면접까지
          </p>
          <p className="mt-2 text-lg text-white/40">
            개발자 취업 준비의 모든 과정을 한 곳에서.
          </p>
          <div className="mt-10 flex flex-wrap justify-center gap-4">
            <Link
              href={loggedIn ? "/portfolio" : "/login"}
              className="rounded-lg bg-white px-8 py-3 font-semibold text-zinc-900 shadow-lg transition hover:bg-zinc-100 hover:shadow-xl"
            >
              지금 시작하기 →
            </Link>
            {loggedIn ? (
              <button
                onClick={handleSwitchAccount}
                className="rounded-lg border border-white/20 bg-white/10 px-8 py-3 font-medium text-white backdrop-blur-sm transition hover:bg-white/20"
              >
                다른 계정으로 로그인
              </button>
            ) : (
              <Link
                href="/login"
                className="rounded-lg border border-white/20 bg-white/10 px-8 py-3 font-medium text-white backdrop-blur-sm transition hover:bg-white/20"
              >
                로그인
              </Link>
            )}
          </div>
        </div>
      </section>

      {/* Core Flow Highlight */}
      <section className="mx-auto max-w-5xl px-6 py-20">
        <div className="mb-10 text-center">
          <span className="text-sm font-semibold uppercase tracking-widest text-zinc-400">How it works</span>
          <h2 className="mt-3 text-3xl font-bold text-zinc-900">나만의 재료로 만드는 맞춤 자소서</h2>
          <p className="mt-3 text-zinc-500 max-w-xl mx-auto text-sm leading-relaxed">
            내가 올린 GitHub 레포지토리와 자소서·수상 기록 중 <strong className="text-zinc-800">원하는 것만 직접 골라</strong>,
            지원할 회사와 직무에 딱 맞는 자기소개서를 생성합니다.
          </p>
        </div>

        {/* Flow diagram */}
        <div className="rounded-3xl border border-zinc-200 bg-zinc-50 p-8">
          {/* Inputs row */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            {/* GitHub repos */}
            <div className="rounded-2xl border border-violet-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-3">
                <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-violet-100">
                  <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 0C5.374 0 0 5.373 0 12c0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0112 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12z"/>
                  </svg>
                </span>
                <span className="text-sm font-semibold text-zinc-700">GitHub 레포지토리</span>
              </div>
              <div className="space-y-2">
                {["✓  my-spring-project", "✓  devready-clone", "—  old-toy-project"].map((r, i) => (
                  <div key={i} className={`flex items-center gap-2 rounded-lg px-3 py-2 text-xs ${i < 2 ? "bg-violet-50 text-violet-700 font-medium border border-violet-200" : "bg-zinc-50 text-zinc-400 line-through"}`}>
                    {r}
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs font-semibold text-violet-600 flex items-center gap-1">
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 15l-2 5L9 9l11 4-5 2zm0 0l5 5" />
                </svg>
                분석된 레포 중 강조할 것만 선택
              </p>
            </div>

            {/* Documents */}
            <div className="rounded-2xl border border-emerald-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-3">
                <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-100">
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                </span>
                <span className="text-sm font-semibold text-zinc-700">자소서 · 수상 기록</span>
              </div>
              <div className="space-y-2">
                {["✓  2024 카카오 자소서", "✓  SW 경진대회 금상", "—  2022 인턴 자소서"].map((r, i) => (
                  <div key={i} className={`flex items-center gap-2 rounded-lg px-3 py-2 text-xs ${i < 2 ? "bg-emerald-50 text-emerald-700 font-medium border border-emerald-200" : "bg-zinc-50 text-zinc-400 line-through"}`}>
                    {r}
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs font-semibold text-emerald-600 flex items-center gap-1">
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 15l-2 5L9 9l11 4-5 2zm0 0l5 5" />
                </svg>
                업로드한 서류 중 관련 있는 것만 선택
              </p>
            </div>

            {/* Target */}
            <div className="rounded-2xl border border-orange-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-2 mb-3">
                <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-orange-100">
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
                  </svg>
                </span>
                <span className="text-sm font-semibold text-zinc-700">지원 회사 · 직무</span>
              </div>
              <div className="space-y-2">
                <div className="rounded-lg bg-orange-50 border border-orange-200 px-3 py-2.5">
                  <p className="text-xs font-semibold text-orange-700">회사</p>
                  <p className="text-sm font-bold text-orange-900 mt-0.5">카카오</p>
                </div>
                <div className="rounded-lg bg-orange-50 border border-orange-200 px-3 py-2.5">
                  <p className="text-xs font-semibold text-orange-700">직무</p>
                  <p className="text-sm font-bold text-orange-900 mt-0.5">백엔드 개발자</p>
                </div>
              </div>
              <p className="mt-3 text-xs font-semibold text-orange-600 flex items-center gap-1">
                <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 15l-2 5L9 9l11 4-5 2zm0 0l5 5" />
                </svg>
                회사와 직무에 맞게 톤·강조점 최적화
              </p>
            </div>
          </div>

          {/* Arrow */}
          <div className="my-6 flex flex-col items-center gap-1">
            <div className="flex items-center gap-3 rounded-full bg-white border border-zinc-200 px-5 py-2 shadow-sm">
              <span className="text-sm font-semibold text-zinc-600">선택한 재료 →</span>
              <span className="text-sm font-bold text-zinc-900">AI 자소서 생성</span>
            </div>
            <div className="h-6 w-px bg-zinc-200" />
            <svg className="h-4 w-4 text-zinc-400" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </div>

          {/* Output */}
          <div className="flex flex-col sm:flex-row items-stretch gap-0">
            {/* OUTPUT card */}
            <div className="flex-1 rounded-2xl border-2 border-indigo-500 bg-indigo-50 p-5 shadow-md">
              <div className="flex items-center gap-2 mb-3">
                <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-indigo-500">
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </span>
                <span className="text-sm font-bold text-indigo-900">맞춤 자기소개서 생성</span>
                <span className="ml-auto rounded-full bg-indigo-500 px-2.5 py-0.5 text-xs font-medium text-white">OUTPUT</span>
              </div>
              <p className="text-xs text-indigo-700 leading-relaxed">
                선택한 레포지토리의 기술적 성과, 자소서의 경험, 수상 이력을 엮어
                <strong className="text-indigo-900"> 카카오 백엔드 포지션</strong>에 최적화된 자기소개서를 완성합니다.
              </p>
            </div>

            {/* Arrow connector */}
            <div className="flex items-center justify-center px-2 py-3 sm:py-0">
              <div className="flex flex-col sm:flex-row items-center gap-1">
                {/* mobile: vertical arrow */}
                <div className="flex sm:hidden flex-col items-center gap-0.5">
                  <div className="h-4 w-px bg-gradient-to-b from-indigo-400 to-amber-400" />
                  <svg className="h-4 w-4 text-amber-400" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                  </svg>
                </div>
                {/* desktop: horizontal arrow */}
                <div className="hidden sm:flex items-center gap-0.5">
                  <div className="w-4 h-px bg-gradient-to-r from-indigo-400 to-amber-400" />
                  <svg className="h-4 w-4 text-amber-400" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                </div>
              </div>
            </div>

            {/* NEXT card */}
            <div className="flex-1 rounded-2xl border-2 border-amber-400 bg-amber-50 p-5 shadow-md">
              <div className="flex items-center gap-2 mb-3">
                <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-amber-400">
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M15 10l4.553-2.069A1 1 0 0121 8.82v6.36a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                </span>
                <span className="text-sm font-bold text-amber-900">생성된 자소서로 모의 면접</span>
                <span className="ml-auto rounded-full bg-amber-400 px-2.5 py-0.5 text-xs font-medium text-white">RESULT</span>
              </div>
              <p className="text-xs text-amber-700 leading-relaxed">
                완성된 자소서를 바탕으로 <strong className="text-amber-900">예상 면접 질문을 자동 도출</strong>하고,
                실시간 AI 모의 면접과 심층 피드백으로 답변 완성도를 높입니다.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="mx-auto max-w-5xl px-6 py-20">
        <div className="mb-12 text-center">
          <span className="text-sm font-semibold uppercase tracking-widest text-zinc-400">Features</span>
          <h2 className="mt-3 text-3xl font-bold text-zinc-900">🛠 주요 기능 안내</h2>
        </div>

        <div className="grid gap-6 sm:grid-cols-2">
          {FEATURES.map((feature) => (
            <div
              key={feature.number}
              className={`rounded-2xl border ${feature.borderLight} ${feature.bgLight} p-6 transition hover:shadow-md`}
            >
              <div className="mb-4 flex items-center gap-3">
                <span
                  className={`inline-flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br ${feature.color} text-xs font-bold text-white shadow-sm`}
                >
                  {feature.number}
                </span>
                <h3 className="font-semibold text-zinc-900 text-base leading-snug">
                  {feature.title}
                  {feature.badge && (
                    <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">
                      {feature.badge}
                    </span>
                  )}
                </h3>
              </div>
              <ul className="space-y-3">
                {feature.items.map((item) => (
                  <li key={item.label} className="flex gap-3">
                    <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-white/80 text-zinc-500 shadow-sm">
                      {item.icon}
                    </span>
                    <div>
                      <p className="text-sm font-medium text-zinc-800">{item.label}</p>
                      <p className="text-xs text-zinc-500 mt-0.5 leading-relaxed">{item.desc}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          ))}

        </div>
      </section>

      {/* How to start */}
      <section className="bg-zinc-50 px-6 py-20">
        <div className="mx-auto max-w-5xl">
          <div className="mb-12 text-center">
            <span className="text-sm font-semibold uppercase tracking-widest text-zinc-400">How to start</span>
            <h2 className="mt-3 text-3xl font-bold text-zinc-900">💡 시작하는 방법</h2>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((s, i) => (
              <div key={s.step} className="relative rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
                {i < STEPS.length - 1 && (
                  <span className="absolute -right-2 top-8 hidden text-zinc-300 lg:block text-lg">→</span>
                )}
                <span className={`inline-flex h-9 w-9 items-center justify-center rounded-xl ${s.color} text-sm font-bold text-white shadow`}>
                  {s.step}
                </span>
                <p className="mt-4 font-semibold text-zinc-900">{s.title}</p>
                <p className="mt-1.5 text-xs text-zinc-500 leading-relaxed">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Privacy notice */}
      <section className="border-t border-zinc-100 px-6 py-10">
        <div className="mx-auto max-w-2xl text-center">
          <p className="flex items-center justify-center gap-2 text-sm text-zinc-400">
            <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
            사용자의 데이터를 학습에 무단 사용하지 않으며, 모든 분석 과정은 안전하게 보호됩니다.
          </p>
        </div>
      </section>
    </main>
  );
}
