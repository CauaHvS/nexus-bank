import { Link } from 'react-router-dom'
import { RegisterForm } from '../components/RegisterForm'

export function RegisterPage() {
  return (
    <div className="flex min-h-screen items-start justify-center bg-gray-50 px-4 py-8 dark:bg-gray-950">
      <div className="w-full max-w-md space-y-6">
        {/* Brand */}
        <div className="text-center">
          <Link
            to="/login"
            className="mb-3 inline-flex items-center justify-center gap-2"
            aria-label="Nexus Bank - Voltar ao login"
          >
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
          </Link>
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h1 className="mb-1 text-xl font-semibold text-gray-900 dark:text-white">
            Abra sua conta
          </h1>
          <p className="mb-6 text-sm text-gray-500 dark:text-gray-400">
            Gratis, sem taxas e em menos de 5 minutos
          </p>
          <RegisterForm />
          <p className="mt-6 text-center text-sm text-gray-500 dark:text-gray-400">
            Ja tem conta?{' '}
            <Link
              to="/login"
              className="font-medium text-primary-600 hover:underline dark:text-primary-400"
            >
              Entrar
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
