import { User } from './user.model';

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE' | 'BLOCKED';
export type TaskEnvironment = 'PRODUCTION' | 'STAGING' | 'DEVELOPMENT';
export type CanvasZone = 'FOCUS' | 'DRIFTING' | 'ORBIT';
export type ApprovalStatus = 'APPROVED' | 'PENDING_APPROVAL' | 'REJECTED';

export interface Task {
  id: number;
  title: string;
  description: string;
  status: TaskStatus;
  approvalStatus: ApprovalStatus;
  rejectionReason?: string;
  environment: TaskEnvironment;
  customFactorApplied?: boolean;
  pinged?: boolean;
  assignee: User | null;
  createdBy: User | null;
  createdAt: string;
  lastActivityAt: string;
  deadline: string;
  pingCount: number;
  dependentTaskIds: number[];
  dependencyIds: number[];
  version?: number;

  // Dynamic Gravity Score Breakdown
  totalGravityScore: number;
  dependencyScore: number;
  directBlockedCount: number;
  indirectBlockedCount: number;
  deadlineProximityScore: number;
  hoursUntilDeadline: number;
  overdue: boolean;
  stagnationScore: number;
  hoursInactive: number;
  workloadDensityScore: number;
  assigneeActiveTasksCount: number;
  customFactorName?: string;
  customFactorScore?: number;
  environmentMultiplier: number;
  canvasZone: CanvasZone;
}

export interface CreateTaskRequest {
  title: string;
  description: string;
  environment: TaskEnvironment;
  customFactorApplied?: boolean;
  assigneeId?: number;
  deadline: string;
  parentTaskIds?: number[];
}

/**
 * Shared zone determination utility matching backend GravityEngineService thresholds:
 * - FOCUS: score >= 80.0
 * - DRIFTING: score >= 50.0 and score < 80.0
 * - ORBIT: score < 50.0 (or status == DONE)
 */
export function determineCanvasZone(score: number, status?: TaskStatus): CanvasZone {
  if (status === 'DONE') {
    return 'ORBIT';
  }
  if (score >= 80.0) {
    return 'FOCUS';
  }
  if (score >= 50.0) {
    return 'DRIFTING';
  }
  return 'ORBIT';
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface TaskFilterParams {
  status?: TaskStatus;
  environment?: TaskEnvironment;
  assigneeId?: number;
  canvasZone?: CanvasZone;
  search?: string;
  sortBy?: string;
  direction?: 'ASC' | 'DESC' | 'asc' | 'desc';
  page?: number;
  size?: number;
}

