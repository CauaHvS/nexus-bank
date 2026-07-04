import { Skeleton } from '@/components/ui/Skeleton'
import type { StatementEntry } from '../types/accounts.types'

function formatCurrency(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(iso))
}

interface Props {
  entries: StatementEntry[]
  isLoading?: boolean
}

export function StatementList({ entries, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-14 w-full" />)}
      </div>
    )
  }

  if (entries.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">
        Nenhuma movimentacao no periodo.
      </div>
    )
  }

  return (
    <ul className="divide-y divide-gray-100 dark:divide-gray-800">
      {entries.map(entry => (
        <li key={entry.entryId} className="flex items-center justify-between py-3">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
              {entry.description}
            </p>
            <p className="text-xs text-gray-500 dark:text-gray-400">{formatDate(entry.occurredAt)}</p>
          </div>
          <span className={`ml-4 text-sm font-semibold ${
            entry.type === 'CREDIT' ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
          }`}>
            {entry.type === 'CREDIT' ? '+' : '-'}{formatCurrency(entry.amount)}
          </span>
        </li>
      ))}
    </ul>
  )
}
