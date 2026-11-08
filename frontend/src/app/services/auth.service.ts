import { Injectable, signal, computed } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { User } from '../models/user.model';

/**
 * Authentication Service managing Security Context, Credentials, and Roles
 * (Project Manager, Team Lead, Developer, Tester).
 *
 * Credential storage notes:
 * - The application uses HTTP Basic Authentication. Every authenticated request
 *   sends the Authorization header with a Base64-encoded "username:password" token.
 * - Base64 encoding is NOT encryption. A stored token is reversible and still
 *   contains reusable credentials if stolen.
 * - To avoid persisting the raw password, only the pre-computed Base64 token is
 *   kept in sessionStorage (key: 'gravity_token'). The raw password is discarded
 *   from memory after the token is encoded.
 * - sessionStorage is used instead of localStorage: it is scoped to the browser tab
 *   and cleared when the tab is closed. It does not survive cross-tab or cross-session
 *   access. This is a meaningful improvement over localStorage for short-lived sessions.
 * - sessionStorage does not protect against XSS. If an attacker can execute
 *   JavaScript on the page, the token can be read. Backend authentication of every
 *   request and a short session scope are the primary mitigations available within
 *   the HTTP Basic architecture.
 * - The current username (not password) is retained in memory (currentUser signal)
 *   for role checks and the Authorization header rebuild needed after a password change.
 *   It is never written to sessionStorage separately.
 * - Migrating to a session-cookie or short-lived token scheme (e.g. JWT with an
 *   auth endpoint) would allow the raw credential to remain only in memory, which
 *   is a stronger posture. That migration is out of scope for Phase 5.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8081/api';

  public currentUser = signal<User | null>(null);
  public isAuthenticated = computed(() => this.currentUser() !== null);

  // Specific Role Indicators
  public isProjectManager = computed(() => this.currentUser()?.role === 'ROLE_PROJECT_MANAGER');
  public isTeamLead = computed(() => this.currentUser()?.role === 'ROLE_TEAM_LEAD');
  public isTester = computed(() => this.currentUser()?.role === 'ROLE_TESTER');
  public isDeveloper = computed(() => this.currentUser()?.role === 'ROLE_TEAM_MEMBER');

  // Combined Lead Authority (Project Managers and Team Leads)
  public hasLeadPermissions = computed(() =>
    this.currentUser()?.role === 'ROLE_PROJECT_MANAGER' ||
    this.currentUser()?.role === 'ROLE_TEAM_LEAD'
  );

  // Backward-compatibility aliases
  public isAdmin = computed(() => this.isProjectManager());
  public isManager = computed(() => this.hasLeadPermissions());

  /**
   * In-memory reference to the current username. Used to reconstruct the
   * Authorization header after a password change. Never persisted to storage.
   */
  private activeUsername: string | null = null;

  constructor(private http: HttpClient) {
    // Attempt to restore session from sessionStorage on app load.
    // Only the Base64 token is read — raw password is not stored anywhere.
    const stored = sessionStorage.getItem('gravity_token');
    if (stored) {
      this.activeUsername = this.extractUsernameFromToken(stored);
      this.loadCurrentUser();
    }
  }

  /**
   * Builds the Authorization header using the token stored in sessionStorage.
   * Returns empty headers if no token is present (unauthenticated).
   */
  public getHeaders(): HttpHeaders {
    const token = sessionStorage.getItem('gravity_token');
    if (!token) return new HttpHeaders();
    return new HttpHeaders({
      'Authorization': `Basic ${token}`,
      'Content-Type': 'application/json'
    });
  }

  /**
   * Authenticates the user via HTTP Basic against /api/auth/me.
   * Only the Base64 token (not the raw password) is persisted to sessionStorage.
   * The raw password parameter is used once to construct the token and then discarded.
   */
  public login(username: string, pass: string): Observable<User> {
    const token = btoa(`${username}:${pass}`);
    const headers = new HttpHeaders({
      'Authorization': `Basic ${token}`,
      'Content-Type': 'application/json'
    });

    return this.http.get<User>(`${this.apiUrl}/auth/me`, { headers }).pipe(
      tap((user) => {
        this.activeUsername = username;
        this.currentUser.set(user);
        // Store only the Base64 token. Raw password is discarded here.
        sessionStorage.setItem('gravity_token', token);
      }),
      catchError((err) => {
        this.logout();
        return throwError(() => err);
      })
    );
  }

  /**
   * Refreshes the current user profile from the backend using the stored token.
   * Called on app init to restore authenticated state from sessionStorage.
   */
  public loadCurrentUser(): void {
    const headers = this.getHeaders();
    if (!headers.has('Authorization')) return;

    this.http.get<User>(`${this.apiUrl}/auth/me`, { headers }).subscribe({
      next: (user) => this.currentUser.set(user),
      error: () => this.logout()
    });
  }

  /**
   * Called after a successful password change to update the in-memory token.
   * The old token is replaced with a new one built from the current username
   * and the new password. The new password is used once to produce the token
   * and is not retained.
   */
  public updatePasswordInSession(newPassword: string): void {
    if (this.activeUsername) {
      const updatedToken = btoa(`${this.activeUsername}:${newPassword}`);
      sessionStorage.setItem('gravity_token', updatedToken);
      // newPassword reference leaves scope here; not stored elsewhere.
    }
  }

  public logout(): void {
    this.currentUser.set(null);
    this.activeUsername = null;
    sessionStorage.removeItem('gravity_token');
  }

  /**
   * Decodes the stored Base64 Basic token to recover the username portion.
   * Used on app init to track the current username in memory without storing it.
   * Returns null if the token is malformed.
   */
  private extractUsernameFromToken(token: string): string | null {
    try {
      const decoded = atob(token);
      const colonIndex = decoded.indexOf(':');
      return colonIndex !== -1 ? decoded.substring(0, colonIndex) : null;
    } catch {
      return null;
    }
  }
}
