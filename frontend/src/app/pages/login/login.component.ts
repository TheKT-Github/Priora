import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';

/**
 * Dedicated Full-Page Login Route Component (`/login`).
 * Clean executive corporate sign-in interface.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="min-h-screen bg-slate-100 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 w-full max-w-md rounded-2xl p-8 shadow-xl space-y-6">
        
        <!-- Brand Title -->
        <div class="text-center space-y-2">
          <div class="w-12 h-12 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-extrabold text-xl mx-auto shadow-sm">
            P
          </div>
          <h1 class="text-2xl font-bold text-slate-900 tracking-tight">Priora</h1>
          <p class="text-xs text-slate-500 font-normal">Enterprise Priority & Authorization System</p>
        </div>

        <!-- Error Banner -->
        <div *ngIf="errorMessage" class="bg-rose-50 border border-rose-200 text-rose-700 px-4 py-2.5 rounded-lg text-xs font-medium">
          {{ errorMessage }}
        </div>

        <!-- Form with autocomplete disabled -->
        <form (ngSubmit)="onLogin()" class="space-y-4" autocomplete="off">
          
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Username *</label>
            <input type="text" [(ngModel)]="username" name="username" required
                   autocomplete="off"
                   placeholder="Enter username"
                   class="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white font-mono">
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Password *</label>
            <input type="password" [(ngModel)]="password" name="password" required
                   autocomplete="new-password"
                   placeholder="••••••••"
                   class="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2.5 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white font-mono">
          </div>

          <!-- Sign In Button -->
          <button type="submit" [disabled]="!username || !password || loading"
                  class="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-sm flex items-center justify-center gap-2 transition-all">
            <span *ngIf="loading" class="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            Sign In to Dashboard
          </button>

        </form>

        <div class="text-center text-xs text-slate-500 border-t border-slate-100 pt-4 space-y-2">
          <div>
            Don't have an account? 
            <a routerLink="/register" class="font-semibold text-indigo-600 hover:text-indigo-700 hover:underline">
              Create an account
            </a>
          </div>
          <div class="text-[11px] text-slate-400">
            Protected by Spring Security Role-Based Access Control (RBAC)
          </div>
        </div>

      </div>
    </div>
  `
})
export class LoginComponent implements OnInit {
  username = '';
  password = '';
  loading = false;
  errorMessage: string | null = null;

  constructor(
    private authService: AuthService,
    private taskService: TaskService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.username = '';
    this.password = '';
  }

  onLogin(): void {
    if (!this.username || !this.password) return;
    this.loading = true;
    this.errorMessage = null;

    this.authService.login(this.username, this.password).subscribe({
      next: () => {
        this.loading = false;
        this.taskService.loadTasks();
        this.router.navigate(['/canvas']);
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Invalid username or password. Please try again.';
      }
    });
  }
}
