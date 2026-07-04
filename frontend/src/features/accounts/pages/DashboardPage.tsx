import { useAccounts } from '../hooks/useAccounts'
import { useStatement } from '../hooks/useStatement'
import { BalanceCard } from '../components/BalanceCard'
import { StatementList } from '../components/StatementList'
import { Skeleton } from '@/components/ui/Skeleton'
import { ErrorMessage } from '@/components/ui/ErrorMessage'
import { useAuth } from '@/hooks/useAuth'
import { Link } from 'react-router-dom'

export function DashboardPage() {
  const { user } = useAuth()
  const { data: accounts, isLoading: loadingAccounts, isError } = useAccounts()
  const primaryAccount = accounts?.[0]
  const { data: statement, isLoading: loadingStatement } = useStatement(primaryAccount?.accountId)

  if (isError) return <ErrorMessage message="Nao foi possivel carregar as contas." />

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">
          Ola{user?.name ? `, ${user.name.split(' ')[0]}` : ''}
        </h1>
        <p className="text-sm text-gray-500 dark:text-gray-400">Bem-vindo de volta</p>
      </div>

      {/* Cards de saldo */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <BalanceCard
          label="Saldo disponivel"
          balance={primaryAccount?.balance}
          currency={primaryAccount?.currency}
          isLoading={loadingAccounts}
        />
        <BalanceCard
          label="Limite Pix"
          balance={5000}
          currency="BRL"
          isLoading={loadingAccounts}
        />
        <BalanceCard
          label="Rendimento do mes"
          balance={87.32}
          currency="BRL"
          highlight="positive"
          isLoading={loadingAccounts}
        />
      </div>

      {/* Acoes rapidas */}
      <div className="flex gap-3">
        {[
          { label: 'Pix', to: '/transferencias' },
          { label: 'Transferir', to: '/transferencias' },
          { label: 'Extrato', to: '/extrato' },
        ].map(action => (
          <Link key={action.label} to={action.to}
            className="flex-1 rounded-lg border border-primary-200 bg-primary-50 py-3 text-center text-sm font-medium text-primary-700 hover:bg-primary-100 dark:border-primary-800 dark:bg-primary-950 dark:text-primary-400 transition">
            {action.label}
          </Link>
        ))}
      </div>

      {/* Ultimas movimentacoes */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">Ultimas movimentacoes</h2>
          <Link to="/extrato" className="text-xs text-primary-600 hover:underline dark:text-primary-400">
            Ver todas
          </Link>
        </div>
        {loadingAccounts ? (
          <Skeleton className="h-48 w-full" />
        ) : !primaryAccount ? (
          <div className="rounded-lg border border-dashed border-gray-300 p-8 text-center text-sm text-gray-500 dark:border-gray-700">
            Abra sua primeira conta para comecar.{' '}
            <Link to="/contas" className="text-primary-600 hover:underline">Abrir conta</Link>
          </div>
        ) : (
          <StatementList entries={statement?.content.slice(0, 5) ?? []} isLoading={loadingStatement} />
        )}
      </div>
    </div>
  )
}
