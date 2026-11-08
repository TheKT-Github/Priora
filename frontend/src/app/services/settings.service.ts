import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { GravitySettings } from '../models/settings.model';
import { AuthService } from './auth.service';

/**
 * Settings Service powered by Angular 16 Signals.
 * Manages Manager Customizable Gravity Score Engine factor toggles and Ticket Approval policies.
 */
@Injectable({
  providedIn: 'root'
})
export class SettingsService {
  private apiUrl = 'http://localhost:8081/api';

  public settings = signal<GravitySettings>({
    enableDependencyScore: true,
    enableDeadlineProximityScore: true,
    enableStagnationScore: true,
    enableWorkloadDensityScore: true,
    requireApprovalForDevTasks: true
  });

  public loading = signal<boolean>(false);
  public successMessage = signal<string | null>(null);

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  public loadSettings(): void {
    this.loading.set(true);
    this.http.get<GravitySettings>(`${this.apiUrl}/settings`, { headers: this.authService.getHeaders() }).subscribe({
      next: (data) => {
        this.settings.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load settings:', err);
        this.loading.set(false);
      }
    });
  }

  public updateSettings(newSettings: GravitySettings): Observable<GravitySettings> {
    this.loading.set(true);
    return this.http.put<GravitySettings>(`${this.apiUrl}/settings`, newSettings, { headers: this.authService.getHeaders() }).pipe(
      tap((updated) => {
        this.settings.set(updated);
        this.loading.set(false);
        this.successMessage.set('Gravity Engine & Approval Policy successfully updated!');
        setTimeout(() => this.successMessage.set(null), 4000);
      }),
      catchError((err) => {
        this.loading.set(false);
        return throwError(() => err);
      })
    );
  }
}
