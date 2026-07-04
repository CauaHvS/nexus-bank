import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useRegister } from '../hooks/useRegister'
import { cn } from '@/lib/utils'

// Validacao pelo algoritmo dos digitos verificadores
function isValidCpf(cpf: string): boolean {
  const d = cpf.replace(/\D/g, '')
  if (d.length !== 11 || /^(.)\1+$/.test(d)) return false
  let sum = 0
  for (let i = 0; i < 9; i++) sum += parseInt(d[i]) * (10 - i)
  let first = 11 - (sum % 11)
  if (first >= 10) first = 0
  if (first !== parseInt(d[9])) return false
  sum = 0
  for (let i = 0; i < 10; i++) sum += parseInt(d[i]) * (11 - i)
  let second = 11 - (sum % 11)
  if (second >= 10) second = 0
  return second === parseInt(d[10])
}

function getPasswordStrength(password: string): 0 | 1 | 2 | 3 | 4 {
  if (!password) return 0
  let score = 0
  if (password.length >= 8) score++
  if (password.length >= 12) score++
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score++
  if (/\d/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++
  return Math.min(4, score) as 0 | 1 | 2 | 3 | 4
}

const strengthConfig = {
  0: { bars: 0, label: '', color: '' },
  1: { bars: 1, label: 'Fraca', color: 'bg-red-500' },
  2: { bars: 2, label: 'Razoável', color: 'bg-yellow-500' },
  3: { bars: 3, label: 'Boa', color: 'bg-blue-500' },
  4: { bars: 4, label: 'Forte', color: 'bg-green-500' },
}

const schema = z
  .object({
    name: z.string().min(3, 'Nome deve ter ao menos 3 caracteres'),
    email: z.string().email('E-mail inválido'),
    cpf: z.string().refine(v => isValidCpf(v), 'CPF inválido'),
    phone: z.string().optional(),
    password: z.string().min(8, 'Senha deve ter ao menos 8 caracteres'),
    confirmPassword: z.string(),
    terms: z.boolean().refine(v => v === true, 'Você precisa aceitar os termos para continuar'),
  })
  .refine(d => d.password === d.confirmPassword, {
    message: 'As senhas não conferem',
    path: ['confirmPassword'],
  })

type FormData = z.infer<typeof schema>

const inputBase =
  'w-full rounded-md border px-3 py-2 text-sm outline-none transition focus:ring-2 disabled:opacity-60 disabled:cursor-not-allowed dark:bg-gray-800 dark:text-gray-100'
const inputNormal =
  'border-gray-300 focus:border-primary-500 focus:ring-primary-200 dark:border-gray-600'
const inputErrorCls = 'border-red-400 focus:ring-red-300 dark:border-red-600'

function Field({
  label,
  id,
  error,
  hint,
  children,
}: {
  label: string
  id: string
  error?: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1">
      <label
        htmlFor={id}
        className="block text-sm font-medium text-gray-700 dark:text-gray-300"
      >
        {label}
      </label>
      {children}
      {hint && !error && (
        <p className="text-xs text-gray-500 dark:text-gray-400">{hint}</p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-xs text-red-600 dark:text-red-400">
          {error}
        </p>
      )}
    </div>
  )
}

export function RegisterForm() {
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  const { mutate, isPending, isError, error } = useRegister()

  const passwordValue = watch('password') ?? ''
  const strength = getPasswordStrength(passwordValue)
  const strengthInfo = strengthConfig[strength]

  const onSubmit = ({ confirmPassword: _confirmPassword, terms: _terms, ...data }: FormData) =>
    mutate(data)

  const apiError = isError
    ? ((error as any)?.response?.data?.detail ?? 'Erro ao criar conta. Tente novamente.')
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

      <Field label="Nome completo" id="name" error={errors.name?.message}>
        <input
          id="name"
          type="text"
          autoComplete="name"
          placeholder="Lucas Ferreira dos Santos"
          disabled={isPending}
          aria-invalid={!!errors.name}
          aria-describedby={errors.name ? 'name-error' : undefined}
          {...register('name')}
          className={cn(inputBase, errors.name ? inputErrorCls : inputNormal)}
        />
      </Field>

      <Field
        label="CPF"
        id="cpf"
        error={errors.cpf?.message}
        hint="Somente números, sem pontos ou traços"
      >
        <input
          id="cpf"
          type="text"
          placeholder="000.000.000-00"
          maxLength={14}
          autoComplete="off"
          disabled={isPending}
          aria-invalid={!!errors.cpf}
          aria-describedby={errors.cpf ? 'cpf-error' : 'cpf-hint'}
          {...register('cpf')}
          className={cn(inputBase, errors.cpf ? inputErrorCls : inputNormal)}
        />
      </Field>

      <Field label="E-mail" id="email" error={errors.email?.message}>
        <input
          id="email"
          type="email"
          autoComplete="email"
          placeholder="lucas@email.com"
          disabled={isPending}
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'email-error' : undefined}
          {...register('email')}
          className={cn(inputBase, errors.email ? inputErrorCls : inputNormal)}
        />
      </Field>

      <Field label="Telefone (opcional)" id="phone" error={errors.phone?.message}>
        <input
          id="phone"
          type="tel"
          autoComplete="tel"
          placeholder="(11) 99999-9999"
          disabled={isPending}
          {...register('phone')}
          className={cn(inputBase, errors.phone ? inputErrorCls : inputNormal)}
        />
      </Field>

      <div className="space-y-1">
        <label
          htmlFor="password"
          className="block text-sm font-medium text-gray-700 dark:text-gray-300"
        >
          Senha
        </label>
        <div className="relative">
          <input
            id="password"
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            placeholder="Mínimo 8 caracteres"
            disabled={isPending}
            aria-invalid={!!errors.password}
            aria-describedby="password-strength"
            {...register('password')}
            className={cn(inputBase, 'pr-10', errors.password ? inputErrorCls : inputNormal)}
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
        {/* Indicador de forca da senha */}
        {passwordValue.length > 0 && (
          <div className="space-y-1">
            <div
              id="password-strength"
              role="progressbar"
              aria-label="Força da senha"
              aria-valuenow={strength}
              aria-valuemin={0}
              aria-valuemax={4}
              className="flex gap-1"
            >
              {[1, 2, 3, 4].map(i => (
                <div
                  key={i}
                  className={cn(
                    'h-1 flex-1 rounded-full transition-colors',
                    i <= strengthInfo.bars ? strengthInfo.color : 'bg-gray-200 dark:bg-gray-700',
                  )}
                />
              ))}
            </div>
            {strengthInfo.label && (
              <p className={cn(
                'text-xs',
                strength <= 1 ? 'text-red-600' :
                strength === 2 ? 'text-yellow-600' :
                strength === 3 ? 'text-blue-600' : 'text-green-600',
              )}>
                {strengthInfo.label}
              </p>
            )}
          </div>
        )}
        {errors.password && (
          <p id="password-error" className="text-xs text-red-600 dark:text-red-400">
            {errors.password.message}
          </p>
        )}
      </div>

      <div className="space-y-1">
        <label
          htmlFor="confirmPassword"
          className="block text-sm font-medium text-gray-700 dark:text-gray-300"
        >
          Confirmar senha
        </label>
        <div className="relative">
          <input
            id="confirmPassword"
            type={showConfirm ? 'text' : 'password'}
            autoComplete="new-password"
            placeholder="Repita a senha"
            disabled={isPending}
            aria-invalid={!!errors.confirmPassword}
            aria-describedby={errors.confirmPassword ? 'confirmPassword-error' : undefined}
            {...register('confirmPassword')}
            className={cn(inputBase, 'pr-10', errors.confirmPassword ? inputErrorCls : inputNormal)}
          />
          <button
            type="button"
            onClick={() => setShowConfirm(v => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            aria-label={showConfirm ? 'Ocultar confirmação de senha' : 'Mostrar confirmação de senha'}
          >
            {showConfirm ? (
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
        {errors.confirmPassword && (
          <p id="confirmPassword-error" className="text-xs text-red-600 dark:text-red-400">
            {errors.confirmPassword.message}
          </p>
        )}
      </div>

      <div className="space-y-1 pt-1">
        <div className="flex items-start gap-2">
          <input
            id="terms"
            type="checkbox"
            disabled={isPending}
            {...register('terms')}
            className="mt-0.5 h-4 w-4 shrink-0 rounded border-gray-300 accent-primary-600"
          />
          <label
            htmlFor="terms"
            className="text-sm leading-relaxed text-gray-600 dark:text-gray-400"
          >
            Li e aceito os{' '}
            <a href="#" className="font-medium text-primary-600 hover:underline dark:text-primary-400">
              Termos de Uso
            </a>{' '}
            e a{' '}
            <a href="#" className="font-medium text-primary-600 hover:underline dark:text-primary-400">
              Política de Privacidade
            </a>{' '}
            do Nexus Bank
          </label>
        </div>
        {errors.terms && (
          <p className="text-xs text-red-600 dark:text-red-400">{errors.terms.message}</p>
        )}
      </div>

      <button
        type="submit"
        disabled={isPending}
        aria-busy={isPending}
        className="mt-2 w-full rounded-md bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {isPending ? 'Criando sua conta...' : 'Criar conta'}
      </button>
    </form>
  )
}
