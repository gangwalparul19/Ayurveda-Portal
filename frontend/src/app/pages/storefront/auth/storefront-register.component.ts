import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { StorefrontAuthService } from '../../../services/storefront-auth.service';

@Component({
  selector: 'app-storefront-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './storefront-register.component.html',
  styleUrls: ['./storefront-register.component.scss']
})
export class StorefrontRegisterComponent {
  fullName = '';
  email = '';
  phone = '';
  password = '';
  address = '';
  city = '';
  state = '';
  pincode = '';

  isLoading = false;
  errorMessage = '';
  successMessage = '';

  constructor(
    private sfAuth: StorefrontAuthService,
    private router: Router
  ) {}

  onSubmit(): void {
    if (!this.fullName || !this.email || !this.password) {
      this.errorMessage = 'Full name, email, and password are required.';
      return;
    }
    this.isLoading = true;
    this.errorMessage = '';

    this.sfAuth.register({
      fullName: this.fullName,
      email: this.email,
      phone: this.phone,
      password: this.password,
      address: this.address,
      city: this.city,
      state: this.state,
      pincode: this.pincode
    }).subscribe({
      next: () => {
        this.successMessage = 'Account created! Redirecting to login…';
        setTimeout(() => this.router.navigate(['/store/login']), 1500);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Registration failed. Please try again.';
      }
    });
  }
}
