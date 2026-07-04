import { apiClient } from '@/api/client'
import type { AuthTokens, LoginRequest, RegisterRequest, RegisterResponse } from '../types/auth.types'

export const authApi = {
  register: (data: RegisterRequest) =>
    apiClient.post<RegisterResponse>('/auth/register', data).then(r => r.data),

  login: (data: LoginRequest) =>
    apiClient.post<AuthTokens>('/auth/login', data).then(r => r.data),

  refresh: (refreshToken: string) =>
    apiClient.post<AuthTokens>('/auth/refresh', { refreshToken }).then(r => r.data),

  logout: (refreshToken: string) =>
    apiClient.post('/auth/logout', { refreshToken }),
}
