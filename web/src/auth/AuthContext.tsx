import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { api } from '../lib/api'
import { clearSession, getStoredUser, getToken, saveSession, saveUser } from '../lib/session'
import type { User } from '../lib/types'

interface AuthContextValue {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  register: (email: string, password: string, fullName: string) => Promise<User>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => getStoredUser())
  const [loading, setLoading] = useState(() => Boolean(getToken()))

  useEffect(() => {
    const token = getToken()
    if (!token) {
      setLoading(false)
      return
    }

    let active = true
    api
      .me()
      .then((freshUser) => {
        if (!active) return
        setUser(freshUser)
        saveUser(freshUser)
      })
      .catch(() => {
        if (!active) return
        clearSession()
        setUser(null)
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener('devtrack:unauthorized', onUnauthorized)
    return () => window.removeEventListener('devtrack:unauthorized', onUnauthorized)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const auth = await api.login({ email, password })
    setUser(auth.user)
    return auth.user
  }, [])

  const register = useCallback(async (email: string, password: string, fullName: string) => {
    await api.register({ email, password, fullName })
    const auth = await api.login({ email, password })
    saveSession(auth.accessToken, auth.user)
    setUser(auth.user)
    return auth.user
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return context
}
