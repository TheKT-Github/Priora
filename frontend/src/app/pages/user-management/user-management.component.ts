import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserService } from '../../services/user.service';
import { AuthService } from '../../services/auth.service';
import { CreateUserRequest } from '../../models/user.model';

/**
 * Project Manager User Management Page (`/users`).
 * Provision accounts for Project Managers, Team Leads, Developers, Testers, and Custom Roles.
 */
@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-7xl mx-auto px-6 py-8 space-y-6">
      
      <!-- Section Header -->
      <div class="flex items-center justify-between border-b border-slate-200 pb-4">
        <div>
          <h2 class="text-xl font-bold text-slate-900 tracking-tight">Project Manager User Provisioning</h2>
          <p class="text-xs text-slate-500 font-normal mt-0.5">
            Provision accounts with OWASP production-grade validation logic for Project Managers, Team Leads, Developers, and Testers.
          </p>
        </div>
        <span class="text-xs font-semibold bg-purple-50 text-purple-800 border border-purple-200 px-3 py-1 rounded-full">
          {{ userService.users().length }} System Accounts
        </span>
      </div>

      <!-- Unauthorized Warning for Non-Project Managers -->
      <div *ngIf="!authService.isProjectManager()" class="bg-amber-50 border border-amber-200 text-amber-900 p-6 rounded-2xl text-center space-y-2">
        <h3 class="text-sm font-bold">Project Manager Permission Required</h3>
        <p class="text-xs text-amber-700">Only Project Managers have authorization to provision new user credentials.</p>
      </div>

      <ng-container *ngIf="authService.isProjectManager()">
        
        <!-- Global Success Banner -->
        <div *ngIf="userService.successMessage()" 
             class="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-xl text-xs font-semibold shadow-sm flex items-center gap-2">
          <svg class="w-4 h-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          {{ userService.successMessage() }}
        </div>

        <!-- Global Error Banner -->
        <div *ngIf="globalErrorMessage" 
             class="bg-rose-50 border border-rose-200 text-rose-800 px-4 py-3 rounded-xl text-xs font-semibold shadow-sm flex items-center gap-2">
          <svg class="w-4 h-4 text-rose-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          Backend Validation Alert: {{ globalErrorMessage }}
        </div>

        <!-- Account Creation Form with OWASP Validation -->
        <div class="bg-white border border-slate-200 rounded-2xl p-6 shadow-sm space-y-4">
          <div>
            <h3 class="text-base font-bold text-slate-900">Provision User Account (OWASP Validated)</h3>
            <p class="text-xs text-slate-500 font-normal mt-0.5">
              Fill in all user account details. Specific validation errors display directly under their respective input fields.
            </p>
          </div>

          <form (ngSubmit)="onCreateUser()" class="space-y-5 pt-2">
            
            <!-- Row 1: Full Name, Username, Initial Password -->
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-5">
              
              <!-- Full Name Field -->
              <div>
                <div class="flex items-center justify-between mb-1">
                  <label class="block text-xs font-semibold text-slate-700">Full Name *</label>
                  <span class="text-[10px] text-slate-400 font-medium">2-70 chars (Letters, -, ')</span>
                </div>
                <input type="text" [(ngModel)]="fullName" (input)="onFullNameInput()" name="fullName" required
                       placeholder="e.g. Sarah Jenkins or Jean-Luc O'Connor"
                       [class.border-rose-400]="fullNameError"
                       [class.bg-rose-50]="fullNameError"
                       class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white transition-all">
                
                <span *ngIf="fullNameError" class="text-[11px] text-rose-600 font-semibold flex items-center gap-1 mt-1">
                  <svg class="w-3 h-3 text-rose-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {{ fullNameError }}
                </span>
              </div>

              <!-- Username Field -->
              <div>
                <div class="flex items-center justify-between mb-1">
                  <label class="block text-xs font-semibold text-slate-700">Username *</label>
                  <span class="text-[10px] text-slate-400 font-medium">3-30 chars (No reserved keywords)</span>
                </div>
                <input type="text" [(ngModel)]="username" (input)="onUsernameInput()" name="username" required
                       placeholder="e.g. sjenkins or alex.vance"
                       [class.border-rose-400]="usernameError"
                       [class.bg-rose-50]="usernameError"
                       class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white transition-all font-mono">
                
                <span *ngIf="usernameError" class="text-[11px] text-rose-600 font-semibold flex items-center gap-1 mt-1">
                  <svg class="w-3 h-3 text-rose-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {{ usernameError }}
                </span>
              </div>

              <!-- Initial Password Field -->
              <div>
                <div class="flex items-center justify-between mb-1">
                  <label class="block text-xs font-semibold text-slate-700">Initial Password *</label>
                  <span class="text-[10px] text-slate-400 font-medium">Min 8-12 chars (Max 128)</span>
                </div>
                <input type="text" [(ngModel)]="password" (input)="onFieldInput('password')" name="password" required
                       placeholder="e.g. Password123!"
                       [class.border-rose-400]="passwordError"
                       [class.bg-rose-50]="passwordError"
                       class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white transition-all font-mono">
                
                <!-- Password OWASP Requirement Checklist Badges -->
                <div class="flex flex-wrap items-center gap-1 mt-1.5 text-[9px]">
                  <span [class]="hasMinLength ? 'bg-emerald-100 text-emerald-800 border-emerald-300 font-bold' : 'bg-slate-100 text-slate-500 border-slate-200'" class="px-1.5 py-0.5 rounded border">
                    8-128 chars
                  </span>
                  <span [class]="hasUppercase ? 'bg-emerald-100 text-emerald-800 border-emerald-300 font-bold' : 'bg-slate-100 text-slate-500 border-slate-200'" class="px-1.5 py-0.5 rounded border">
                    1 Uppercase (A-Z)
                  </span>
                  <span [class]="hasLowercase ? 'bg-emerald-100 text-emerald-800 border-emerald-300 font-bold' : 'bg-slate-100 text-slate-500 border-slate-200'" class="px-1.5 py-0.5 rounded border">
                    1 Lowercase (a-z)
                  </span>
                  <span [class]="hasNumber ? 'bg-emerald-100 text-emerald-800 border-emerald-300 font-bold' : 'bg-slate-100 text-slate-500 border-slate-200'" class="px-1.5 py-0.5 rounded border">
                    1 Number (0-9)
                  </span>
                  <span [class]="hasSpecial ? 'bg-emerald-100 text-emerald-800 border-emerald-300 font-bold' : 'bg-slate-100 text-slate-500 border-slate-200'" class="px-1.5 py-0.5 rounded border">
                    1 Special/Space
                  </span>
                </div>

                <span *ngIf="passwordError" class="text-[11px] text-rose-600 font-semibold flex items-center gap-1 mt-1">
                  <svg class="w-3 h-3 text-rose-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {{ passwordError }}
                </span>
              </div>

            </div>

            <!-- Row 2: Employee Number, Role Dropdown, Department Dropdown -->
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-5">
              
              <!-- Employee Number Field -->
              <div>
                <div class="flex items-center justify-between mb-1">
                  <label class="block text-xs font-semibold text-slate-700">Employee Number *</label>
                  <span class="text-[10px] text-slate-400 font-medium">Format: EMP-[Number]</span>
                </div>
                
                <div class="flex items-center rounded-xl border border-slate-200 bg-slate-50 overflow-hidden focus-within:border-indigo-600 focus-within:bg-white"
                     [class.border-rose-400]="employeeNoError"
                     [class.bg-rose-50]="employeeNoError">
                  <span class="bg-slate-200/80 text-slate-700 font-bold text-xs px-3 py-2 border-r border-slate-300 select-none">
                    EMP-
                  </span>
                  <input type="text" [(ngModel)]="employeeNumDigits" (input)="onEmployeeNumInput()" name="employeeNumDigits" required
                         placeholder="301"
                         class="w-full bg-transparent px-3 py-2 text-xs text-slate-900 focus:outline-none font-mono">
                </div>

                <span *ngIf="employeeNoError" class="text-[11px] text-rose-600 font-semibold flex items-center gap-1 mt-1">
                  <svg class="w-3 h-3 text-rose-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {{ employeeNoError }}
                </span>
              </div>

              <!-- Role Selection Dropdown (Strictly Whitelisted: Project Manager, Team Lead, Developer, Tester) -->
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">Assigned Role *</label>
                <select [(ngModel)]="roleSelect" (change)="onRoleChange()" name="roleSelect" required
                        class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
                  <option value="ROLE_PROJECT_MANAGER">Project Manager</option>
                  <option value="ROLE_TEAM_LEAD">Team Lead</option>
                  <option value="ROLE_TEAM_MEMBER">Developer / Team Member</option>
                  <option value="ROLE_TESTER">Tester</option>
                </select>
              </div>

              <!-- Department Selection Dropdown -->
              <div>
                <label class="block text-xs font-semibold text-slate-700 mb-1">Department *</label>
                <select [(ngModel)]="departmentSelect" (change)="onDepartmentChange()" name="departmentSelect" required
                        class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
                  <option value="Software Engineering">Software Engineering</option>
                  <option value="Frontend Infrastructure">Frontend Infrastructure</option>
                  <option value="IT Administration">IT Administration</option>
                  <option value="Quality Assurance">Quality Assurance</option>
                  <option value="Product Management">Product Management</option>
                  <option value="OTHER">Other Department...</option>
                </select>
              </div>

            </div>

            <!-- Dynamic Custom Input Label for "Other" Department Selection -->
            <div class="grid grid-cols-1 gap-4" *ngIf="departmentSelect === 'OTHER'">
              
              <!-- Custom Department Input Label -->
              <div class="bg-indigo-50/60 border border-indigo-200 p-3 rounded-xl space-y-1">
                <label class="block text-xs font-bold text-indigo-900">Custom Department Name *</label>
                <input type="text" [(ngModel)]="customDepartment" (input)="onFieldInput('customDepartment')" name="customDepartment"
                       placeholder="e.g. Cloud Operations & Security"
                       [class.border-rose-400]="customDepartmentError"
                       class="w-full bg-white border border-indigo-200 rounded-lg px-3 py-1.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600">
                <span *ngIf="customDepartmentError" class="text-[11px] text-rose-600 font-semibold flex items-center gap-1 mt-1">
                  <svg class="w-3 h-3 text-rose-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  {{ customDepartmentError }}
                </span>
              </div>

            </div>

            <!-- Submit Button -->
            <div class="flex items-center justify-end pt-2">
              <button type="submit" [disabled]="!isFormValid || userService.loading()"
                      class="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-sm transition-all flex items-center gap-2">
                <span *ngIf="userService.loading()" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                Create User Account
              </button>
            </div>

          </form>
        </div>

        <!-- System Users Directory Table -->
        <div class="bg-white border border-slate-200 rounded-2xl p-6 shadow-sm space-y-4">
          <h3 class="text-base font-bold text-slate-900">System Users Directory</h3>

          <div class="overflow-x-auto">
            <table class="w-full text-left text-xs border-collapse">
              <thead>
                <tr class="border-b border-slate-200 bg-slate-50/60 text-slate-500 uppercase text-[10px] font-bold">
                  <th class="p-3">User</th>
                  <th class="p-3">Username</th>
                  <th class="p-3">Employee No</th>
                  <th class="p-3">Department</th>
                  <th class="p-3">Assigned Role</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr *ngFor="let user of userService.users()" class="hover:bg-slate-50/80 transition-all">
                  <td class="p-3 font-bold text-slate-900 flex items-center gap-2">
                    <div class="w-7 h-7 rounded-full bg-indigo-600 text-white font-bold text-[10px] flex items-center justify-center">
                      {{ user.fullName.charAt(0) }}
                    </div>
                    {{ user.fullName }}
                  </td>
                  <td class="p-3 font-mono text-slate-600">&#64;{{ user.username }}</td>
                  <td class="p-3 text-slate-700 font-semibold">{{ user.employeeNo || 'N/A' }}</td>
                  <td class="p-3 text-slate-600">{{ user.department || 'General' }}</td>
                  <td class="p-3">
                    <span [class]="getRoleBadgeClasses(user.role)" class="text-[10px] font-bold px-2.5 py-0.5 rounded-full border uppercase">
                      {{ getRoleLabel(user.role) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

      </ng-container>

    </div>
  `
})
export class UserManagementComponent implements OnInit {

  // Reserved Usernames Blocklist
  readonly reservedUsernames = new Set([
    'admin', 'administrator', 'root', 'support', 'null', 'system', 'user', 'superuser', 'guest', 'api', 'owner'
  ]);

  // Form Fields
  fullName = '';
  username = '';
  password = '';
  employeeNumDigits = ''; // Admin appends digits to prefilled EMP-

  roleSelect = 'ROLE_TEAM_MEMBER';

  departmentSelect = 'Software Engineering';
  customDepartment = '';

  // Password Checklist Status
  hasMinLength = false;
  hasUppercase = false;
  hasLowercase = false;
  hasNumber = false;
  hasSpecial = false;

  // Field-Level Validation Error Messages
  fullNameError = '';
  usernameError = '';
  passwordError = '';
  employeeNoError = '';
  customDepartmentError = '';

  isFormValid = false;
  globalErrorMessage: string | null = null;

  constructor(
    public userService: UserService,
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    this.userService.loadUsers();
    this.validateForm();
  }

  onFullNameInput(): void {
    this.onFieldInput('fullName');
  }

  onUsernameInput(): void {
    this.onFieldInput('username');
  }

  onEmployeeNumInput(): void {
    if (this.employeeNumDigits) {
      this.employeeNumDigits = this.employeeNumDigits.replace(/[^0-9]/g, '');
    }
    this.onFieldInput('employeeNo');
  }

  onRoleChange(): void {
    this.validateForm();
  }

  onDepartmentChange(): void {
    if (this.departmentSelect !== 'OTHER') {
      this.customDepartment = '';
      this.customDepartmentError = '';
    }
    this.validateForm();
  }

  onFieldInput(fieldName: string): void {
    this.globalErrorMessage = null;
    this.validateForm();
  }

  validateForm(): boolean {
    let valid = true;

    // 1. Full Name Validation
    const trimmedName = this.fullName ? this.fullName.trim() : '';
    if (!trimmedName) {
      this.fullNameError = 'Full Name is required.';
      valid = false;
    } else if (trimmedName.length < 2 || trimmedName.length > 70) {
      this.fullNameError = 'Full Name must be between 2 and 70 characters long.';
      valid = false;
    } else if (/[<>&]/.test(trimmedName)) {
      this.fullNameError = 'Full Name cannot contain HTML tags or special script symbols.';
      valid = false;
    } else if (!/^[\p{L}]+(?:[ '\-][\p{L}]+)*$/u.test(trimmedName)) {
      this.fullNameError = 'Full Name can only contain letters, spaces, hyphens, and apostrophes.';
      valid = false;
    } else if (!this.fullNameError.includes('already')) {
      this.fullNameError = '';
    }

    // 2. Username Validation
    const trimmedUser = this.username ? this.username.trim() : '';
    if (!trimmedUser) {
      this.usernameError = 'Username is required.';
      valid = false;
    } else if (trimmedUser.length < 3 || trimmedUser.length > 30) {
      this.usernameError = 'Username must be between 3 and 30 characters long.';
      valid = false;
    } else if (this.reservedUsernames.has(trimmedUser.toLowerCase())) {
      this.usernameError = `Username '${trimmedUser}' is a reserved system keyword.`;
      valid = false;
    } else if (/[._-]{2,}/.test(trimmedUser)) {
      this.usernameError = 'Username cannot contain consecutive special symbols.';
      valid = false;
    } else if (!/^[a-zA-Z0-9](?:[a-zA-Z0-9._-]{0,28}[a-zA-Z0-9])?$/.test(trimmedUser)) {
      this.usernameError = 'Username must start and end with an alphanumeric character.';
      valid = false;
    } else if (!this.usernameError.includes('already taken')) {
      this.usernameError = '';
    }

    // 3. Initial Password Validation
    const rawPass = this.password ? this.password : '';
    this.hasMinLength = rawPass.length >= 8 && rawPass.length <= 128;
    this.hasUppercase = /[A-Z]/.test(rawPass);
    this.hasLowercase = /[a-z]/.test(rawPass);
    this.hasNumber = /[0-9]/.test(rawPass);
    this.hasSpecial = /[\s!"#$%&'()*+,-./:;<=>?@\[\]^_`{|}~]/.test(rawPass);

    if (!rawPass) {
      this.passwordError = 'Initial Password is required.';
      valid = false;
    } else if (rawPass.length < 8 || rawPass.length > 128) {
      this.passwordError = 'Password must be between 8 and 128 characters long.';
      valid = false;
    } else if (!this.hasUppercase || !this.hasLowercase || !this.hasNumber || !this.hasSpecial) {
      this.passwordError = 'Password must contain at least 1 uppercase, 1 lowercase, 1 number, and 1 special character.';
      valid = false;
    } else if (!this.passwordError.includes('already')) {
      this.passwordError = '';
    }

    // 4. Employee Number Validation
    const empDigits = this.employeeNumDigits ? this.employeeNumDigits.trim() : '';
    if (!empDigits) {
      this.employeeNoError = 'Employee Number digits are required (e.g. 301).';
      valid = false;
    } else if (!/^[0-9]{1,6}$/.test(empDigits)) {
      this.employeeNoError = 'Employee Number must contain 1-6 numeric digits after EMP- (e.g. 301).';
      valid = false;
    } else if (!this.employeeNoError.includes('already exists')) {
      this.employeeNoError = '';
    }

    // 5. Custom Department Validation if "Other" chosen
    if (this.departmentSelect === 'OTHER') {
      if (!this.customDepartment || this.customDepartment.trim().length === 0) {
        this.customDepartmentError = 'Custom Department Name is required when selecting Other.';
        valid = false;
      } else {
        this.customDepartmentError = '';
      }
    } else {
      this.customDepartmentError = '';
    }

    this.isFormValid = valid;
    return valid;
  }

  onCreateUser(): void {
    if (!this.validateForm()) return;
    this.globalErrorMessage = null;

    const fullEmployeeNo = 'EMP-' + this.employeeNumDigits.trim();

    const finalDepartment = this.departmentSelect === 'OTHER' 
      ? this.customDepartment.trim() 
      : this.departmentSelect;

    const request: CreateUserRequest = {
      username: this.username.trim(),
      password: this.password,
      fullName: this.fullName.trim(),
      role: this.roleSelect,
      employeeNo: fullEmployeeNo,
      department: finalDepartment
    };

    this.userService.createUser(request).subscribe({
      next: () => {
        this.username = '';
        this.password = '';
        this.fullName = '';
        this.employeeNumDigits = '';
        this.customDepartment = '';
        this.roleSelect = 'ROLE_TEAM_MEMBER';
        this.departmentSelect = 'Software Engineering';

        this.fullNameError = '';
        this.usernameError = '';
        this.passwordError = '';
        this.employeeNoError = '';
        this.customDepartmentError = '';

        this.validateForm();
      },
      error: (err) => {
        let msg = 'Failed to create user.';
        if (err.error) {
          if (typeof err.error === 'string') {
            msg = err.error;
          } else if (err.error.message) {
            msg = err.error.message;
          } else if (err.message) {
            msg = err.message;
          }
        }
        
        const lowerMsg = msg.toLowerCase();
        if (lowerMsg.includes('username')) {
          this.usernameError = msg;
          this.globalErrorMessage = null;
        } else if (lowerMsg.includes('employee number') || lowerMsg.includes('employee no')) {
          this.employeeNoError = msg;
          this.globalErrorMessage = null;
        } else if (lowerMsg.includes('full name')) {
          this.fullNameError = msg;
          this.globalErrorMessage = null;
        } else if (lowerMsg.includes('password')) {
          this.passwordError = msg;
          this.globalErrorMessage = null;
        } else if (lowerMsg.includes('department')) {
          this.customDepartmentError = msg;
          this.globalErrorMessage = null;
        } else {
          this.globalErrorMessage = msg;
        }
      }
    });
  }

  getRoleLabel(role?: string): string {
    if (!role) return 'Developer';
    switch (role) {
      case 'ROLE_PROJECT_MANAGER': return 'Project Manager';
      case 'ROLE_TEAM_LEAD': return 'Team Lead';
      case 'ROLE_TEAM_MEMBER': return 'Developer';
      case 'ROLE_TESTER': return 'Tester';
      default: return role.replace('ROLE_', '');
    }
  }

  getRoleBadgeClasses(role?: string): string {
    switch (role) {
      case 'ROLE_PROJECT_MANAGER': return 'bg-purple-50 text-purple-700 border-purple-200';
      case 'ROLE_TEAM_LEAD': return 'bg-indigo-50 text-indigo-700 border-indigo-200';
      case 'ROLE_TEAM_MEMBER': return 'bg-emerald-50 text-emerald-700 border-emerald-200';
      case 'ROLE_TESTER': return 'bg-sky-50 text-sky-700 border-sky-200';
      default: return 'bg-amber-50 text-amber-700 border-amber-200';
    }
  }
}
