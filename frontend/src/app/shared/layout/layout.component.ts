import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../core/models';
import { IconComponent } from '../ui/icon.component';

interface NavItem {
  icon: string;
  label: string;
  route: string;
  roles?: string[];
}

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  templateUrl: './layout.component.html',
  styleUrls: ['./layout.component.scss']
})
export class LayoutComponent implements OnInit, OnDestroy {
  user: User | null = null;

  /** Desktop rail collapse (full vs icon-only). */
  sidebarCollapsed = false;

  /** Mobile/tablet off-canvas drawer open state. */
  mobileNavOpen = false;

  currentYear = new Date().getFullYear();

  navItems: NavItem[] = [
    { icon: 'dashboard', label: 'Dashboard', route: '/dashboard' },
    { icon: 'orders', label: 'Orders', route: '/orders', roles: ['TENANT_ADMIN', 'MANAGER', 'SALESPERSON', 'DISPATCHER'] },
    { icon: 'products', label: 'Products', route: '/products', roles: ['TENANT_ADMIN', 'MANAGER', 'SALESPERSON'] },
    { icon: 'customers', label: 'Customers', route: '/customers', roles: ['TENANT_ADMIN', 'MANAGER', 'SALESPERSON'] },
    { icon: 'dispatch', label: 'Dispatch', route: '/dispatch', roles: ['TENANT_ADMIN', 'MANAGER', 'DISPATCHER'] },
    { icon: 'billing', label: 'Billing', route: '/billing', roles: ['TENANT_ADMIN', 'MANAGER'] },
    { icon: 'reports', label: 'Reports', route: '/reports', roles: ['TENANT_ADMIN', 'MANAGER'] },
    { icon: 'targets', label: 'Targets', route: '/targets', roles: ['TENANT_ADMIN'] },
    { icon: 'users', label: 'Users', route: '/users', roles: ['TENANT_ADMIN'] },
    { icon: 'star', label: 'Reviews', route: '/reviews', roles: ['TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER'] },
    { icon: 'tag', label: 'Coupons', route: '/coupons', roles: ['TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER'] },
  ];

  adminNavItems: NavItem[] = [
    { icon: 'tenants', label: 'Tenants', route: '/admin/tenants', roles: ['SUPER_ADMIN'] },
    { icon: 'settings', label: 'Platform', route: '/admin/analytics', roles: ['SUPER_ADMIN'] },
  ];

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.user = user;
    });
  }

  ngOnDestroy(): void {
    this.unlockBodyScroll();
  }

  /**
   * Hamburger behavior depends on viewport:
   * - Desktop (>= 1024px): toggle the collapsed rail.
   * - Mobile/tablet (< 1024px): toggle the off-canvas drawer.
   */
  toggleSidebar(): void {
    if (this.isMobileViewport()) {
      this.setMobileNav(!this.mobileNavOpen);
    } else {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    }
  }

  /** Close the drawer (used by backdrop click and nav selection). */
  closeMobileNav(): void {
    this.setMobileNav(false);
  }

  /** Called when a nav item is clicked — close the drawer on mobile. */
  onNavSelect(): void {
    if (this.mobileNavOpen) {
      this.setMobileNav(false);
    }
  }

  private setMobileNav(open: boolean): void {
    this.mobileNavOpen = open;
    if (open) {
      this.lockBodyScroll();
    } else {
      this.unlockBodyScroll();
    }
  }

  private isMobileViewport(): boolean {
    return typeof window !== 'undefined' && window.matchMedia('(max-width: 1023.98px)').matches;
  }

  private lockBodyScroll(): void {
    if (typeof document !== 'undefined') {
      document.body.classList.add('no-scroll');
    }
  }

  private unlockBodyScroll(): void {
    if (typeof document !== 'undefined') {
      document.body.classList.remove('no-scroll');
    }
  }

  /** Reset drawer state when crossing back to the desktop breakpoint. */
  @HostListener('window:resize')
  onResize(): void {
    if (!this.isMobileViewport() && this.mobileNavOpen) {
      this.setMobileNav(false);
    }
  }

  isVisible(item: NavItem): boolean {
    if (!item.roles || item.roles.length === 0) return true;
    return this.authService.hasAnyRole(...item.roles);
  }

  logout(): void {
    this.unlockBodyScroll();
    this.authService.logout();
  }

  getUserInitials(): string {
    if (!this.user?.fullName) return '?';
    return this.user.fullName
      .split(' ')
      .map(n => n[0])
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }
}
