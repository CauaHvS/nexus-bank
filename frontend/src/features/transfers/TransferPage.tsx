import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { isAxiosError } from 'axios'
import { useAccounts } from '@/features/accounts/hooks/useAccounts'
import { Skeleton } from '@/components/ui/Skeleton'
import { useInitiateTransfer, useGetTransfer } from './useTransfers'
import type { PaymentType } from './transfersApi'

// ---------------------------------------------------------------------------
// Schema de validacao
// ---------------------------------------------------------------------------
const transferSchema = z.object({
  sourceAccountId: z.string().min(1, 'Selecione a conta de origem'),
  targetAccountId: z.string().min(1, 'Informe a conta de destino'),
  amount: z
    .number({ invalid_type_error: 'Valor invalido' })
    .positive('O valor deve ser maior que zero'),
  currency: z.string().min(1),
  paymentType: z.enum(['INTERNAL', 'PIX', 'TED']),
  description: z.string().max(140, 'Maximo 140 caracteres').optional(),
  scheduledFor: z.string().optional(),
})

type TransferFormValues = z.infer<typeof transferSchema>

// ---------------------------------------------------------------------------
// Helpers de exibicao
// ---------------------------------------------------------------------------
function formatCurrency(value: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value)
}

function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}

function paymentTypeLabel(type: PaymentType): string {
  const map: Record<PaymentType, string> = {
    INTERNAL: 'Transferencia Interna',
    PIX: 'Pix',
    TED: 'TED',
  }
  return map[type]
}

// ---------------------------------------------------------------------------
// Sub-componente: indicador de passos
// ---------------------------------------------------------------------------
interface StepBarProps {
  current: 1 | 2
}

