import type { User } from './types'

const TOKEN_KEY = 'devtrack.token'
const USER_KEY = 'devtrack.user'

let memoryToken: string | null = sessionStorage.getItem(TOKEN_KEY)

export function getToken(): string | null {
  return memoryToken
}

export function getStoredUser(): User | null {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export function saveSession(token: string, user: User): void {
  memoryToken = token
  sessionStorage.setItem(TOKEN_KEY, token)
  sessionStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function saveUser(user: User): void {
  sessionStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession(): void {
  memoryToken = null
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
}
