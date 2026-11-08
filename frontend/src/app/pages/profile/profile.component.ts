import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { UserService } from '../../services/user.service';

/**
 * User Profile Page (`/profile`).
 * Displays User Details (Name, Employee No, Department, Role, Username)
 * with a "Change Password" button that reveals the Change Password form upon user action.
 */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-3xl mx-auto px-6 py-8 space-y-6">
      
      <!-- Section Header -->
      <div class="border-b border-slate-200 pb-4">
        <h2 class="text-xl font-bold text-slate-900 tracking-tight">User Account Profile</h2>
        <p class="text-xs text-slate-500 font-normal mt-0.5">
          View your employment credentials and manage security settings.
        </p>
      </div>

      <!-- Notification Banners -->
      <div *ngIf="userService.successMessage()" 
           class="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-xl text-xs font-semibold shadow-sm">
        {{ userService.successMessage() }}
      </div>

      <div *ngIf="errorMessage" 
           class="bg-rose-50 border border-rose-200 text-rose-800 px-4 py-3 rounded-xl text-xs font-semibold shadow-sm">
        {{ errorMessage }}
      </div>

      <!-- Employee Credentials Summary Card -->
      <div class="bg-white border border-slate-200 rounded-2xl p-6 shadow-sm space-y-5">
        
        <div class="flex items-center justify-between border-b border-slate-100 pb-4">
          <div class="flex items-center gap-3">
            <div class="w-12 h-12 rounded-xl bg-indigo-600 text-white font-extrabold text-lg flex items-center justify-center shadow-xs">
              {{ authService.currentUser()?.fullName?.charAt(0) || 'U' }}
            </div>
            <div>
              <h3 class="text-base font-bold text-slate-900 leading-tight">
                {{ authService.currentUser()?.fullName }}
              </h3>
              <p class="text-xs text-slate-500 font-medium">&#64;{{ authService.currentUser()?.username }}</p>
            </div>
          </div>

          <span [class]="getRoleBadgeClasses(authService.currentUser()?.role)"
                class="text-xs font-bold px-3 py-1 rounded-full uppercase border">
            {{ getRoleLabel(authService.currentUser()?.role) }}
          </span>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
          <div class="bg-slate-50 p-3.5 rounded-xl border border-slate-100">
            <span class="text-slate-400 font-medium block text-[10px] uppercase">Employee Number</span>
            <span class="font-bold text-slate-800 text-sm">
              {{ authService.currentUser()?.employeeNo || 'EMP-101' }}
            </span>
          </div>

          <div class="bg-slate-50 p-3.5 rounded-xl border border-slate-100">
            <span class="text-slate-400 font-medium block text-[10px] uppercase">Department</span>
            <span class="font-bold text-slate-800 text-sm">
              {{ authService.currentUser()?.department || 'Engineering' }}
            </span>
          </div>
        </div>

        <!-- Change Password Action Trigger Button -->
        <div class="flex items-center justify-between pt-4 border-t border-slate-100">
          <div class="text-xs text-slate-500 font-medium">
            Security Settings: Password last updated recently
          </div>

          <button (click)="showChangePasswordForm = !showChangePasswordForm"
                  class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-xs transition-all flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
            {{ showChangePasswordForm ? 'Close Password Form' : 'Change Password' }}
          </button>
        </div>

      </div>

      <!-- CONDITIONAL CHANGE PASSWORD TEMPLATE -->
      <div *ngIf="showChangePasswordForm" 
           class="bg-white border border-slate-200 rounded-2xl p-6 shadow-md space-y-4 animate-fade-in">
        
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h3 class="text-base font-bold text-slate-900">Change Account Password</h3>
            <p class="text-xs text-slate-500 font-normal mt-0.5">
              Enter your current password and specify a new security password.
            </p>
          </div>
          <button (click)="showChangePasswordForm = false" class="text-slate-400 hover:text-slate-600 text-lg font-bold">
            &times;
          </button>
        </div>

        <form (ngSubmit)="onChangePassword()" class="space-y-4 pt-1">
          
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Current Password *</label>
            <input type="password" [(ngModel)]="currentPassword" name="currentPassword" required
                   placeholder="••••••••"
                   class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">New Password (Min 8 chars) *</label>
              <input type="password" [(ngModel)]="newPassword" name="newPassword" required
                     placeholder="••••••••"
                     class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
            </div>

            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Confirm New Password *</label>
              <input type="password" [(ngModel)]="confirmPassword" name="confirmPassword" required
                     placeholder="••••••••"
                     class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
            </div>
          </div>

          <div class="flex items-center justify-end gap-2 pt-2">
            <button type="button" (click)="showChangePasswordForm = false"
                    class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition-all">
              Cancel
            </button>
            <button type="submit" [disabled]="!currentPassword || !newPassword || !confirmPassword || userService.loading()"
                    class="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-sm transition-all flex items-center gap-2">
              <span *ngIf="userService.loading()" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              Submit New Password
            </button>
          </div>

        </form>
      </div>

    </div>
  `
})
export class ProfileComponent {
  showChangePasswordForm = false;

  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  errorMessage: string | null = null;

  constructor(
    public authService: AuthService,
    public userService: UserService
  ) {}

  onChangePassword(): void {
    this.errorMessage = null;

    if (!this.currentPassword || !this.newPassword || !this.confirmPassword) {
      this.errorMessage = 'Please complete all password fields.';
      return;
    }

    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'New password and confirmation password do not match.';
      return;
    }

    if (this.newPassword.length < 8) {
      this.errorMessage = 'New password must be at least 8 characters long.';
      return;
    }

    this.userService.changePassword({
      currentPassword: this.currentPassword,
      newPassword: this.newPassword
    }).subscribe({
      next: () => {
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
        this.showChangePasswordForm = false;
      },
      error: (err) => {
        if (err.status === 400 || err.error?.message) {
          this.errorMessage = err.error?.message || 'Incorrect current password.';
        } else {
          this.errorMessage = 'Failed to update password. Please verify current password.';
        }
      }
    });
  }

  getRoleLabel(role?: string): string {
    switch (role) {
      case 'ROLE_PROJECT_MANAGER': return 'Project Manager';
      case 'ROLE_TEAM_LEAD': return 'Team Lead';
      case 'ROLE_TESTER': return 'Tester';
      case 'ROLE_TEAM_MEMBER': return 'Developer / Team Member';
      default: return role ? role.replace('ROLE_', '') : 'Developer';
    }
  }

  getRoleBadgeClasses(role?: string): string {
    switch (role) {
      case 'ROLE_PROJECT_MANAGER': return 'bg-purple-50 text-purple-700 border-purple-200';
      case 'ROLE_TEAM_LEAD': return 'bg-indigo-50 text-indigo-700 border-indigo-200';
      case 'ROLE_TESTER': return 'bg-sky-50 text-sky-700 border-sky-200';
      default: return 'bg-emerald-50 text-emerald-700 border-emerald-200';
    }
  }
}