function StepBar({ current }: StepBarProps) {
  const steps = [
    { label: 'Dados', num: 1 },
    { label: 'Confirmacao', num: 2 },
  ] as const

  return (
    <div
      className="flex items-center gap-0 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800"
      aria-label={`Progresso: Passo ${current} de 2`}
    >
      {steps.map((step, idx) => {
        const done = step.num < current
        const active = step.num === current
        return (
          <div key={step.num} className="flex items-center gap-0">
            <div className="flex items-center gap-2">
              <div
                className={[
                  'flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-bold',
                  done
                    ? 'bg-green-600 text-white'
                    : active
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-200 text-gray-500 dark:bg-gray-600 dark:text-gray-400',
                ].join(' ')}
                aria-current={active ? 'step' : undefined}
                aria-label={done ? `Passo ${step.num} concluido` : undefined}
              >
                {done ? (
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" aria-hidden="true">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                ) : (
                  step.num
                )}
              </div>
              <span
                className={[
                  'text-sm',
                  done
                    ? 'text-green-600 dark:text-green-400'
                    : active
                      ? 'font-semibold text-blue-600 dark:text-blue-400'
                      : 'text-gray-400 dark:text-gray-500',
                ].join(' ')}
              >
                {step.label}
              </span>
            </div>
            {idx < steps.length - 1 && (
              <div
                className={[
                  'mx-3 h-0.5 min-w-6 flex-1',
                  done ? 'bg-green-600' : 'bg-gray-200 dark:bg-gray-600',
                ].join(' ')}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Sub-componente: selecao do tipo de transferencia
// ---------------------------------------------------------------------------
interface TypeTabsProps {
  value: PaymentType
  onChange: (v: PaymentType) => void
}

const PAYMENT_TYPES: Array<{ value: PaymentType; label: string }> = [
  { value: 'PIX', label: 'Pix' },
  { value: 'TED', label: 'TED' },
  { value: 'INTERNAL', label: 'Interna' },
]

function TypeTabs({ value, onChange }: TypeTabsProps) {
  return (
    <div className="flex overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700" role="tablist" aria-label="Tipo de transferencia">
      {PAYMENT_TYPES.map((type) => (
        <button
          key={type.value}
          type="button"
          role="tab"
          aria-selected={value === type.value}
          onClick={() => onChange(type.value)}
          className={[
            'flex-1 py-2.5 px-4 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500',
            'not-last:border-r not-last:border-gray-200 dark:not-last:border-gray-700',
            value === type.value
              ? 'bg-blue-600 text-white font-semibold'
              : 'bg-transparent text-gray-500 hover:bg-gray-50 hover:text-gray-900 dark:hover:bg-gray-700 dark:hover:text-gray-100',
          ].join(' ')}
        >
          {type.label}
        </button>
      ))}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Sub-componente: toast inline
// ---------------------------------------------------------------------------
interface ToastProps {
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
  onClose: () => void
}

function Toast({ type, message, onClose }: ToastProps) {
  const styles: Record<string, string> = {
    success: 'bg-green-50 border-green-200 text-green-800 dark:bg-green-950 dark:border-green-800 dark:text-green-200',
    error: 'bg-red-50 border-red-200 text-red-800 dark:bg-red-950 dark:border-red-800 dark:text-red-200',
    warning: 'bg-yellow-50 border-yellow-200 text-yellow-800 dark:bg-yellow-950 dark:border-yellow-800 dark:text-yellow-200',
    info: 'bg-blue-50 border-blue-200 text-blue-800 dark:bg-blue-950 dark:border-blue-800 dark:text-blue-200',
  }

  return (
    <div
      role="alert"
      aria-live="assertive"
      className={`flex items-start gap-3 rounded-lg border p-4 ${styles[type]}`}
    >
      <span className="flex-1 text-sm">{message}</span>
      <button
        type="button"
        onClick={onClose}
        className="shrink-0 text-current opacity-60 hover:opacity-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-current"
        aria-label="Fechar notificacao"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Componente principal
// ---------------------------------------------------------------------------
export function TransferPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<1 | 2>(1)
  const [pendingTransferId, setPendingTransferId] = useState<string | null>(null)
  const [toast, setToast] = useState<{ type: ToastProps['type']; message: string } | null>(null)
  const [formSnapshot, setFormSnapshot] = useState<TransferFormValues | null>(null)

  const { data: accounts, isLoading: loadingAccounts } = useAccounts()
  const initiateMutation = useInitiateTransfer()
  const { data: polledTransfer } = useGetTransfer(pendingTransferId)

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<TransferFormValues>({
    resolver: zodResolver(transferSchema),
    defaultValues: {
      sourceAccountId: '',
      targetAccountId: '',
      amount: undefined as unknown as number,
      currency: 'BRL',
      paymentType: 'PIX' as const,
      description: '',
      scheduledFor: '',
    },
  })

  const paymentType = watch('paymentType')
  const sourceAccountId = watch('sourceAccountId')
  const selectedAccount = accounts?.find((a) => a.accountId === sourceAccountId)

  // Polling: reage ao status da transferencia
  useEffect(() => {
    if (!polledTransfer || !pendingTransferId) return

    if (polledTransfer.status === 'COMPLETED') {
      setPendingTransferId(null)
      const isScheduled = !!polledTransfer.scheduledFor
      setToast({
        type: 'success',
        message: isScheduled
          ? `Transferencia agendada para ${formatDateTime(polledTransfer.scheduledFor!)}`
          : 'Transferencia realizada com sucesso!',
      })
      setTimeout(() => navigate('/extrato'), 2500)
    } else if (polledTransfer.status === 'FAILED' || polledTransfer.status === 'COMPENSATION_FAILED') {
      setPendingTransferId(null)
      setToast({
        type: 'error',
        message: polledTransfer.failureReason ?? 'Transferencia falhou. Tente novamente.',
      })
    }
  }, [polledTransfer, pendingTransferId, navigate])

  function resolveErrorToast(status: number): string {
    if (status === 409) return 'Transferencia ja processada anteriormente.'
    if (status === 403) return 'Voce nao tem permissao sobre essa conta.'
    if (status === 422) return 'Saldo insuficiente.'
    if (status === 429) return 'Muitas requisicoes. Aguarde e tente novamente.'
    return 'Erro ao realizar transferencia.'
  }

  // Passo 1 -> 2: apenas valida e avanca
  function onReview(values: TransferFormValues) {
    setFormSnapshot(values)
    setStep(2)
  }

  // Passo 2: confirma e envia
  async function onConfirm() {
    if (!formSnapshot) return

    try {
      const result = await initiateMutation.mutateAsync(formSnapshot)

      // Se ja completou na criacao (synchronous path)
      if (result.status === 'COMPLETED') {
        const isScheduled = !!result.scheduledFor
        setToast({
          type: 'success',
          message: isScheduled
            ? `Transferencia agendada para ${formatDateTime(result.scheduledFor!)}`
            : 'Transferencia realizada com sucesso!',
        })
        setTimeout(() => navigate('/extrato'), 2500)
        return
      }

      // Status PENDING -> inicia polling
      if (result.status === 'PENDING') {
        setPendingTransferId(result.transferId)
        return
      }

      // SCHEDULED
      if (result.status === 'SCHEDULED') {
        setToast({
          type: 'success',
          message: `Transferencia agendada para ${formatDateTime(result.scheduledFor!)}`,
        })
        setTimeout(() => navigate('/extrato'), 2500)
      }
    } catch (err) {
      if (isAxiosError(err)) {
        const status = err.response?.status ?? 0
        setToast({ type: 'error', message: resolveErrorToast(status) })
      } else {
        setToast({ type: 'error', message: 'Erro ao realizar transferencia.' })
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Render: loading de contas
  // ---------------------------------------------------------------------------
  if (loadingAccounts) {
    return (
      <div className="mx-auto max-w-xl space-y-4">
        <Skeleton className="h-14 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  // ---------------------------------------------------------------------------
  // Render: processando (polling ativo)
  // ---------------------------------------------------------------------------
  if (pendingTransferId) {
    return (
      <div className="mx-auto max-w-xl">
        <div
          className="flex flex-col items-center gap-4 rounded-xl border border-gray-200 bg-white p-10 text-center dark:border-gray-700 dark:bg-gray-800"
          aria-live="polite"
          aria-busy="true"
        >
          <div className="h-12 w-12 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" role="status" aria-label="Processando" />
          <p className="text-lg font-semibold text-gray-900 dark:text-gray-100">Processando transferencia...</p>
          <p className="text-sm text-gray-500 dark:text-gray-400">Aguarde enquanto confirmamos sua operacao.</p>
        </div>
      </div>
    )
  }

  // ---------------------------------------------------------------------------
  // Render: passo 1 — dados da transferencia
  // ---------------------------------------------------------------------------
  const targetLabel =
    paymentType === 'PIX'
      ? 'Chave Pix (CPF, e-mail, telefone ou chave aleatoria)'
      : paymentType === 'TED'
        ? 'Numero da conta de destino'
        : 'ID da conta de destino'

  return (
    <div className="mx-auto max-w-xl space-y-4">
      {/* Toast */}
      {toast && (
        <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
      )}

      {/* Step bar */}
      <StepBar current={step} />

      {/* Passo 1 */}
      {step === 1 && (
        <form
          onSubmit={handleSubmit(onReview)}
          className="space-y-5 rounded-xl border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800"
          noValidate
        >
          <div>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Dados da transferencia</h2>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Preencha as informacoes para continuar</p>
          </div>

          {/* Conta de origem */}
          <div className="space-y-1">
            <label htmlFor="sourceAccountId" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              Conta de origem
            </label>
            {accounts && accounts.length > 0 ? (
              <select
                id="sourceAccountId"
                {...register('sourceAccountId')}
                className={[
                  'w-full rounded-lg border px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500',
                  'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100',
                  errors.sourceAccountId
                    ? 'border-red-400 dark:border-red-600'
                    : 'border-gray-300 dark:border-gray-600',
                ].join(' ')}
                aria-invalid={!!errors.sourceAccountId}
                aria-describedby={errors.sourceAccountId ? 'sourceAccountId-error' : undefined}
              >
                <option value="">Selecione uma conta</option>
                {accounts.map((acc) => (
                  <option key={acc.accountId} value={acc.accountId}>
                    {acc.type === 'CHECKING' ? 'Conta Corrente' : 'Conta Poupanca'} — Ag. {acc.agency} / {acc.accountNumber} ({formatCurrency(acc.balance)})
                  </option>
                ))}
              </select>
            ) : (
              <div className="rounded-lg border border-dashed border-gray-300 p-4 text-center text-sm text-gray-500 dark:border-gray-600 dark:text-gray-400">
                Nenhuma conta ativa encontrada.
              </div>
            )}
            {errors.sourceAccountId && (
              <p id="sourceAccountId-error" role="alert" className="text-xs text-red-600 dark:text-red-400">
                {errors.sourceAccountId.message}
              </p>
            )}
            {selectedAccount && (
              <p className="text-xs text-gray-400 dark:text-gray-500">
                Saldo disponivel: {formatCurrency(selectedAccount.balance)}
              </p>
            )}
          </div>

          {/* Tipo de transferencia */}
          <div className="space-y-1">
            <span className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              Tipo de transferencia
            </span>
            <TypeTabs
              value={paymentType}
              onChange={(v) => setValue('paymentType', v, { shouldValidate: true })}
            />
          </div>

          {/* Conta de destino */}
          <div className="space-y-1">
            <label htmlFor="targetAccountId" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              {targetLabel}
            </label>
            <input
              id="targetAccountId"
              type="text"
              {...register('targetAccountId')}
              placeholder={
                paymentType === 'PIX'
                  ? 'Ex: 123.456.789-00 ou email@exemplo.com'
                  : 'Ex: 00001-2'
              }
              className={[
                'w-full rounded-lg border px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500',
                'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400',
                errors.targetAccountId
                  ? 'border-red-400 dark:border-red-600'
                  : 'border-gray-300 dark:border-gray-600',
              ].join(' ')}
              aria-invalid={!!errors.targetAccountId}
              aria-describedby={errors.targetAccountId ? 'targetAccountId-error' : undefined}
            />
            {errors.targetAccountId && (
              <p id="targetAccountId-error" role="alert" className="text-xs text-red-600 dark:text-red-400">
                {errors.targetAccountId.message}
              </p>
            )}
          </div>

          {/* Valor */}
          <div className="space-y-1">
            <label htmlFor="amount" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              Valor (R$)
            </label>
            <div className="relative">
              <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm font-semibold text-gray-500 dark:text-gray-400">
                R$
              </span>
              <input
                id="amount"
                type="number"
                min="0.01"
                step="0.01"
                {...register('amount', { valueAsNumber: true })}
                placeholder="0,00"
                className={[
                  'w-full rounded-lg border py-2.5 pl-10 pr-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500',
                  'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400',
                  errors.amount
                    ? 'border-red-400 dark:border-red-600'
                    : 'border-gray-300 dark:border-gray-600',
                ].join(' ')}
                aria-invalid={!!errors.amount}
                aria-describedby={errors.amount ? 'amount-error' : undefined}
              />
            </div>
            {errors.amount && (
              <p id="amount-error" role="alert" className="text-xs text-red-600 dark:text-red-400">
                {errors.amount.message}
              </p>
            )}
          </div>

          {/* Descricao */}
          <div className="space-y-1">
            <label htmlFor="description" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              Descricao <span className="text-gray-400 font-normal">(opcional)</span>
            </label>
            <input
              id="description"
              type="text"
              maxLength={140}
              {...register('description')}
              placeholder="Ex: Aluguel julho, Divisao de conta..."
              className={[
                'w-full rounded-lg border px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500',
                'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400',
                errors.description
                  ? 'border-red-400 dark:border-red-600'
                  : 'border-gray-300 dark:border-gray-600',
              ].join(' ')}
              aria-invalid={!!errors.description}
              aria-describedby={errors.description ? 'description-error' : undefined}
            />
            {errors.description && (
              <p id="description-error" role="alert" className="text-xs text-red-600 dark:text-red-400">
                {errors.description.message}
              </p>
            )}
          </div>

          {/* Agendamento */}
          <div className="space-y-1">
            <label htmlFor="scheduledFor" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
              Agendar para <span className="text-gray-400 font-normal">(opcional)</span>
            </label>
            <input
              id="scheduledFor"
              type="datetime-local"
              {...register('scheduledFor')}
              className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
            />
            <p className="text-xs text-gray-400 dark:text-gray-500">
              Deixe em branco para enviar imediatamente.
            </p>
          </div>

          {/* Botao */}
          <button
            type="submit"
            className="w-full rounded-lg bg-blue-600 py-3 text-sm font-semibold text-white transition hover:bg-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
          >
            Revisar transferencia
          </button>
        </form>
      )}

      {/* Passo 2 — confirmacao */}
      {step === 2 && formSnapshot && (
        <div className="space-y-5 rounded-xl border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
          <div>
            <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">Confirme a transferencia</h2>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Verifique todos os dados antes de confirmar</p>
          </div>

          {/* Resumo */}
          <div className="rounded-lg border border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900">
            {[
              {
                label: 'De (conta)',
                value: (() => {
                  const acc = accounts?.find((a) => a.accountId === formSnapshot.sourceAccountId)
                  return acc
                    ? `${acc.type === 'CHECKING' ? 'Conta Corrente' : 'Conta Poupanca'} — Ag. ${acc.agency} / ${acc.accountNumber}`
                    : formSnapshot.sourceAccountId
                })(),
              },
              { label: 'Para', value: formSnapshot.targetAccountId },
              { label: 'Tipo', value: paymentTypeLabel(formSnapshot.paymentType) },
              { label: 'Descricao', value: formSnapshot.description || '—' },
              {
                label: 'Data',
                value: formSnapshot.scheduledFor
                  ? `Agendado: ${formatDateTime(formSnapshot.scheduledFor)}`
                  : 'Imediato',
              },
            ].map((row) => (
              <div
                key={row.label}
                className="flex items-center justify-between border-b border-gray-200 px-4 py-3 last:border-b-0 dark:border-gray-700"
              >
                <span className="text-sm text-gray-500 dark:text-gray-400">{row.label}</span>
                <span className="text-sm font-medium text-gray-900 dark:text-gray-100">{row.value}</span>
              </div>
            ))}
            <div className="flex items-center justify-between border-t-2 border-gray-300 px-4 py-3 dark:border-gray-600">
              <span className="text-sm text-gray-500 dark:text-gray-400">Valor</span>
              <span className="text-xl font-bold text-red-600 dark:text-red-400">
                {formatCurrency(formSnapshot.amount)}
              </span>
            </div>
          </div>

          {/* Aviso sobre irreversibilidade */}
          <div
            role="note"
            className="flex items-start gap-3 rounded-lg border border-yellow-200 bg-yellow-50 p-4 dark:border-yellow-800 dark:bg-yellow-950"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              className="mt-0.5 shrink-0 text-yellow-700 dark:text-yellow-400"
              aria-hidden="true"
            >
              <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
              <line x1="12" y1="9" x2="12" y2="13" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
            <p className="text-sm text-yellow-800 dark:text-yellow-300">
              {formSnapshot.paymentType === 'PIX'
                ? 'Transferencias via Pix sao instantaneas e nao podem ser canceladas apos a confirmacao.'
                : 'Confirme os dados com atencao antes de prosseguir.'}
            </p>
          </div>

          {/* Botoes */}
          <div className="flex gap-3">
            <button
              type="button"
              onClick={() => setStep(1)}
              disabled={initiateMutation.isPending}
              className="flex-1 rounded-lg border border-gray-300 py-3 text-sm font-semibold text-gray-700 transition hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 disabled:opacity-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            >
              Voltar
            </button>
            <button
              type="button"
              onClick={onConfirm}
              disabled={initiateMutation.isPending}
              className="flex-[2] rounded-lg bg-blue-600 py-3 text-sm font-semibold text-white transition hover:bg-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
              aria-busy={initiateMutation.isPending}
            >
              {initiateMutation.isPending ? (
                <span className="flex items-center justify-center gap-2" aria-live="polite">
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" aria-hidden="true" />
                  Enviando...
                </span>
              ) : (
                'Confirmar transferencia'
              )}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
