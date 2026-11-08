import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { CanvasComponent } from './components/canvas/canvas.component';
import { SettingsComponent } from './pages/settings/settings.component';
import { ProfileComponent } from './pages/profile/profile.component';
import { UserManagementComponent } from './pages/user-management/user-management.component';
import { ApprovalsComponent } from './pages/approvals/approvals.component';
import { AuthService } from './services/auth.service';

const authGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.isAuthenticated()) return true;
  return router.parseUrl('/login');
};

const managerGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.isManager()) return true;
  return router.parseUrl('/canvas');
};

const adminGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.isAdmin()) return true;
  return router.parseUrl('/canvas');
};

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'canvas', component: CanvasComponent, canActivate: [authGuard] },
  { path: 'settings', component: SettingsComponent, canActivate: [authGuard, managerGuard] },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  { path: 'users', component: UserManagementComponent, canActivate: [authGuard, adminGuard] },
  { path: 'approvals', component: ApprovalsComponent, canActivate: [authGuard, managerGuard] },
  { path: '', redirectTo: 'canvas', pathMatch: 'full' },
  { path: '**', redirectTo: 'canvas' }
];
