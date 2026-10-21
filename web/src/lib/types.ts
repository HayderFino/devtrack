export type PlatformRole = 'USER' | 'ADMIN'
export type ProjectRole = 'OWNER' | 'EDITOR' | 'VIEWER'
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface User {
  id: number
  email: string
  fullName: string
  platformRole: PlatformRole
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface Project {
  id: number
  name: string
  description?: string
  ownerId: number
  archived: boolean
  role: ProjectRole
  memberCount: number
  createdAt: string
  updatedAt: string
}

export interface ProjectMember {
  userId: number
  email: string
  fullName: string
  role: ProjectRole
  addedAt: string
}

export interface Task {
  id: number
  projectId: number
  title: string
  description?: string
  status: TaskStatus
  priority: TaskPriority
  assigneeId?: number
  assigneeName?: string
  dueDate?: string
  createdById?: number
  createdByName?: string
  overdue: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface FieldError {
  field: string
  message: string
  rejectedValue?: unknown
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  timestamp?: string
  errors?: FieldError[]
}

export interface CreateProjectInput {
  name: string
  description?: string
}

export interface UpdateProjectInput {
  name?: string
  description?: string
}

export interface CreateTaskInput {
  title: string
  description?: string
  status?: TaskStatus
  priority?: TaskPriority
  assigneeId?: number
  dueDate?: string
}

export interface UpdateTaskInput {
  title?: string
  description?: string
  status?: TaskStatus
  priority?: TaskPriority
  dueDate?: string
}

export interface TaskFilters {
  status?: TaskStatus[]
  priority?: TaskPriority[]
  assigneeId?: number
  unassigned?: boolean
  dueBefore?: string
  overdue?: boolean
  q?: string
  page?: number
  size?: number
  sort?: string
}

export const TASK_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED']
export const TASK_PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

export const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'Pendiente',
  IN_PROGRESS: 'En curso',
  DONE: 'Hecha',
  CANCELLED: 'Cancelada',
}

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
  URGENT: 'Urgente',
}

export const ROLE_LABELS: Record<ProjectRole, string> = {
  OWNER: 'Propietario',
  EDITOR: 'Editor',
  VIEWER: 'Lector',
}
