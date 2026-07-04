import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { BottomNav } from './BottomNav'

export function AppLayout() {
  return (
    <div className="flex min-h-screen bg-gray-50 dark:bg-gray-950">
      <Sidebar />
      <main className="flex-1 px-4 py-6 pb-20 lg:pb-6 lg:px-8 max-w-5xl mx-auto w-full">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  )
}
