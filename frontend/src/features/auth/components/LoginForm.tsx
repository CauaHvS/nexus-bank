import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useLogin } from '../hooks/useLogin'
import { cn } from '@/lib/utils'

const schema = z.object({
  email: z.string().email('E-mail inválido'),
  password: z.string().min(1, 'Informe a senha'),
})
type FormData = z.infer<typeof schema>

const inputBase =
  'w-full rounded-md border px-3 py-2 text-sm outline-none transition focus:ring-2 disabled:opacity-60 disabled:cursor-not-allowed dark:bg-gray-800 dark:text-gray-100'
const inputNormal =
  'border-gray-300 focus:border-primary-500 focus:ring-primary-200 dark:border-gray-600'
const inputError = 'border-red-400 focus:ring-red-300 dark:border-red-600'

export function LoginForm() {
  const [showPassword, setShowPassword] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const { mutate, isPending, isError, error } = useLogin()

  const onSubmit = (data: FormData) => mutate(data)

  const apiError = isError
    ? ((error as any)?.response?.data?.detail ?? 'E-mail ou senha incorretos.')
    : null

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
      {apiError && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300"
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            className="mt-0.5 shrink-0"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          {apiError}
        </div>
      )}

      <div className="space-y-1">
        <label
          htmlFor="email"
          className="block text-sm font-medium text-gray-700 dark:text-gray-300"
        >
          E-mail
        </label>
        <input
          id="email"
          type="email"
          autoComplete="email"
          placeholder="seu@email.com"
          disabled={isPending}
          {...register('email')}
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'email-error' : undefined}
          className={cn(inputBase, errors.email ? inputError : inputNormal)}
        />
        {errors.email && (
          <p id="email-error" className="text-xs text-red-600 dark:text-red-400">
            {errors.email.message}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <div className="flex items-center justify-between">
          <label
            htmlFor="password"
            className="block text-sm font-medium text-gray-700 dark:text-gray-300"
          >
            Senha
          </label>
          <a
            href="#"
            className="text-xs font-medium text-primary-600 hover:underline dark:text-primary-400"
          >
            Esqueceu a senha?
          </a>
        </div>
        <div className="relative">
          <input
            id="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            placeholder="Sua senha"
            disabled={isPending}
            {...register('password')}
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'password-error' : undefined}
            className={cn(inputBase, 'pr-10', errors.password ? inputError : inputNormal)}
          />
          <button
            type="button"
            onClick={() => setShowPassword(v => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
          >
            {showPassword ? (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                <line x1="1" y1="1" x2="23" y2="23" />
              </svg>
            ) : (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
            )}
          </button>
        </div>
        {errors.password && (
          <p id="password-error" className="text-xs text-red-600 dark:text-red-400">
            {errors.password.message}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={isPending}
        aria-busy={isPending}
        className="mt-2 w-full rounded-md bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {isPending ? 'Autenticando...' : 'Entrar'}
      </button>
    </form>
  )
}
