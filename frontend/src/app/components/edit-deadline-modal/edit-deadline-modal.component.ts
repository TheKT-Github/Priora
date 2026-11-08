import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Task } from '../../models/task.model';
import { TaskService } from '../../services/task.service';

/**
 * Manager-Only Edit Deadline Modal Component.
 * Restricts selection to current/future dates using the min datetime attribute.
 */
@Component({
  selector: 'app-edit-deadline-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl space-y-5 border border-slate-200">
        
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h2 class="text-base font-bold text-slate-900 tracking-tight">Adjust Task Deadline</h2>
            <p class="text-xs text-slate-500 font-normal">Task #{{ task.id }} - {{ task.title }}</p>
          </div>
          <button (click)="close.emit()" class="text-slate-400 hover:text-slate-600 text-lg font-bold">
            &times;
          </button>
        </div>

        <form (ngSubmit)="onSubmit()" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-slate-700 mb-1">New Target Deadline *</label>
            <input type="datetime-local" [(ngModel)]="deadlineInput" name="deadlineInput" required [min]="minDateTimeInput"
                   class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600 focus:bg-white">
            <span class="text-[10px] text-slate-400 block mt-0.5">Past dates prior to current date are disabled</span>
          </div>

          <div class="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
            <button type="button" (click)="close.emit()"
                    class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition-all">
              Cancel
            </button>
            <button type="submit" [disabled]="!deadlineInput || loading"
                    class="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-sm transition-all flex items-center gap-2">
              <span *ngIf="loading" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
              Update Deadline
            </button>
          </div>
        </form>

      </div>
    </div>
  `
})
export class EditDeadlineModalComponent implements OnInit {
  @Input({ required: true }) task!: Task;
  @Output() close = new EventEmitter<void>();

  deadlineInput = '';
  minDateTimeInput = '';
  loading = false;

  constructor(private taskService: TaskService) {}

  ngOnInit(): void {
    const now = new Date();
    const tzOffset = now.getTimezoneOffset() * 60000;
    const localNow = new Date(now.getTime() - tzOffset);
    this.minDateTimeInput = localNow.toISOString().slice(0, 16);

    if (this.task && this.task.deadline) {
      const d = new Date(this.task.deadline);
      const localD = new Date(d.getTime() - tzOffset);
      this.deadlineInput = localD.toISOString().slice(0, 16);
    } else {
      this.deadlineInput = this.minDateTimeInput;
    }
  }

  onSubmit(): void {
    if (!this.deadlineInput) return;
    this.loading = true;

    const isoDate = new Date(this.deadlineInput).toISOString();
    this.taskService.updateDeadline(this.task.id, isoDate).subscribe({
      next: () => {
        this.loading = false;
        this.close.emit();
      },
      error: () => this.loading = false
    });
  }
}
