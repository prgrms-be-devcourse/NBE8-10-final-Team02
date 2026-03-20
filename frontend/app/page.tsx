import Link from "next/link";

export default function Home() {
  return (
    <main className="flex flex-col items-center justify-center gap-8 py-24 px-8 text-center">
      <h1 className="text-4xl font-bold">AI 기술 면접 연습</h1>
      <p className="max-w-md text-zinc-500">
        포트폴리오와 자소서를 기반으로 AI가 면접 질문을 생성하고 피드백을 제공합니다.
      </p>
      <div className="flex gap-4">
        <Link
          href="/login"
          className="rounded border border-zinc-300 px-6 py-2 font-medium hover:bg-zinc-50"
        >
          로그인
        </Link>
        <Link
          href="/portfolio"
          className="rounded bg-black px-6 py-2 font-medium text-white hover:bg-zinc-800"
        >
          시작하기
        </Link>
      </div>
    </main>
  );
}
