import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';

export interface SfUser {
  userId: number;
  email: string;
  fullName: string;
  customerId: number;
}

@Injectable({ providedIn: 'root' })
export class StorefrontAuthService {
  private readonly TOKEN_KEY = 'sf_access_token';
  private readonly USER_KEY = 'sf_user';
  private readonly API = `${environment.apiUrl}/storefront/auth`;

  private userSubject = new BehaviorSubject<SfUser | null>(this.loadUser());
  user$ = this.userSubject.asObservable();
  isLoggedIn$ = this.user$.pipe(map(u => !!u));

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string): Observable<SfUser> {
    return this.http.post<any>(`${this.API}/login`, { email, password }).pipe(
      tap(res => {
        localStorage.setItem(this.TOKEN_KEY, res.accessToken);
        const user: SfUser = {
          userId: res.userId,
          email: res.email,
          fullName: res.fullName,
          customerId: res.customerId
        };
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
        this.userSubject.next(user);
      }),
      map(res => ({
        userId: res.userId,
        email: res.email,
        fullName: res.fullName,
        customerId: res.customerId
      }))
    );
  }

  register(data: {
    email: string;
    password: string;
    fullName: string;
    phone: string;
    address: string;
    city: string;
    state: string;
    pincode: string;
  }): Observable<any> {
    return this.http.post(`${this.API}/register`, data);
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.userSubject.next(null);
    this.router.navigate(['/store']);
  }

  getProfile(): Observable<any> {
    return this.http.get(`${this.API}/profile`, { headers: this.authHeaders() });
  }

  updateProfile(data: {
    fullName: string;
    phone: string;
    address: string;
    city: string;
    state: string;
    pincode: string;
  }): Observable<any> {
    return this.http.put(`${this.API}/profile`, data, { headers: this.authHeaders() }).pipe(
      tap((res: any) => {
        // Update stored user name if changed
        const current = this.userSubject.value;
        if (current) {
          const updated = { ...current, fullName: data.fullName };
          localStorage.setItem(this.USER_KEY, JSON.stringify(updated));
          this.userSubject.next(updated);
        }
      })
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<any> {
    return this.http.put(
      `${this.API}/change-password`,
      { currentPassword, newPassword },
      { headers: this.authHeaders() }
    );
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getCurrentUser(): SfUser | null {
    return this.userSubject.value;
  }

  isLoggedIn(): boolean {
    return !!this.userSubject.value;
  }

  private loadUser(): SfUser | null {
    try {
      const stored = localStorage.getItem(this.USER_KEY);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  }

  private authHeaders(): HttpHeaders {
    const token = this.getToken();
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
