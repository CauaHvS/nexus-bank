import { useEffect, useState } from 'react'

export function Header() {
  const [dark, setDark] = useState(() =>
    document.documentElement.classList.contains('dark'),
  )

  useEffect(() => {
    if (dark) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }, [dark])

  return (
    <header className="border-b border-gray-200 bg-white px-6 py-4 dark:border-gray-800 dark:bg-gray-900">
      <div className="flex items-center justify-between">
        <span className="text-lg font-semibold text-primary-700 dark:text-primary-400">
          Nexus Bank
        </span>
        <button
          onClick={() => setDark((d) => !d)}
          className="rounded-md px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
        >
          {dark ? 'Modo claro' : 'Modo escuro'}
        </button>
      </div>
    </header>
  )
}
