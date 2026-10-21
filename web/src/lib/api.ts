import { clearSession, getToken, saveSession } from './session'
import type {
  AuthResponse,
  CreateProjectInput,
  CreateTaskInput,
  PageResponse,
  ProblemDetail,
  Project,
  ProjectMember,
  ProjectRole,
  Task,
  TaskFilters,
  UpdateProjectInput,
  UpdateTaskInput,
  User,
} from './types'

const BASE = '/api/v1'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `Error ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code || 'UNKNOWN'
    this.problem = problem
  }
}

type RequestOptions = {
  method?: string
  body?: unknown
  auth?: boolean
  signal?: AbortSignal
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, signal } = options
  const headers = new Headers({ Accept: 'application/json' })

  if (body !== undefined) headers.set('Content-Type', 'application/json')

  const token = getToken()
  if (auth && token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, {
      code: 'NETWORK_ERROR',
      title: 'Sin conexion',
      detail: 'No se pudo contactar con el servidor. Comprueba que la API esta levantada.',
    })
  }

  if (response.status === 401 && auth) {
    clearSession()
    window.dispatchEvent(new Event('devtrack:unauthorized'))
  }

  if (response.status === 204) return undefined as T

  const text = await response.text()
  let payload: unknown = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = null
    }
  }

  if (!response.ok) {
    const problem =
      payload && typeof payload === 'object'
        ? (payload as ProblemDetail)
        : { status: response.status, detail: `Error ${response.status}`, code: 'UNKNOWN' }
    throw new ApiError(response.status, problem)
  }

  return payload as T
}

function toQuery(filters: TaskFilters): string {
  const params = new URLSearchParams()
  filters.status?.forEach((value) => params.append('status', value))
  filters.priority?.forEach((value) => params.append('priority', value))
  if (filters.assigneeId !== undefined) params.set('assigneeId', String(filters.assigneeId))
  if (filters.unassigned) params.set('unassigned', 'true')
  if (filters.dueBefore) params.set('dueBefore', filters.dueBefore)
  if (filters.overdue) params.set('overdue', 'true')
  if (filters.q?.trim()) params.set('q', filters.q.trim())
  if (filters.page !== undefined) params.set('page', String(filters.page))
  params.set('size', String(filters.size ?? 100))
  params.set('sort', filters.sort ?? 'createdAt,desc')
  return params.toString()
}

export const api = {
  async register(input: { email: string; password: string; fullName: string }): Promise<User> {
    return request<User>('/auth/register', { method: 'POST', body: input, auth: false })
  },

  async login(input: { email: string; password: string }): Promise<AuthResponse> {
    const auth = await request<AuthResponse>('/auth/login', { method: 'POST', body: input, auth: false })
    saveSession(auth.accessToken, auth.user)
    return auth
  },

  async me(): Promise<User> {
    return request<User>('/users/me')
  },

  async listProjects(archived = 'false'): Promise<PageResponse<Project>> {
    return request<PageResponse<Project>>(`/projects?archived=${encodeURIComponent(archived)}&size=100`)
  },

  async createProject(input: CreateProjectInput): Promise<Project> {
    return request<Project>('/projects', { method: 'POST', body: input })
  },

  async getProject(projectId: number): Promise<Project> {
    return request<Project>(`/projects/${projectId}`)
  },

  async updateProject(projectId: number, input: UpdateProjectInput): Promise<Project> {
    return request<Project>(`/projects/${projectId}`, { method: 'PATCH', body: input })
  },

  async archiveProject(projectId: number): Promise<void> {
    return request<void>(`/projects/${projectId}`, { method: 'DELETE' })
  },

  async listMembers(projectId: number): Promise<ProjectMember[]> {
    const result = await request<ProjectMember[] | PageResponse<ProjectMember>>(
      `/projects/${projectId}/members`,
    )
    return Array.isArray(result) ? result : result.content
  },

  async addMember(projectId: number, userId: number, role: Exclude<ProjectRole, 'OWNER'>): Promise<ProjectMember> {
    return request<ProjectMember>(`/projects/${projectId}/members`, {
      method: 'POST',
      body: { userId, role },
    })
  },

  async changeMemberRole(
    projectId: number,
    userId: number,
    role: Exclude<ProjectRole, 'OWNER'>,
  ): Promise<ProjectMember> {
    return request<ProjectMember>(`/projects/${projectId}/members/${userId}`, {
      method: 'PATCH',
      body: { role },
    })
  },

  async removeMember(projectId: number, userId: number): Promise<void> {
    return request<void>(`/projects/${projectId}/members/${userId}`, { method: 'DELETE' })
  },

  async transferOwnership(projectId: number, userId: number): Promise<Project> {
    return request<Project>(`/projects/${projectId}/members/${userId}/ownership`, {
      method: 'PUT',
      body: { newOwnerId: userId },
    })
  },

  async listTasks(projectId: number, filters: TaskFilters = {}): Promise<PageResponse<Task>> {
    return request<PageResponse<Task>>(`/projects/${projectId}/tasks?${toQuery(filters)}`)
  },

  async getTask(projectId: number, taskId: number): Promise<Task> {
    return request<Task>(`/projects/${projectId}/tasks/${taskId}`)
  },

  async createTask(projectId: number, input: CreateTaskInput): Promise<Task> {
    return request<Task>(`/projects/${projectId}/tasks`, { method: 'POST', body: input })
  },

  async updateTask(projectId: number, taskId: number, input: UpdateTaskInput): Promise<Task> {
    return request<Task>(`/projects/${projectId}/tasks/${taskId}`, { method: 'PATCH', body: input })
  },

  async assignTask(projectId: number, taskId: number, assigneeId: number | null): Promise<Task> {
    return request<Task>(`/projects/${projectId}/tasks/${taskId}/assignee`, {
      method: 'PUT',
      body: { assigneeId },
    })
  },

  async deleteTask(projectId: number, taskId: number): Promise<void> {
    return request<void>(`/projects/${projectId}/tasks/${taskId}`, { method: 'DELETE' })
  },
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.problem.errors?.length) {
      return error.problem.errors.map((item) => item.message).join('. ')
    }
    return error.message
  }
  return error instanceof Error ? error.message : 'Ha ocurrido un error inesperado'
}
