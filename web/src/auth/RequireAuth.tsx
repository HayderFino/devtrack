import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { FullPageSpinner } from '../components/ui'

export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <FullPageSpinner label="Recuperando tu sesión..." />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />

  return <Outlet />
}
