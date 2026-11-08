import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { TaskService } from '../../services/task.service';

/**
 * Corporate Minimalist Navigation Header Component.
 * Restricts Task Creation to Team Leads and Project Managers, features Ping Notification Alerts, and user profile links.
 */
@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <!-- Developer & Tester Ping Notification Stream Banner -->
    <div *ngIf="!authService.hasLeadPermissions() && taskService.developerNotifications().length > 0" 
         class="bg-amber-500 text-white px-6 py-2 text-xs font-bold flex items-center justify-between shadow-xs border-b border-amber-600 animate-pulse">
      <div class="flex items-center gap-2 max-w-7xl mx-auto w-full">
        <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
        <span>
          <strong>Priority Alert:</strong> Lead pinged {{ taskService.developerNotifications().length }} task(s) assigned to you: 
          <span *ngFor="let t of taskService.developerNotifications()" class="underline ml-1">#{{ t.id }} - {{ t.title }}</span>
        </span>
      </div>
    </div>

    <header *ngIf="authService.isAuthenticated()" class="bg-white border-b border-slate-200 sticky top-0 z-30 px-6 py-3 shadow-2xs">
      <div class="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4">
        
        <!-- Brand & Main Navigation Links -->
        <div class="flex items-center gap-6">
          <a routerLink="/canvas" class="flex items-center gap-2.5 group">
            <div class="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center text-white font-extrabold text-sm shadow-xs group-hover:bg-indigo-700 transition-all">
              P
            </div>
            <div>
              <h1 class="text-lg font-bold text-slate-900 tracking-tight leading-tight">Priora</h1>
              <p class="text-[10px] text-slate-500 font-normal">Enterprise Task Priority Engine</p>
            </div>
          </a>

          <!-- Router Navigation Tabs -->
          <nav class="flex items-center gap-1 bg-slate-100 p-1 rounded-xl border border-slate-200 text-xs">
            <a routerLink="/canvas" routerLinkActive="bg-white text-indigo-700 shadow-2xs font-bold"
               class="px-3 py-1.5 rounded-lg text-slate-600 font-medium hover:text-slate-900 transition-all">
              Canvas
            </a>

            <a *ngIf="authService.hasLeadPermissions()" routerLink="/settings" routerLinkActive="bg-white text-indigo-700 shadow-2xs font-bold"
               class="px-3 py-1.5 rounded-lg text-slate-600 font-medium hover:text-slate-900 transition-all">
              Settings
            </a>

            <a *ngIf="authService.hasLeadPermissions()" routerLink="/approvals" routerLinkActive="bg-white text-indigo-700 shadow-2xs font-bold"
               class="px-3 py-1.5 rounded-lg text-slate-600 font-medium hover:text-slate-900 transition-all">
              Approvals
            </a>

            <!-- Project Manager Only: User Provisioning -->
            <a *ngIf="authService.isProjectManager()" routerLink="/users" routerLinkActive="bg-white text-purple-700 shadow-2xs font-bold"
               class="px-3 py-1.5 rounded-lg text-slate-600 font-medium hover:text-slate-900 transition-all">
              Users
            </a>
          </nav>
        </div>

        <!-- Right Side User Profile & Actions -->
        <div class="flex items-center gap-3">
          
          <!-- Team Lead / Project Manager Only: Create Task Button -->
          <button *ngIf="authService.hasLeadPermissions()" (click)="openCreateModal.emit()"
                  class="bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-xs px-3.5 py-2 rounded-lg shadow-2xs transition-all flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            Create Task
          </button>

          <!-- User Profile Badge (Clicking opens /profile) -->
          <a routerLink="/profile" 
             title="View Profile & Change Password"
             class="flex items-center gap-2 bg-slate-50 hover:bg-slate-100 border border-slate-200 px-3 py-1 rounded-lg transition-all group">
            <div class="w-6 h-6 rounded-full bg-indigo-600 flex items-center justify-center font-bold text-white text-[10px]">
              {{ authService.currentUser()?.fullName?.charAt(0) || 'U' }}
            </div>
            <div class="text-left">
              <div class="text-xs font-semibold text-slate-800 group-hover:text-indigo-600 leading-tight transition-colors">
                {{ authService.currentUser()?.fullName }}
              </div>
              <div class="text-[9px] font-semibold text-slate-500 uppercase">
                {{ getRoleLabel(authService.currentUser()?.role) }}
              </div>
            </div>
          </a>

          <!-- Explicit Sign Out Button -->
          <button (click)="onSignOut()"
                  title="Sign Out"
                  class="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-medium border border-slate-200 transition-all">
            Sign Out
          </button>

        </div>

      </div>
    </header>
  `
})
export class HeaderComponent {
  @Output() openCreateModal = new EventEmitter<void>();

  constructor(
    public authService: AuthService,
    public taskService: TaskService,
    private router: Router
  ) {}

  getRoleLabel(role?: string): string {
    switch (role) {
      case 'ROLE_PROJECT_MANAGER': return 'Project Manager';
      case 'ROLE_TEAM_LEAD': return 'Team Lead';
      case 'ROLE_TESTER': return 'Tester';
      case 'ROLE_TEAM_MEMBER': return 'Developer';
      default: return role ? role.replace('ROLE_', '') : 'Developer';
    }
  }

  onSignOut(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
