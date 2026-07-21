export interface CurrentUser {
  id: number;
  username: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  authProvider: string;
  githubId: string | null;
  createdAt: string;
}
