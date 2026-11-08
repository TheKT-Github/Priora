import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TaskService } from '../../services/task.service';
import { AuthService } from '../../services/auth.service';
import { Task, PageResponse } from '../../models/task.model';

/**
 * Dedicated Manager Approvals Page (`/approvals`).
 * Authorization queue for developer-submitted tickets with manager rejection reason tracking.
 * Includes server-side pagination with Phase 10 API endpoints.
 * Zero emojis - clean corporate presentation.
 */
@Component({
  selector: 'app-approvals',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-7xl mx-auto px-6 py-8 space-y-6">
      
      <!-- Section Header -->
      <div class="flex items-center justify-between border-b border-slate-200 pb-4">
        <div>
          <h2 class="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            Manager Ticket Approvals Queue
          </h2>
          <p class="text-xs text-slate-500 font-normal mt-0.5">
            Developer-raised tickets requiring manager authorization before appearing on the main canvas.
          </p>
        </div>
        <span class="text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200 px-3 py-1 rounded-full">
          {{ totalCount }} Pending Tickets
        </span>
      </div>

      <!-- Unauthorized Warning for Non-Managers -->
      <div *ngIf="!authService.isManager()" class="bg-amber-50 border border-amber-200 text-amber-900 p-6 rounded-2xl text-center space-y-2">
        <h3 class="text-sm font-bold">Manager Permission Required</h3>
        <p class="text-xs text-amber-700">Only Managers have authorization to review developer-submitted tickets.</p>
      </div>

      <!-- Empty State -->
      <div *ngIf="authService.isManager() && displayTasks.length === 0" 
           class="bg-white rounded-2xl p-12 text-center border border-slate-200 space-y-2">
        <h3 class="text-sm font-bold text-slate-800">No Pending Tickets</h3>
        <p class="text-xs text-slate-500">All developer-submitted tickets have been authorized and processed.</p>
      </div>

      <!-- Pending Developer Tickets Queue -->
      <div *ngIf="authService.isManager() && displayTasks.length > 0" class="grid grid-cols-1 md:grid-cols-2 gap-5">
        <div *ngFor="let task of displayTasks" 
             class="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm space-y-4 hover:border-slate-300 transition-all">
          
          <div class="flex items-center justify-between">
            <span [class]="getEnvBadgeClasses(task.environment)" class="text-[10px] font-bold uppercase px-2 py-0.5 rounded border">
              {{ task.environment }} ({{ task.environmentMultiplier }}x)
            </span>
            <span class="text-[11px] font-semibold text-amber-700 bg-amber-50 border border-amber-200 px-2.5 py-0.5 rounded-full">
              Awaiting Manager Approval
            </span>
          </div>

          <div>
            <h3 class="text-sm font-bold text-slate-900 mb-1">#{{ task.id }} - {{ task.title }}</h3>
            <p class="text-xs text-slate-600 leading-relaxed">{{ task.description || 'No description provided.' }}</p>
          </div>

          <!-- Submitted By Info -->
          <div class="bg-slate-50 rounded-xl p-3 border border-slate-100 text-xs flex items-center justify-between">
            <div class="flex items-center gap-2">
              <div class="w-6 h-6 rounded-full bg-indigo-600 text-white flex items-center justify-center text-[10px] font-bold">
                {{ task.createdBy?.fullName?.charAt(0) || 'D' }}
              </div>
              <div>
                <span class="text-slate-500 text-[10px] block font-medium">Raised By Developer</span>
                <span class="font-semibold text-slate-800">{{ task.createdBy?.fullName || 'Developer' }}</span>
              </div>
            </div>

            <div class="text-right">
              <span class="text-slate-500 text-[10px] block font-medium">Target Deadline</span>
              <span class="font-semibold text-slate-800">{{ task.deadline | date:'short' }}</span>
            </div>
          </div>

          <!-- Manager Approval Actions -->
          <div class="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
            <button (click)="openRejectionModal(task)"
                    class="px-4 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded-lg text-xs font-semibold transition-all">
              Reject Ticket
            </button>
            <button (click)="onApprove(task.id)"
                    class="px-4 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold shadow-sm transition-all">
              Approve & Display on Canvas
            </button>
          </div>

        </div>
      </div>

      <!-- Pagination Controls -->
      <div *ngIf="authService.isManager() && pendingPage && pendingPage.totalPages > 1"
           class="flex items-center justify-between border-t border-slate-200 pt-4 px-2">
        <span class="text-xs text-slate-600 font-medium">
          Showing Page {{ pendingPage.pageNumber + 1 }} of {{ pendingPage.totalPages }} ({{ pendingPage.totalElements }} Total)
        </span>
        <div class="flex items-center gap-2">
          <button (click)="prevPage()" [disabled]="!pendingPage.hasPrevious"
                  class="px-3 py-1.5 text-xs font-semibold rounded-lg border border-slate-200 text-slate-700 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed">
            Previous
          </button>
          <button (click)="nextPage()" [disabled]="!pendingPage.hasNext"
                  class="px-3 py-1.5 text-xs font-semibold rounded-lg border border-slate-200 text-slate-700 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed">
            Next
          </button>
        </div>
      </div>

      <!-- REJECTION REASON MODAL OVERLAY -->
      <div *ngIf="selectedTaskForRejection" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
        <div class="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl space-y-4 border border-slate-200">
          
          <div class="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 class="text-base font-bold text-slate-900">
              Reject Ticket #{{ selectedTaskForRejection.id }}
            </h3>
            <button (click)="closeRejectionModal()" class="text-slate-400 hover:text-slate-600 text-lg font-bold">
              &times;
            </button>
          </div>

          <p class="text-xs text-slate-600">
            Please provide a mandatory reason for rejecting this developer ticket ("{{ selectedTaskForRejection.title }}"):
          </p>

          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Reason for Rejection *</label>
            <textarea [(ngModel)]="rejectionReasonInput" rows="3" required
                      placeholder="e.g., Ticket scope overlaps with existing backend refactor, or missing dependency details..."
                      class="w-full bg-slate-50 border border-slate-200 rounded-xl p-3 text-xs text-slate-900 focus:outline-none focus:border-rose-500 focus:bg-white"></textarea>
          </div>

          <div class="flex items-center justify-end gap-2 pt-2">
            <button type="button" (click)="closeRejectionModal()"
                    class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-xs font-medium">
              Cancel
            </button>
            <button type="button" (click)="confirmRejection()" [disabled]="!rejectionReasonInput.trim()"
                    class="px-4 py-2 bg-rose-600 hover:bg-rose-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-sm">
              Confirm Rejection
            </button>
          </div>

        </div>
      </div>

    </div>
  `
})
export class ApprovalsComponent implements OnInit {

  selectedTaskForRejection: Task | null = null;
  rejectionReasonInput: string = '';
  pendingPage: PageResponse<Task> | null = null;
  currentPage: number = 0;
  pageSize: number = 6;

  constructor(
    public taskService: TaskService,
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    if (this.authService.isManager()) {
      this.loadPendingPage(0);
    }
  }

  loadPendingPage(page: number): void {
    this.currentPage = page;
    this.taskService.searchPendingTasks(this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        this.pendingPage = res;
      },
      error: () => {
        this.taskService.loadPendingTasks();
      }
    });
  }

  get displayTasks(): Task[] {
    return this.pendingPage ? this.pendingPage.content : this.taskService.pendingTasks();
  }

  get totalCount(): number {
    return this.pendingPage ? this.pendingPage.totalElements : this.taskService.pendingTasks().length;
  }

  prevPage(): void {
    if (this.pendingPage?.hasPrevious) {
      this.loadPendingPage(this.currentPage - 1);
    }
  }

  nextPage(): void {
    if (this.pendingPage?.hasNext) {
      this.loadPendingPage(this.currentPage + 1);
    }
  }

  onApprove(taskId: number): void {
    this.taskService.approveTask(taskId).subscribe({
      next: () => this.loadPendingPage(this.currentPage)
    });
  }

  openRejectionModal(task: Task): void {
    this.selectedTaskForRejection = task;
    this.rejectionReasonInput = '';
  }

  closeRejectionModal(): void {
    this.selectedTaskForRejection = null;
    this.rejectionReasonInput = '';
  }

  confirmRejection(): void {
    if (!this.selectedTaskForRejection || !this.rejectionReasonInput.trim()) return;
    const taskId = this.selectedTaskForRejection.id;
    const reason = this.rejectionReasonInput.trim();

    this.taskService.rejectTask(taskId, reason).subscribe({
      next: () => {
        this.closeRejectionModal();
        this.loadPendingPage(this.currentPage);
      },
      error: (err) => console.error('Failed to reject task:', err)
    });
  }

  getEnvBadgeClasses(env: string): string {
    switch (env) {
      case 'PRODUCTION': return 'bg-rose-50 text-rose-700 border-rose-200';
      case 'STAGING': return 'bg-amber-50 text-amber-700 border-amber-200';
      default: return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  }
}

