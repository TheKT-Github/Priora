import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TaskService } from '../../services/task.service';
import { TaskCardComponent } from '../task-card/task-card.component';

/**
 * 3-Zone Visual Priority Dashboard Canvas.
 * Standardized uniform grid layout (`grid-cols-1 md:grid-cols-2 lg:grid-cols-3`) across all 3 zones.
 * Zero emojis - clean executive presentation.
 */
@Component({
  selector: 'app-canvas',
  standalone: true,
  imports: [CommonModule, TaskCardComponent],
  template: `
    <div class="max-w-7xl mx-auto px-6 py-8 space-y-10">
      
      <!-- Error Message Banner -->
      <div *ngIf="taskService.errorMessage()" 
           class="bg-rose-50 border border-rose-200 text-rose-800 p-4 rounded-xl text-xs font-semibold flex items-center justify-between shadow-xs">
        <span>{{ taskService.errorMessage() }}</span>
        <button (click)="taskService.loadTasks()" class="px-3 py-1 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-xs transition-all">
          Retry Connection
        </button>
      </div>

      <!-- Loading Spinner -->
      <div *ngIf="taskService.loading()" class="flex flex-col items-center justify-center py-16 space-y-3">
        <div class="w-8 h-8 border-3 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
        <p class="text-xs font-semibold text-slate-500">Calculating Dynamic Priora Scores...</p>
      </div>

      <ng-container *ngIf="!taskService.loading()">

        <!-- ZONE 1: FOCUS ZONE (Score >= 80.0) -->
        <section class="space-y-4">
          <div class="flex items-center justify-between border-b border-indigo-100 pb-3">
            <div class="flex items-center gap-3">
              <div class="w-3 h-3 rounded-full bg-indigo-600"></div>
              <h2 class="text-lg font-bold text-slate-900 tracking-tight">Focus Zone</h2>
              <span class="text-xs font-semibold text-indigo-700 bg-indigo-50 border border-indigo-200 px-2.5 py-0.5 rounded-full">
                Score &ge; 80.0
              </span>
            </div>
            <span class="text-xs font-semibold text-slate-500">
              {{ taskService.focusZoneTasks().length }} Critical Tasks
            </span>
          </div>

          <!-- Empty Focus State -->
          <div *ngIf="taskService.focusZoneTasks().length === 0" 
               class="bg-white rounded-2xl p-8 text-center border border-dashed border-slate-200 text-slate-400 text-xs font-medium">
            No critical tasks in the Focus Zone.
          </div>

          <!-- Uniform Grid Layout -->
          <div *ngIf="taskService.focusZoneTasks().length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <app-task-card *ngFor="let task of taskService.focusZoneTasks()" [task]="task">
            </app-task-card>
          </div>
        </section>

        <!-- ZONE 2: DRIFTING ZONE (Score 50.0 - 79.9) -->
        <section class="space-y-4">
          <div class="flex items-center justify-between border-b border-slate-200 pb-3">
            <div class="flex items-center gap-3">
              <div class="w-3 h-3 rounded-full bg-amber-500"></div>
              <h2 class="text-lg font-bold text-slate-900 tracking-tight">Drifting Zone</h2>
              <span class="text-xs font-semibold text-amber-800 bg-amber-50 border border-amber-200 px-2.5 py-0.5 rounded-full">
                Score 50.0 - 79.9
              </span>
            </div>
            <span class="text-xs font-semibold text-slate-500">
              {{ taskService.driftingZoneTasks().length }} Active Tasks
            </span>
          </div>

          <!-- Empty Drifting State -->
          <div *ngIf="taskService.driftingZoneTasks().length === 0" 
               class="bg-white rounded-2xl p-8 text-center border border-dashed border-slate-200 text-slate-400 text-xs font-medium">
            No active tasks in the Drifting Zone.
          </div>

          <!-- Uniform Grid Layout -->
          <div *ngIf="taskService.driftingZoneTasks().length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <app-task-card *ngFor="let task of taskService.driftingZoneTasks()" [task]="task">
            </app-task-card>
          </div>
        </section>

        <!-- ZONE 3: ORBIT ZONE (Score < 50.0 / Completed Tasks) -->
        <section class="space-y-4">
          <div class="flex items-center justify-between border-b border-slate-200 pb-3">
            <div class="flex items-center gap-3">
              <div class="w-3 h-3 rounded-full bg-slate-400"></div>
              <h2 class="text-lg font-bold text-slate-900 tracking-tight">Orbit Zone</h2>
              <span class="text-xs font-semibold text-slate-700 bg-slate-100 border border-slate-200 px-2.5 py-0.5 rounded-full">
                Score &lt; 50.0 / Done
              </span>
            </div>
            <span class="text-xs font-semibold text-slate-500">
              {{ taskService.orbitZoneTasks().length }} Backlog / Completed Tasks
            </span>
          </div>

          <!-- Empty Orbit State -->
          <div *ngIf="taskService.orbitZoneTasks().length === 0" 
               class="bg-white rounded-2xl p-8 text-center border border-dashed border-slate-200 text-slate-400 text-xs font-medium">
            No backlog or completed tasks in the Orbit Zone.
          </div>

          <!-- Uniform Grid Layout (Matches Focus Zone: 3 Columns) -->
          <div *ngIf="taskService.orbitZoneTasks().length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            <app-task-card *ngFor="let task of taskService.orbitZoneTasks()" [task]="task">
            </app-task-card>
          </div>
        </section>

      </ng-container>

    </div>
  `
})
export class CanvasComponent implements OnInit {
  constructor(public taskService: TaskService) {}

  ngOnInit(): void {
    this.taskService.loadTasks();
  }
}
