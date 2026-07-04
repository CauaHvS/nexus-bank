import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/authApi'
import type { RegisterRequest } from '../types/auth.types'

export function useRegister() {
  const navigate = useNavigate()
  return useMutation({
    mutationFn: (data: RegisterRequest) => authApi.register(data),
    onSuccess: () => navigate('/login', { state: { registered: true } }),
  })
}
