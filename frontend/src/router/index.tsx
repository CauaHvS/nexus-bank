import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RequireAuth } from '@/components/guards/RequireAuth'
import { AppLayout } from '@/components/layout/AppLayout'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { RegisterPage } from '@/features/auth/pages/RegisterPage'
import { DashboardPage } from '@/features/accounts/pages/DashboardPage'
import { ContasPage } from '@/features/accounts/pages/ContasPage'
import { CarteiraPage } from '@/features/accounts/pages/CarteiraPage'
import { TransferPage } from '@/features/transfers/TransferPage'
import { NotificationsPage } from '@/features/notifications/NotificationsPage'

export const router = createBrowserRouter([
  // Rotas publicas (sem layout autenticado)
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },

  // Rotas protegidas
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'contas', element: <ContasPage /> },
          { path: 'carteira', element: <CarteiraPage /> },
          // Placeholders para fases seguintes
          { path: 'transferencias', element: <TransferPage /> },
          { path: 'extrato', element: <div className="p-8 text-center text-gray-500">Extrato -- Fase 2 (em breve)</div> },
          { path: 'notificacoes', element: <NotificationsPage /> },
          { path: 'perfil', element: <div className="p-8 text-center text-gray-500">Perfil -- proximas fatias</div> },
        ],
      },
    ],
  },

  // Fallback
  { path: '*', element: <Navigate to="/login" replace /> },
])
