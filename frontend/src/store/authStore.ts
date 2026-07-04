import type { AuthTokens, AuthUser } from '@/features/auth/types/auth.types'

const KEYS = {
  accessToken: 'access_token',
  refreshToken: 'refresh_token',
  user: 'auth_user',
} as const

export const authStore = {
  getAccessToken: () => localStorage.getItem(KEYS.accessToken),
  getRefreshToken: () => localStorage.getItem(KEYS.refreshToken),
  getUser: (): AuthUser | null => {
    const raw = localStorage.getItem(KEYS.user)
    return raw ? JSON.parse(raw) : null
  },
  save: (tokens: AuthTokens, user: AuthUser) => {
    localStorage.setItem(KEYS.accessToken, tokens.accessToken)
    localStorage.setItem(KEYS.refreshToken, tokens.refreshToken)
    localStorage.setItem(KEYS.user, JSON.stringify(user))
  },
  clear: () => {
    Object.values(KEYS).forEach(k => localStorage.removeItem(k))
  },
  isAuthenticated: () => !!localStorage.getItem(KEYS.accessToken),
}
