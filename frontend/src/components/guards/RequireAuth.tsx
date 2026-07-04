import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { authStore } from '@/store/authStore'

export function RequireAuth() {
  const location = useLocation()
  if (!authStore.isAuthenticated()) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <Outlet />
}
