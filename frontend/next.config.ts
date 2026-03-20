import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  // 개발 환경에서 백엔드가 다른 포트(8080)에서 실행될 때 API 프록시 설정.
  // BACKEND_URL 환경변수가 없으면 localhost:8080을 기본값으로 사용한다.
  async rewrites() {
    return [
      {
        source: '/api/v1/:path*',
        destination: `${process.env.BACKEND_URL ?? 'http://localhost:8080'}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
