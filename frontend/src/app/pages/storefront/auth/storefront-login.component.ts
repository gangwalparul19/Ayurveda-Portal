import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { StorefrontAuthService } from '../../../services/storefront-auth.service';

@Component({
  selector: 'app-storefront-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './storefront-login.component.html',
  styleUrls: ['./storefront-login.component.scss']
})
export class StorefrontLoginComponent {
  email = '';
  password = '';
  isLoading = false;
  errorMessage = '';

  constructor(
    private sfAuth: StorefrontAuthService,
    private router: Router
  ) {}

  onSubmit(): void {
    if (!this.email || !this.password) {
      this.errorMessage = 'Please enter your email and password.';
      return;
    }
    this.isLoading = true;
    this.errorMessage = '';

    this.sfAuth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/store/profile']),
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.message || 'Invalid email or password.';
      }
    });
  }
}
