import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { UserService } from '../../services/user.service';
import { CreateUserRequest } from '../../models/user.model';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="min-h-screen bg-slate-100 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 w-full max-w-lg rounded-2xl p-8 shadow-xl space-y-6">
        
        <!-- Brand Header -->
        <div class="text-center space-y-2">
          <div class="w-12 h-12 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-extrabold text-xl mx-auto shadow-sm">
            P
          </div>
          <h1 class="text-2xl font-bold text-slate-900 tracking-tight">Create an Account</h1>
          <p class="text-xs text-slate-500 font-normal">Sign up to access Priora Task Management</p>
        </div>

        <!-- Global Success Message -->
        <div *ngIf="successMessage" class="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-xl text-xs font-semibold flex items-center gap-2">
          <svg class="w-4 h-4 text-emerald-600 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          {{ successMessage }}
        </div>

        <!-- Global Error Message -->
        <div *ngIf="errorMessage" class="bg-rose-50 border border-rose-200 text-rose-700 px-4 py-2.5 rounded-lg text-xs font-medium">
          {{ errorMessage }}
        </div>

        <!-- Registration Form -->
        <form (ngSubmit)="onRegister()" class="space-y-4" autocomplete="off">
          
          <!-- Full Name -->
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Full Name *</label>
            <input type="text" [(ngModel)]="fullName" name="fullName" required
                   placeholder="e.g. Alex Vance"
                   class="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
          </div>

          <!-- Username -->
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Username *</label>
            <input type="text" [(ngModel)]="username" name="username" required
                   placeholder="e.g. alex.vance (lowercase, no spaces)"
                   class="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white font-mono">
          </div>

          <!-- Password -->
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Password *</label>
            <input type="password" [(ngModel)]="password" name="password" required
                   placeholder="Min 8 chars, 1 uppercase, 1 lowercase, 1 number, 1 symbol"
                   class="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white font-mono">
            <p class="text-[10px] text-slate-400 mt-1">Requires 8+ chars, upper, lower, number, and special character.</p>
          </div>

          <!-- Role: read-only display — backend always assigns Team Member on public registration -->
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Role</label>
              <div class="w-full bg-slate-100 border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-500 select-none">
                Team Member
              </div>
              <p class="text-[10px] text-slate-400 mt-1">Assigned by the system. A manager can upgrade your role later.</p>
            </div>

            <!-- Employee Number -->
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Employee ID *</label>
              <input type="text" [(ngModel)]="employeeNo" name="employeeNo" required
                     placeholder="EMP-101"
                     class="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white font-mono">
            </div>
          </div>

          <!-- Department -->
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Department *</label>
            <input type="text" [(ngModel)]="department" name="department" required
                   placeholder="e.g. Software Engineering"
                   class="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
          </div>

          <!-- Submit Button -->
          <button type="submit" [disabled]="!isFormValid() || loading"
                  class="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-sm flex items-center justify-center gap-2 transition-all">
            <span *ngIf="loading" class="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            Register Account
          </button>

        </form>

        <!-- Redirect to Login Link -->
        <div class="text-center text-xs text-slate-500 border-t border-slate-100 pt-4">
          Already have an account? 
          <a routerLink="/login" class="font-semibold text-indigo-600 hover:text-indigo-700 hover:underline">
            Sign In here
          </a>
        </div>

      </div>
    </div>
  `
})
export class RegisterComponent {
  fullName = '';
  username = '';
  password = '';
  employeeNo = '';
  department = '';
  loading = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  constructor(
    private userService: UserService,
    private router: Router
  ) {}

  isFormValid(): boolean {
    return !!(
      this.fullName.trim() &&
      this.username.trim() &&
      this.password.trim() &&
      this.employeeNo.trim() &&
      this.department.trim()
    );
  }

  onRegister(): void {
    if (!this.isFormValid()) return;

    this.loading = true;
    this.errorMessage = null;
    this.successMessage = null;

    const request: CreateUserRequest = {
      fullName: this.fullName.trim(),
      username: this.username.trim(),
      password: this.password.trim(),
      role: 'ROLE_TEAM_MEMBER',  // always TEAM_MEMBER; backend enforces this regardless
      employeeNo: this.employeeNo.trim(),
      department: this.department.trim()
    };

    this.userService.registerUser(request).subscribe({
      next: (user) => {
        this.loading = false;
        this.successMessage = `Registration successful for ${user.fullName}! You will be registered as a Team Member. Redirecting to login...`;
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 1500);
      },
      error: (err) => {
        this.loading = false;
        if (err.error?.message) {
          this.errorMessage = err.error.message;
        } else {
          this.errorMessage = 'Registration failed. Please verify the entered details meet all format requirements.';
        }
      }
    });
  }
}
