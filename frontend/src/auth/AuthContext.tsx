import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, api, onAuthProblem } from '../api/client'
import type { Me } from '../api/types'

interface AuthApi {
  me: Me | null
  loading: boolean
  login: (username: string, password: string) => Promise<Me>
  logout: () => Promise<void>
  refresh: () => Promise<unknown>
}

const AuthContext = createContext<AuthApi | null>(null)

async function fetchMe(): Promise<Me | null> {
  try {
    return await api.probe<Me>('/api/auth/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { data, isLoading, refetch } = useQuery({ queryKey: ['me'], queryFn: fetchMe, staleTime: 60_000, retry: false })

  // Session ended (timeout, logout elsewhere, replaced by another login) or
  // a temporary password must be changed: react centrally.
  useEffect(
    () =>
      onAuthProblem((error) => {
        if (error.code === 'PASSWORD_CHANGE_REQUIRED') {
          navigate('/change-password', { replace: true })
          return
        }
        queryClient.setQueryData(['me'], null)
        queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
        navigate('/login', { replace: true, state: { reason: error.code } })
      }),
    [navigate, queryClient],
  )

  const login = useCallback(
    async (username: string, password: string) => {
      const me = await api.post<Me>('/api/auth/login', { username, password })
      // Drop data of any previous user, but keep the live "me" query so observers stay attached
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
      queryClient.setQueryData(['me'], me)
      return me
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } finally {
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' })
      queryClient.setQueryData(['me'], null)
      navigate('/login', { replace: true })
    }
  }, [navigate, queryClient])

  const value = useMemo<AuthApi>(
    () => ({ me: data ?? null, loading: isLoading, login, logout, refresh: refetch }),
    [data, isLoading, login, logout, refetch],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthApi {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}

/** Current user; only call inside routes that require authentication. */
export function useMe(): Me {
  const { me } = useAuth()
  if (!me) throw new Error('Not authenticated')
  return me
}
