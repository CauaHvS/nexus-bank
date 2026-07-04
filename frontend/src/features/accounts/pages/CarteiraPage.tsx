import { useState } from 'react'
import { useAccounts } from '../hooks/useAccounts'
import { Skeleton } from '@/components/ui/Skeleton'

function formatCurrency(v: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)
}

export function CarteiraPage() {
  const { data: accounts, isLoading } = useAccounts()
  const [hidden, setHidden] = useState(false)
  const primary = accounts?.[0]

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Carteira</h1>

      {isLoading ? (
        <Skeleton className="h-40 w-full rounded-lg" />
      ) : primary ? (
        <div className="rounded-xl border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-900">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                Ag {primary.agency} / Cc {primary.accountNumber}
              </p>
              <p className="mt-0.5 text-sm font-medium text-gray-700 dark:text-gray-300">
                {primary.type === 'CHECKING' ? 'Conta Corrente' : 'Conta Poupanca'}
              </p>
            </div>
            <button
              onClick={() => setHidden(h => !h)}
              className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
              aria-label={hidden ? 'Mostrar saldo' : 'Ocultar saldo'}
            >
              {hidden ? 'mostrar' : 'ocultar'}
            </button>
          </div>
          <p className="mt-4 text-3xl font-bold text-gray-900 dark:text-gray-100">
            {hidden ? '••••••' : formatCurrency(primary.balance)}
          </p>
          <button
            onClick={() => navigator.clipboard?.writeText(primary.accountNumber)}
            className="mt-4 rounded-md border border-primary-300 px-3 py-1.5 text-xs font-medium text-primary-700 hover:bg-primary-50 dark:border-primary-700 dark:text-primary-400 transition"
          >
            Copiar numero da conta
          </button>
        </div>
      ) : (
        <div className="rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700">
          Nenhuma conta encontrada.
        </div>
      )}

      {/* Chaves Pix (placeholder -- sera implementado na Fase 3) */}
      <div>
        <h2 className="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">Chaves Pix</h2>
        <div className="rounded-lg border border-dashed border-gray-300 p-6 text-center text-sm text-gray-500 dark:border-gray-700">
          Gerenciamento de chaves Pix disponivel na Fase 3.
        </div>
      </div>
    </div>
  )
}
