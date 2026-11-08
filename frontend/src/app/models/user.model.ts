export type UserRole = 'ROLE_PROJECT_MANAGER' | 'ROLE_TEAM_LEAD' | 'ROLE_TEAM_MEMBER' | 'ROLE_TESTER';

export interface User {
  id: number;
  username: string;
  fullName: string;
  role: UserRole | string;
  employeeNo?: string;
  department?: string;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  fullName: string;
  role: string;
  employeeNo: string;
  department: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
