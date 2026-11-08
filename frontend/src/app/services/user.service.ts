import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { User, CreateUserRequest, ChangePasswordRequest } from '../models/user.model';
import { AuthService } from './auth.service';

/**
 * Service managing User Accounts, Admin Account Creation, and Password Updates.
 */
@Injectable({
  providedIn: 'root'
})
export class UserService {
  private apiUrl = 'http://localhost:8081/api';

  public users = signal<User[]>([]);
  public loading = signal<boolean>(false);
  public successMessage = signal<string | null>(null);

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  public loadUsers(): void {
    this.loading.set(true);
    this.http.get<User[]>(`${this.apiUrl}/users`, { headers: this.authService.getHeaders() }).subscribe({
      next: (data) => {
        this.users.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load users:', err);
        this.loading.set(false);
      }
    });
  }

  public registerUser(req: CreateUserRequest): Observable<User> {
    this.loading.set(true);
    return this.http.post<User>(`${this.apiUrl}/users/register`, req).pipe(
      tap((newUser) => {
        this.loading.set(false);
      }),
      catchError((err) => {
        this.loading.set(false);
        return throwError(() => err);
      })
    );
  }

  public createUser(req: CreateUserRequest): Observable<User> {
    this.loading.set(true);
    return this.http.post<User>(`${this.apiUrl}/users`, req, { headers: this.authService.getHeaders() }).pipe(
      tap((newUser) => {
        this.users.set([...this.users(), newUser]);
        this.loading.set(false);
        this.successMessage.set(`Account created successfully for ${newUser.fullName} (${newUser.username})!`);
        setTimeout(() => this.successMessage.set(null), 4000);
      }),
      catchError((err) => {
        this.loading.set(false);
        return throwError(() => err);
      })
    );
  }

  public changePassword(req: ChangePasswordRequest): Observable<void> {
    this.loading.set(true);
    return this.http.put<void>(`${this.apiUrl}/users/change-password`, req, { headers: this.authService.getHeaders() }).pipe(
      tap(() => {
        this.loading.set(false);
        this.authService.updatePasswordInSession(req.newPassword);
        this.successMessage.set('Password updated successfully!');
        setTimeout(() => this.successMessage.set(null), 4000);
      }),
      catchError((err) => {
        this.loading.set(false);
        return throwError(() => err);
      })
    );
  }
}
