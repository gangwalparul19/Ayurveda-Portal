import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { AuthResponse, LoginRequest, User, TenantUiConfig } from '../models';
import { ThemeService } from '../tenant/theme.service';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly API_URL = environment.apiUrl;
  private readonly TOKEN_KEY = 'access_token';
  private readonly REFRESH_KEY = 'refresh_token';
  private readonly USER_KEY = 'user_data';

  private currentUserSubject = new BehaviorSubject<User | null>(this.loadStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router,
    private themeService: ThemeService
  ) {
    // Apply stored theme on service init
    const storedUser = this.loadStoredUser();
    if (storedUser) {
      const uiConfig = this.loadStoredUiConfig();
      if (uiConfig) {
        this.themeService.applyTheme(uiConfig);
      }
    }
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/auth/login`, request).pipe(
      tap(response => {
        this.storeTokens(response.accessToken, response.refreshToken);
        this.storeUser(response);
        if (response.uiConfig) {
          this.storeUiConfig(response.uiConfig);
          this.themeService.applyTheme(response.uiConfig);
        }
        this.currentUserSubject.next(this.mapToUser(response));
        this.isAuthenticatedSubject.next(true);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    localStorage.removeItem(this.USER_KEY);
    localStorage.removeItem('ui_config');
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);
    this.themeService.resetTheme();
    this.router.navigate(['/login']);
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http.post<AuthResponse>(`${this.API_URL}/auth/refresh`, {}, {
      headers: { 'X-Refresh-Token': refreshToken || '' }
    }).pipe(
      tap(response => {
        this.storeTokens(response.accessToken, response.refreshToken);
      })
    );
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_KEY);
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  hasRole(role: string): boolean {
    const user = this.getCurrentUser();
    return user?.role === role;
  }

  hasAnyRole(...roles: string[]): boolean {
    const user = this.getCurrentUser();
    return user ? roles.includes(user.role) : false;
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }

  private storeTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(this.TOKEN_KEY, accessToken);
    localStorage.setItem(this.REFRESH_KEY, refreshToken);
  }

  private storeUser(response: AuthResponse): void {
    const userData = this.mapToUser(response);
    localStorage.setItem(this.USER_KEY, JSON.stringify(userData));
  }

  private storeUiConfig(config: TenantUiConfig): void {
    localStorage.setItem('ui_config', JSON.stringify(config));
  }

  private loadStoredUser(): User | null {
    const stored = localStorage.getItem(this.USER_KEY);
    return stored ? JSON.parse(stored) : null;
  }

  private loadStoredUiConfig(): TenantUiConfig | null {
    const stored = localStorage.getItem('ui_config');
    return stored ? JSON.parse(stored) : null;
  }

  private mapToUser(response: AuthResponse): User {
    return {
      userId: response.userId,
      username: response.username,
      fullName: response.fullName,
      email: response.email,
      role: response.role,
      tenantKey: response.tenantKey,
      companyName: response.companyName
    };
  }
}
