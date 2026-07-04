import { Link, useLocation } from 'react-router-dom'
import { LoginForm } from '../components/LoginForm'

export function LoginPage() {
  const location = useLocation()
  const registered = (location.state as { registered?: boolean })?.registered

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 dark:bg-gray-950">
      <div className="w-full max-w-md space-y-6">
        {/* Brand */}
        <div className="text-center">
          <div className="mb-3 flex items-center justify-center gap-2">
            <div
              className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-600"
              aria-hidden="true"
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="#fff"
                strokeWidth="2.5"
              >
                <rect x="2" y="3" width="20" height="14" rx="2" />
                <line x1="8" y1="21" x2="16" y2="21" />
                <line x1="12" y1="17" x2="12" y2="21" />
              </svg>
            </div>
            <span className="text-2xl font-bold text-gray-900 dark:text-white">
              Nexus Bank
            </span>
          </div>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Banco digital para quem vai mais longe
          </p>
        </div>

        {registered && (
          <div
            role="status"
            className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-700 dark:border-green-800 dark:bg-green-950 dark:text-green-300"
          >
            Conta criada com sucesso. Faca o login para continuar.
          </div>
        )}

        <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h1 className="mb-1 text-xl font-semibold text-gray-900 dark:text-white">
            Bem-vindo de volta
          </h1>
          <p className="mb-6 text-sm text-gray-500 dark:text-gray-400">
            Entre com sua conta para continuar
          </p>
          <LoginForm />
          <p className="mt-6 text-center text-sm text-gray-500 dark:text-gray-400">
            Nao tem conta?{' '}
            <Link
              to="/register"
              className="font-medium text-primary-600 hover:underline dark:text-primary-400"
            >
              Criar conta gratuita
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
