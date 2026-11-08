import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Task, TaskStatus } from '../../models/task.model';
import { TaskService } from '../../services/task.service';
import { AuthService } from '../../services/auth.service';

/**
 * Standardized Corporate Executive Task Card Component.
 * Guaranteed 100% Uniform Size (Fixed 460px Height), Alignment, and Design across Focus, Drifting, and Orbit Zones.
 * Restricts Ping action trigger to Team Leads & Project Managers (`authService.hasLeadPermissions()`).
 */
@Component({
  selector: 'app-task-card',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div [class]="getCardContainerClasses()"
         class="bg-white border rounded-2xl p-5 shadow-xs hover:shadow-md transition-all duration-200 flex flex-col justify-between h-[460px] min-h-[460px] w-full">
      
      <!-- Top Bar: Environment, Overdue Badge & Gravity Score -->
      <div class="flex items-center justify-between gap-2 border-b border-slate-100 pb-3 h-10">
        <div class="flex flex-wrap items-center gap-1.5 overflow-hidden">
          <span [class]="getEnvBadgeClasses(task.environment)" class="text-[10px] font-bold tracking-wider uppercase px-2 py-0.5 rounded border">
            {{ task.environment }} ({{ task.environmentMultiplier }}x)
          </span>

          <span *ngIf="task.overdue && task.status !== 'DONE'" 
                class="bg-rose-50 text-rose-700 border border-rose-200 text-[10px] font-bold px-2 py-0.5 rounded flex items-center gap-1">
            <svg class="w-3 h-3 text-rose-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            Overdue
          </span>

          <!-- Active Priority Ping Alert Badge -->
          <span *ngIf="task.pinged && task.status !== 'DONE'" 
                class="bg-amber-500 text-white text-[10px] font-extrabold px-2 py-0.5 rounded-full animate-pulse flex items-center gap-1 shadow-2xs">
            <svg class="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
            Pinged Active
          </span>
        </div>

        <!-- Gravity Score Display -->
        <div class="text-right flex items-center gap-1.5 bg-slate-50 border border-slate-200/80 px-2.5 py-1 rounded-xl shrink-0">
          <span class="text-[10px] text-slate-500 font-bold uppercase tracking-wider">Score</span>
          <span class="text-sm font-extrabold text-slate-900 leading-none">{{ task.totalGravityScore }}</span>
        </div>
      </div>

      <!-- Task Title & Description -->
      <div class="h-[60px] min-h-[60px] space-y-1 flex flex-col justify-center">
        <h3 class="text-sm font-bold text-slate-900 tracking-tight leading-snug line-clamp-1" [title]="'#' + task.id + ' - ' + task.title">
          #{{ task.id }} - {{ task.title }}
        </h3>
        <p class="text-xs text-slate-500 font-normal leading-relaxed line-clamp-1">
          {{ task.description || 'No additional description provided.' }}
        </p>
      </div>

      <!-- Formula Factors Breakdown Matrix -->
      <div class="h-[170px] min-h-[170px] bg-slate-50 rounded-xl p-3 border border-slate-100/90 grid grid-cols-2 gap-2 text-[11px] content-between">
        <div>
          <span class="text-slate-400 font-medium block text-[10px] uppercase">Blast Radius</span>
          <span class="font-bold text-slate-800">{{ task.dependencyScore }} pts</span>
          <span class="text-[10px] text-slate-500 block truncate">({{ task.directBlockedCount }} direct / {{ task.indirectBlockedCount }} indirect)</span>
        </div>

        <div>
          <span class="text-slate-400 font-medium block text-[10px] uppercase">Deadline Proximity</span>
          <span class="font-bold text-slate-800">{{ task.deadlineProximityScore }} pts</span>
          <span [class]="task.overdue ? 'text-rose-600 font-semibold' : 'text-slate-500'" class="text-[10px] block truncate">
            ({{ task.hoursUntilDeadline }}h left)
          </span>
        </div>

        <div>
          <span class="text-slate-400 font-medium block text-[10px] uppercase">Aging / Stagnation</span>
          <span class="font-bold text-slate-800">{{ task.stagnationScore }} pts</span>
          <span class="text-[10px] text-slate-500 block truncate">({{ task.hoursInactive }}h inactive)</span>
        </div>

        <div>
          <span class="text-slate-400 font-medium block text-[10px] uppercase">Assignee Load</span>
          <span class="font-bold text-slate-800">{{ task.workloadDensityScore }} pts</span>
          <span class="text-[10px] text-slate-500 block truncate">({{ task.assigneeActiveTasksCount }} active tasks)</span>
        </div>

        <!-- Custom Factor Row OR Uniform Equal Height Placeholder Slot -->
        <div *ngIf="task.customFactorScore && task.customFactorScore > 0; else emptyCustomSlot" 
             class="col-span-2 bg-indigo-50/70 p-2 rounded-lg border border-indigo-100/80 flex items-center justify-between">
          <div class="truncate">
            <span class="text-indigo-700 font-bold block text-[10px] uppercase truncate">
              {{ task.customFactorName || 'Custom Weight' }}
            </span>
            <span class="text-[10px] text-indigo-600 block leading-tight">(Applied to Task)</span>
          </div>
          <span class="font-extrabold text-indigo-900 text-xs shrink-0">+{{ task.customFactorScore }} pts</span>
        </div>

        <ng-template #emptyCustomSlot>
          <div class="col-span-2 bg-slate-100/60 p-2 rounded-lg border border-dashed border-slate-200/60 flex items-center justify-between text-slate-400 text-[10px]">
            <span class="font-medium text-slate-400">Custom Factor: None</span>
            <span class="font-semibold text-slate-400">+0 pts</span>
          </div>
        </ng-template>
      </div>

      <!-- Assignee & Ping Row (Team Lead / Project Manager Interactive Toggle vs Developer / Tester Read-Only Badge) -->
      <div class="h-10 pt-2 border-t border-slate-100 flex items-center justify-between">
        <!-- Assignee Avatar -->
        <div class="flex items-center gap-2">
          <div class="w-6 h-6 rounded-full bg-slate-200 text-slate-700 font-bold text-[10px] flex items-center justify-center border border-slate-300">
            {{ task.assignee?.fullName?.charAt(0) || 'U' }}
          </div>
          <span class="text-xs font-semibold text-slate-700 truncate max-w-[120px]">
            {{ task.assignee?.fullName || 'Unassigned' }}
          </span>
        </div>

        <!-- LEAD ONLY: Interactive Toggle Ping Button -->
        <button *ngIf="authService.hasLeadPermissions()" 
                (click)="onPingToggle()"
                [class]="task.pinged ? 'bg-rose-600 text-white font-extrabold shadow-sm' : 'bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200/80 font-semibold'"
                title="Toggle Lead Ping Alert for Assignee"
                class="px-2.5 py-1 rounded-lg text-xs flex items-center gap-1 transition-all">
          <svg class="w-3 h-3" [class]="task.pinged ? 'text-white' : 'text-amber-600'" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
          {{ task.pinged ? 'Pinged ON' : 'Ping' }}
          <span [class]="task.pinged ? 'bg-white text-rose-700' : 'bg-amber-200 text-amber-900'" class="text-[10px] font-bold px-1.5 py-0.2 rounded-full">
            {{ task.pingCount }}
          </span>
        </button>

        <!-- DEVELOPER & TESTER READ-ONLY PING BADGE -->
        <div *ngIf="!authService.hasLeadPermissions()"
             title="Ping alerts are issued by Team Leads and Project Managers"
             [class]="task.pinged ? 'bg-amber-100 text-amber-900 border border-amber-300 font-bold' : 'bg-slate-100 text-slate-500 border border-slate-200 font-medium'"
             class="px-2.5 py-1 rounded-lg text-xs flex items-center gap-1 cursor-not-allowed">
          <svg class="w-3 h-3 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
          Pings
          <span class="bg-slate-200 text-slate-800 text-[10px] font-bold px-1.5 py-0.2 rounded-full">
            {{ task.pingCount }}
          </span>
        </div>
      </div>

      <!-- Action Buttons Row -->
      <div class="h-12 pt-2 border-t border-slate-100 flex items-center justify-between gap-2">
        
        <!-- Status Dropdown -->
        <select [ngModel]="task.status" (ngModelChange)="onStatusChange($event)"
                class="bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-slate-800 focus:outline-none focus:border-indigo-500 flex-1">
          <option value="TODO">TODO</option>
          <option value="IN_PROGRESS">IN PROGRESS</option>
          <option value="IN_REVIEW">IN REVIEW</option>
          <option value="BLOCKED">BLOCKED</option>
          <option value="DONE">DONE</option>
        </select>

        <!-- Quick Action Buttons (Team Lead / Project Manager Only) -->
        <div *ngIf="authService.hasLeadPermissions()" class="flex items-center gap-1.5 shrink-0">
          
          <!-- Reassign Trigger -->
          <button (click)="taskService.openReassignModal(task)"
                  class="px-2.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-semibold border border-slate-200 transition-all">
            Reassign
          </button>

          <!-- Edit Deadline Trigger -->
          <button (click)="taskService.openDeadlineModal(task)"
                  class="px-2.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-semibold border border-slate-200 transition-all">
            Deadline
          </button>

          <!-- Delete -->
          <button (click)="onDelete()"
                  title="Delete Task"
                  class="px-2.5 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded-lg text-xs font-semibold transition-all flex items-center justify-center">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>

      </div>

    </div>
  `
})
export class TaskCardComponent {
  @Input({ required: true }) task!: Task;

  constructor(
    public taskService: TaskService,
    public authService: AuthService
  ) {}

  getCardContainerClasses(): string {
    if (this.task.status === 'DONE') {
      return 'border-emerald-200 bg-emerald-50/20';
    }
    switch (this.task.canvasZone) {
      case 'FOCUS': return 'border-indigo-300 border-l-4 border-l-indigo-600';
      case 'DRIFTING': return 'border-slate-300 border-l-4 border-l-amber-500';
      default: return 'border-slate-200 border-l-4 border-l-slate-400';
    }
  }

  getEnvBadgeClasses(env: string): string {
    switch (env) {
      case 'PRODUCTION': return 'bg-rose-50 text-rose-700 border-rose-200';
      case 'STAGING': return 'bg-amber-50 text-amber-700 border-amber-200';
      default: return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  }

  onPingToggle(): void {
    if (!this.authService.hasLeadPermissions()) return;
    this.taskService.pingBlocker(this.task.id).subscribe();
  }

  onStatusChange(newStatus: TaskStatus): void {
    this.taskService.updateStatus(this.task.id, newStatus).subscribe();
  }

  onDelete(): void {
    if (confirm(`Are you sure you want to delete Task #${this.task.id}: "${this.task.title}"?`)) {
      this.taskService.deleteTask(this.task.id).subscribe();
    }
  }
}
