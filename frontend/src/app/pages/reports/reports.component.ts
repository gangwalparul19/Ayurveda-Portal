import { Component, OnInit, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.scss']
})
export class ReportsComponent implements OnInit {
  activeTab: 'daily' | 'monthly' | 'salesperson' | 'products' | 'customers' = 'daily';

  // Daily report
  dailyDate = new Date().toISOString().split('T')[0];
  dailyReport: any = null;
  isDailyLoading = false;

  // Monthly report
  selectedMonth = new Date().getMonth() + 1;
  selectedYear = new Date().getFullYear();
  monthlyReport: any = null;
  isMonthlyLoading = false;

  months = [
    { value: 1, label: 'January' }, { value: 2, label: 'February' },
    { value: 3, label: 'March' }, { value: 4, label: 'April' },
    { value: 5, label: 'May' }, { value: 6, label: 'June' },
    { value: 7, label: 'July' }, { value: 8, label: 'August' },
    { value: 9, label: 'September' }, { value: 10, label: 'October' },
    { value: 11, label: 'November' }, { value: 12, label: 'December' }
  ];

  years: number[] = [];

  // Salesperson Performance
  salespersonStartDate = new Date(new Date().setDate(1)).toISOString().split('T')[0]; // First day of month
  salespersonEndDate = new Date().toISOString().split('T')[0];
  salespersonPerformance: any[] = [];
  isSalespersonLoading = false;

  // Top Products
  productsStartDate = new Date(new Date().setDate(1)).toISOString().split('T')[0];
  productsEndDate = new Date().toISOString().split('T')[0];
  topProductsData: any = null;
  isProductsLoading = false;

  // Customer Analytics
  customersStartDate = new Date(new Date().setDate(1)).toISOString().split('T')[0];
  customersEndDate = new Date().toISOString().split('T')[0];
  customerAnalytics: any = null;
  isCustomersLoading = false;

  constructor(private api: ApiService) {
    const currentYear = new Date().getFullYear();
    for (let y = currentYear; y >= currentYear - 3; y--) this.years.push(y);
  }

  ngOnInit(): void {
    this.loadDailyReport();
  }

  switchTab(tab: 'daily' | 'monthly' | 'salesperson' | 'products' | 'customers'): void {
    this.activeTab = tab;
    if (tab === 'monthly' && !this.monthlyReport) {
      this.loadMonthlyReport();
    } else if (tab === 'salesperson' && this.salespersonPerformance.length === 0) {
      this.loadSalespersonPerformance();
    } else if (tab === 'products' && !this.topProductsData) {
      this.loadTopProducts();
    } else if (tab === 'customers' && !this.customerAnalytics) {
      this.loadCustomerAnalytics();
    }
  }

  loadDailyReport(): void {
    this.isDailyLoading = true;
    this.api.getDailyReport(this.dailyDate).subscribe({
      next: (data) => {
        this.dailyReport = data;
        this.isDailyLoading = false;
      },
      error: () => {
        this.dailyReport = { orderCount: 0, totalRevenue: 0, byStatus: {}, byPaymentMode: {} };
        this.isDailyLoading = false;
      }
    });
  }

  loadMonthlyReport(): void {
    this.isMonthlyLoading = true;
    this.api.getMonthlyReport(this.selectedMonth, this.selectedYear).subscribe({
      next: (data) => {
        this.monthlyReport = data;
        this.isMonthlyLoading = false;
      },
      error: () => {
        this.monthlyReport = { totalOrders: 0, totalRevenue: 0, dailyBreakdown: {} };
        this.isMonthlyLoading = false;
      }
    });
  }

  getStatusEntries(obj: any): { key: string; value: number }[] {
    if (!obj) return [];
    return Object.entries(obj).map(([key, value]) => ({ key, value: value as number }));
  }

  getDailyBreakdownEntries(): { date: string; count: number }[] {
    if (!this.monthlyReport?.dailyBreakdown) return [];
    return Object.entries(this.monthlyReport.dailyBreakdown)
      .map(([date, count]) => ({ date, count: count as number }))
      .sort((a, b) => a.date.localeCompare(b.date));
  }

  getMaxDailyCount(): number {
    const entries = this.getDailyBreakdownEntries();
    return entries.length > 0 ? Math.max(...entries.map(e => e.count), 1) : 1;
  }

  getBarHeight(count: number): number {
    return (count / this.getMaxDailyCount()) * 100;
  }

  getDayLabel(dateStr: string): string {
    const d = new Date(dateStr);
    return d.getDate().toString();
  }

  // Salesperson Performance methods
  loadSalespersonPerformance(): void {
    this.isSalespersonLoading = true;
    this.api.getSalespersonPerformance(this.salespersonStartDate, this.salespersonEndDate).subscribe({
      next: (data) => {
        this.salespersonPerformance = data;
        this.isSalespersonLoading = false;
      },
      error: () => {
        this.salespersonPerformance = [];
        this.isSalespersonLoading = false;
      }
    });
  }

  getSalespersonMaxRevenue(): number {
    if (this.salespersonPerformance.length === 0) return 1;
    return Math.max(...this.salespersonPerformance.map(p => p.totalRevenue), 1);
  }

  getSalespersonBarWidth(revenue: number): number {
    return (revenue / this.getSalespersonMaxRevenue()) * 100;
  }

  // Top Products methods
  loadTopProducts(): void {
    this.isProductsLoading = true;
    this.api.getTopProducts(this.productsStartDate, this.productsEndDate, 10).subscribe({
      next: (data) => {
        this.topProductsData = data;
        this.isProductsLoading = false;
      },
      error: () => {
        this.topProductsData = { topByRevenue: [], topByQuantity: [] };
        this.isProductsLoading = false;
      }
    });
  }

  getTopProductMaxRevenue(): number {
    if (!this.topProductsData?.topByRevenue || this.topProductsData.topByRevenue.length === 0) return 1;
    return Math.max(...this.topProductsData.topByRevenue.map((p: any) => p.revenue), 1);
  }

  getTopProductMaxQuantity(): number {
    if (!this.topProductsData?.topByQuantity || this.topProductsData.topByQuantity.length === 0) return 1;
    return Math.max(...this.topProductsData.topByQuantity.map((p: any) => p.quantitySold), 1);
  }

  getProductRevenueWidth(revenue: number): number {
    return (revenue / this.getTopProductMaxRevenue()) * 100;
  }

  getProductQuantityWidth(quantity: number): number {
    return (quantity / this.getTopProductMaxQuantity()) * 100;
  }

  // Customer Analytics methods
  loadCustomerAnalytics(): void {
    this.isCustomersLoading = true;
    this.api.getCustomerAnalytics(this.customersStartDate, this.customersEndDate).subscribe({
      next: (data) => {
        this.customerAnalytics = data;
        this.isCustomersLoading = false;
      },
      error: () => {
        this.customerAnalytics = { uniqueCustomers: 0, newCustomers: 0, returningCustomers: 0, averageOrderValue: 0 };
        this.isCustomersLoading = false;
      }
    });
  }

  getCustomerPercentage(part: number, total: number): number {
    return total > 0 ? (part / total) * 100 : 0;
  }
}
