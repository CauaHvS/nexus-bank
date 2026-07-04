import { useState } from 'react'
import { Skeleton } from '@/components/ui/Skeleton'
import { cn } from '@/lib/utils'

interface Props {
  label: string
  balance?: number
  currency?: string
  isLoading?: boolean
  highlight?: 'positive' | 'neutral'
}

function formatCurrency(value: number, currency = 'BRL') {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(value)
}

export function BalanceCard({ label, balance, currency, isLoading, highlight }: Props) {
  const [hidden, setHidden] = useState(false)

  if (isLoading) return <Skeleton className="h-24 w-full rounded-lg" />

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-900">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
          {label}
        </span>
        <button
          onClick={() => setHidden(h => !h)}
          className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
          aria-label={hidden ? 'Mostrar saldo' : 'Ocultar saldo'}
        >
          {hidden ? 'mostrar' : 'ocultar'}
        </button>
      </div>
      <p className={cn(
        'mt-2 text-2xl font-bold',
        highlight === 'positive' ? 'text-green-600 dark:text-green-400' : 'text-gray-900 dark:text-gray-100',
      )}>
        {hidden ? '••••••' : formatCurrency(balance ?? 0, currency)}
      </p>
    </div>
  )
}
