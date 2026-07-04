import { useMutation } from '@tanstack/react-query'
import { useNavigate, useLocation } from 'react-router-dom'
import { authApi } from '../api/authApi'
import { authStore } from '@/store/authStore'
import type { LoginRequest } from '../types/auth.types'

export function useLogin() {
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/dashboard'

  return useMutation({
    mutationFn: (data: LoginRequest) => authApi.login(data),
    onSuccess: (tokens, variables) => {
      const payload = JSON.parse(atob(tokens.accessToken.split('.')[1]))
      authStore.save(tokens, {
        userId: payload.sub,
        email: variables.email,
        name: payload.name ?? '',
      })
      navigate(from, { replace: true })
    },
  })
}
