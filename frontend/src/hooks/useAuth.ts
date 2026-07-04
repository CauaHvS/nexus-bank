import { useCallback, useSyncExternalStore } from 'react'
import { authStore } from '@/store/authStore'
import { authApi } from '@/features/auth/api/authApi'
import { useNavigate } from 'react-router-dom'

function subscribe(cb: () => void) {
  window.addEventListener('storage', cb)
  return () => window.removeEventListener('storage', cb)
}

export function useAuth() {
  const isAuthenticated = useSyncExternalStore(
    subscribe,
    () => authStore.isAuthenticated(),
    () => false,
  )
  const user = authStore.getUser()
  const navigate = useNavigate()

  const logout = useCallback(async () => {
    const rt = authStore.getRefreshToken()
    if (rt) {
      try { await authApi.logout(rt) } catch { /* ignora erro de rede no logout */ }
    }
    authStore.clear()
    navigate('/login', { replace: true })
  }, [navigate])

  return { isAuthenticated, user, logout }
}
