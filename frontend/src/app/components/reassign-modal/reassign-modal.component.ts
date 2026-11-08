import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Task } from '../../models/task.model';
import { User } from '../../models/user.model';
import { TaskService } from '../../services/task.service';

/**
 * Team Lead & Project Manager Reassign Task Modal Component.
 * Allows reassigning tasks to Developers or Testers.
 */
@Component({
  selector: 'app-reassign-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl space-y-5 border border-slate-200">
        
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h2 class="text-base font-bold text-slate-900 tracking-tight">Reassign Task</h2>
            <p class="text-xs text-slate-500 font-normal">Task #{{ task.id }} - {{ task.title }}</p>
          </div>
          <button (click)="close.emit()" class="text-slate-400 hover:text-slate-600 text-lg font-bold">
            &times;
          </button>
        </div>

        <form (ngSubmit)="onSubmit()" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">Select Assignee (Developer / Tester) *</label>
            <select [(ngModel)]="assigneeId" name="assigneeId"
                    class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
              <option [ngValue]="null">-- Unassigned --</option>
              <option *ngFor="let user of getAssignableUsers()" [value]="user.id">
                {{ user.fullName }} ({{ user.role === 'ROLE_TESTER' ? 'Tester' : 'Developer' }} • {{ user.department || 'Engineering' }})
              </option>
            </select>
          </div>

          <div class="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
            <button type="button" (click)="close.emit()"
                    class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition-all">
              Cancel
            </button>
            <button type="submit" [disabled]="loading"
                    class="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-sm transition-all flex items-center gap-2">
              <span *ngIf="loading" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              Confirm Reassignment
            </button>
          </div>
        </form>

      </div>
    </div>
  `
})
export class ReassignModalComponent implements OnInit {
  @Input({ required: true }) task!: Task;
  @Output() close = new EventEmitter<void>();

  assigneeId: number | null = null;
  loading = false;

  constructor(public taskService: TaskService) {}

  ngOnInit(): void {
    this.taskService.loadUsers();
    if (this.task && this.task.assignee) {
      this.assigneeId = this.task.assignee.id;
    }
  }

  getAssignableUsers(): User[] {
    return this.taskService.users().filter(u => u.role === 'ROLE_TEAM_MEMBER' || u.role === 'ROLE_TESTER');
  }

  onSubmit(): void {
    this.loading = true;
    const targetId = this.assigneeId ? Number(this.assigneeId) : null;
    this.taskService.reassignTask(this.task.id, targetId).subscribe({
      next: () => {
        this.loading = false;
        this.close.emit();
      },
      error: () => this.loading = false
    });
  }
}
