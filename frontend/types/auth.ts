export type Provider = 'github' | 'google' | 'kakao';

export interface User {
  id: number;
  displayName: string;
  email: string | null;
  profileImageUrl: string | null;
  status: 'active' | 'withdrawn';
}
