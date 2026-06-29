import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { StorefrontAuthService, SfUser } from '../../../services/storefront-auth.service';

@Component({
  selector: 'app-sf-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {
  user: SfUser | null = null;

  // Profile form
  fullName = '';
  phone = '';
  address = '';
  city = '';
  state = '';
  pincode = '';

  // Password form
  currentPassword = '';
  newPassword = '';
  confirmPassword = '';

  profileLoading = false;
  profileSuccess = '';
  profileError = '';

  passwordLoading = false;
  passwordSuccess = '';
  passwordError = '';

  activeTab: 'profile' | 'password' = 'profile';

  constructor(
    private sfAuth: StorefrontAuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.user = this.sfAuth.getCurrentUser();
    if (!this.user) {
      this.router.navigate(['/store/login']);
      return;
    }
    this.loadProfile();
  }

  loadProfile(): void {
    const token = this.sfAuth.getToken();
    if (!token) return;

    this.sfAuth.getProfile().subscribe({
      next: (profile: any) => {
        this.fullName = profile.fullName || '';
        this.phone = profile.phone || '';
        this.address = profile.address || '';
        this.city = profile.city || '';
        this.state = profile.state || '';
        this.pincode = profile.pincode || '';
      },
      error: () => {
        // Fill from local cache
        if (this.user) {
          this.fullName = this.user.fullName || '';
        }
      }
    });
  }

  saveProfile(): void {
    this.profileLoading = true;
    this.profileSuccess = '';
    this.profileError = '';

    this.sfAuth.updateProfile({
      fullName: this.fullName,
      phone: this.phone,
      address: this.address,
      city: this.city,
      state: this.state,
      pincode: this.pincode
    }).subscribe({
      next: () => {
        this.profileLoading = false;
        this.profileSuccess = 'Profile updated successfully.';
        this.user = this.sfAuth.getCurrentUser();
        setTimeout(() => (this.profileSuccess = ''), 3000);
      },
      error: (err: any) => {
        this.profileLoading = false;
        this.profileError = err?.error?.message || 'Failed to update profile.';
      }
    });
  }

  changePassword(): void {
    if (!this.currentPassword || !this.newPassword) {
      this.passwordError = 'Please fill in all password fields.';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.passwordError = 'New passwords do not match.';
      return;
    }
    this.passwordLoading = true;
    this.passwordSuccess = '';
    this.passwordError = '';

    this.sfAuth.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: () => {
        this.passwordLoading = false;
        this.passwordSuccess = 'Password changed successfully.';
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
        setTimeout(() => (this.passwordSuccess = ''), 3000);
      },
      error: (err: any) => {
        this.passwordLoading = false;
        this.passwordError = err?.error?.message || 'Failed to change password.';
      }
    });
  }

  logout(): void {
    this.sfAuth.logout();
  }

  getInitials(): string {
    if (!this.user?.fullName) return '?';
    return this.user.fullName
      .split(' ')
      .map(n => n[0])
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }
}
