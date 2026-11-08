import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SettingsService } from '../../services/settings.service';
import { GravitySettings } from '../../models/settings.model';

/**
 * Team Lead & Project Manager Priora Engine Customization Page.
 * Allows Lead to customize formula factor toggles and define a Custom Dependency Weight Factor.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="max-w-4xl mx-auto px-6 py-8 space-y-8">
      
      <!-- Page Header -->
      <div class="border-b border-slate-200 pb-4">
        <h1 class="text-2xl font-extrabold text-slate-900 tracking-tight">Priora Engine Settings</h1>
        <p class="text-sm text-slate-500 mt-1">
          Customize active score calculation factors and define custom team lead dependency weights.
        </p>
      </div>

      <!-- Success Notification -->
      <div *ngIf="savedSuccess" 
           class="bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-xl text-xs font-semibold flex items-center gap-2">
        <svg class="w-4 h-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
        </svg>
        Priora settings updated successfully! Scores will recalculate dynamically across all canvas zones.
      </div>

      <!-- Formula Factor Toggles Section -->
      <div class="bg-white rounded-2xl p-6 border border-slate-200 shadow-xs space-y-6">
        <div>
          <h2 class="text-base font-bold text-slate-900 tracking-tight">Formula Weight Factors</h2>
          <p class="text-xs text-slate-500 mt-0.5">
            Toggle which mathematical variables influence the priority score calculation across all canvas zones.
          </p>
        </div>

        <div class="space-y-4 text-xs">
          
          <!-- Factor 1: Dependency Score -->
          <label class="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50 transition-all cursor-pointer">
            <input type="checkbox" [(ngModel)]="settings.enableDependencyScore"
                   class="mt-0.5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 w-4 h-4">
            <div>
              <span class="font-bold text-slate-800 block text-sm">Blast Radius / Downstream Dependencies</span>
              <span class="text-slate-500 leading-normal">
                Adds <strong>15.0 pts</strong> per direct blocked task + <strong>logarithmic points</strong> per indirect blocked task downstream.
              </span>
            </div>
          </label>

          <!-- Factor 2: Deadline Proximity -->
          <label class="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50 transition-all cursor-pointer">
            <input type="checkbox" [(ngModel)]="settings.enableDeadlineProximityScore"
                   class="mt-0.5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 w-4 h-4">
            <div>
              <span class="font-bold text-slate-800 block text-sm">Deadline Proximity & Overdue Escalation</span>
              <span class="text-slate-500 leading-normal">
                Smooth Sigmoid S-Curve up to <strong>100.0 pts</strong> + Overdue Escalation (<strong>100.0 to 130.0 pts max</strong>).
              </span>
            </div>
          </label>

          <!-- Factor 3: Stagnation Score -->
          <label class="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50 transition-all cursor-pointer">
            <input type="checkbox" [(ngModel)]="settings.enableStagnationScore"
                   class="mt-0.5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 w-4 h-4">
            <div>
              <span class="font-bold text-slate-800 block text-sm">Stagnation / Inactivity Penalty</span>
              <span class="text-slate-500 leading-normal">
                Day-based aging penalty (capped at <strong>+15.0 pts max</strong>). Pauses during <strong>IN_REVIEW</strong> and <strong>DONE</strong>.
              </span>
            </div>
          </label>

          <!-- Factor 4: Workload Density -->
          <label class="flex items-start gap-3 p-3.5 rounded-xl border border-slate-200 hover:bg-slate-50 transition-all cursor-pointer">
            <input type="checkbox" [(ngModel)]="settings.enableWorkloadDensityScore"
                   class="mt-0.5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 w-4 h-4">
            <div>
              <span class="font-bold text-slate-800 block text-sm">Assignee Workload Density Multiplier</span>
              <span class="text-slate-500 leading-normal">
                Applies scalar boost when assigned developer has <strong>> 3 active incomplete tasks</strong>.
              </span>
            </div>
          </label>

        </div>
      </div>

      <!-- CUSTOM TEAM LEAD DEPENDENCY / WEIGHT FACTOR SECTION -->
      <div class="bg-white rounded-2xl p-6 border border-slate-200 shadow-xs space-y-6">
        <div>
          <h2 class="text-base font-bold text-slate-900 tracking-tight">Custom Dependency Weight</h2>
          <p class="text-xs text-slate-500 mt-0.5">
            Define a custom dependency weight factor. When enabled, this dependency selection will be available during task creation.
          </p>
        </div>

        <div class="space-y-4 text-xs">
          <!-- Enable Toggle -->
          <label class="flex items-center gap-3 p-3.5 rounded-xl border border-indigo-200 bg-indigo-50/50 cursor-pointer">
            <input type="checkbox" [(ngModel)]="settings.enableCustomFactor"
                   class="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 w-4 h-4">
            <div>
              <span class="font-bold text-slate-900 block">Enable Custom Dependency Option</span>
              <span class="text-slate-500">Makes the custom dependency selection field available in Create Task Modal.</span>
            </div>
          </label>

          <!-- Custom Factor Configuration Form -->
          <div *ngIf="settings.enableCustomFactor" class="grid grid-cols-1 sm:grid-cols-2 gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Custom Factor Name *</label>
              <input type="text" [(ngModel)]="settings.customFactorName" placeholder="e.g. Security & Risk Compliance"
                     class="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600">
            </div>

            <div>
              <label class="block text-xs font-semibold text-slate-700 mb-1">Custom Factor Score (Points) *</label>
              <input type="number" step="0.5" [(ngModel)]="settings.customFactorPoints" placeholder="15.0"
                     class="w-full bg-white border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-indigo-600">
            </div>
          </div>
        </div>
      </div>

      <!-- Save Settings Button -->
      <div class="flex justify-end pt-2">
        <button (click)="onSave()" [disabled]="saving"
                class="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white font-semibold text-xs rounded-xl shadow-xs transition-all flex items-center gap-2">
          <span *ngIf="saving" class="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
          Save Priora Engine Settings
        </button>
      </div>

    </div>
  `
})
export class SettingsComponent implements OnInit {
  settings: GravitySettings = {
    id: 1,
    enableDependencyScore: true,
    enableDeadlineProximityScore: true,
    enableStagnationScore: true,
    enableWorkloadDensityScore: true,
    requireApprovalForDevTasks: false,
    enableCustomFactor: false,
    customFactorName: 'Security Risk Weight',
    customFactorPoints: 15.0
  };

  saving = false;
  savedSuccess = false;

  constructor(private settingsService: SettingsService) {}

  ngOnInit(): void {
    this.settingsService.loadSettings();
    setTimeout(() => {
      this.settings = { ...this.settingsService.settings() };
    }, 100);
  }

  onSave(): void {
    this.saving = true;
    this.savedSuccess = false;

    this.settingsService.updateSettings(this.settings).subscribe({
      next: (updated) => {
        this.settings = updated;
        this.saving = false;
        this.savedSuccess = true;
        setTimeout(() => this.savedSuccess = false, 4000);
      },
      error: () => this.saving = false
    });
  }
}
