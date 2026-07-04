import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RootLayout } from '@/components/layout/RootLayout'
import { RequireAuth } from '@/components/guards/RequireAuth'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { HealthPage } from '@/features/health/HealthPage'

export const router = createBrowserRouter([
  // Rotas publicas (sem layout autenticado)
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },

  // Rotas protegidas
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <RootLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: 'dashboard', element: <HealthPage /> },
        ],
      },
    ],
  },

  // Fallback
  { path: '*', element: <Navigate to="/login" replace /> },
])
