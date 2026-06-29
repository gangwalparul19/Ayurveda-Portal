import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiService } from '../../core/api/api.service';
import { User, DashboardStats } from '../../core/models';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  user: User | null = null;
  greeting = '';

  stats: DashboardStats & { totalCustomers?: number } = {
    todayOrders: 0,
    todayRevenue: 0,
    pendingOrders: 0,
    monthlyRevenue: 0,
    lowStockProducts: 0,
    totalCustomers: 0
  };

  recentOrders: any[] = [];
  isLoading = true;

  constructor(
    private authService: AuthService,
    private api: ApiService
  ) {}

  ngOnInit(): void {
    this.user = this.authService.getCurrentUser();
    this.setGreeting();
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.api.getDashboardStats().subscribe({
      next: (data: any) => {
        this.stats = {
          todayOrders: data.todayOrders || 0,
          todayRevenue: data.todayRevenue || 0,
          pendingOrders: data.pendingOrders || 0,
          monthlyRevenue: data.monthlyRevenue || 0,
          lowStockProducts: data.lowStockProducts || 0,
          totalCustomers: data.totalCustomers || 0
        };
        this.isLoading = false;
      },
      error: () => {
        // Graceful fallback — show zeros
        this.isLoading = false;
      }
    });
  }

  private setGreeting(): void {
    const hour = new Date().getHours();
    if (hour < 12) this.greeting = 'Good Morning';
    else if (hour < 17) this.greeting = 'Good Afternoon';
    else this.greeting = 'Good Evening';
  }
}
