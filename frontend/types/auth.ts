export type Provider = 'github' | 'google' | 'kakao';

export interface User {
  id: number;
  displayName: string;
  email: string | null;
  profileImageUrl: string | null;
  status: 'active' | 'withdrawn';
  /**
   * 이 사용자가 연결한 소셜 로그인 provider 목록.
   * 예: ["github"], ["google"], ["kakao"]
   * GitHub 연동 여부를 판단할 때 사용한다.
   */
  connectedProviders: Provider[];
}
