import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-targets',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './targets.component.html',
  styleUrls: ['./targets.component.scss']
})
export class TargetsComponent implements OnInit {
  currentYear = new Date().getFullYear();
  currentMonth = new Date().getMonth() + 1;
  
  selectedYear = this.currentYear;
  selectedMonth = this.currentMonth;
  
  monthTargets: any[] = [];
  isLoading = false;
  
  showSetTargetModal = false;
  isSaving = false;
  
  targetForm = {
    salespersonId: 0,
    month: this.currentMonth,
    year: this.currentYear,
    targetAmount: 0
  };
  
  successMessage = '';
  errorMessage = '';
  
  months = [
    { value: 1, label: 'January' }, { value: 2, label: 'February' },
    { value: 3, label: 'March' }, { value: 4, label: 'April' },
    { value: 5, label: 'May' }, { value: 6, label: 'June' },
    { value: 7, label: 'July' }, { value: 8, label: 'August' },
    { value: 9, label: 'September' }, { value: 10, label: 'October' },
    { value: 11, label: 'November' }, { value: 12, label: 'December' }
  ];
  
  years: number[] = [];
  currentUserRole = '';

  constructor(
    private api: ApiService,
    private authService: AuthService
  ) {
    for (let y = this.currentYear; y >= this.currentYear - 2; y--) {
      this.years.push(y);
    }
    for (let y = this.currentYear + 1; y <= this.currentYear + 2; y++) {
      this.years.push(y);
    }
    this.years.sort((a, b) => b - a);
  }

  ngOnInit(): void {
    this.currentUserRole = this.authService.getCurrentUser()?.role || '';
    this.loadMonthTargets();
  }

  loadMonthTargets(): void {
    this.isLoading = true;
    this.api.getMonthTargets(this.selectedMonth, this.selectedYear).subscribe({
      next: (data) => {
        this.monthTargets = data;
        this.isLoading = false;
      },
      error: () => {
        this.monthTargets = [];
        this.isLoading = false;
      }
    });
  }

  openSetTargetModal(): void {
    this.targetForm = {
      salespersonId: 0,
      month: this.selectedMonth,
      year: this.selectedYear,
      targetAmount: 0
    };
    this.errorMessage = '';
    this.showSetTargetModal = true;
  }

  closeModal(): void {
    this.showSetTargetModal = false;
  }

  saveTarget(): void {
    if (!this.targetForm.salespersonId || !this.targetForm.targetAmount) {
      this.errorMessage = 'Salesperson ID and Target Amount are required';
      return;
    }

    this.isSaving = true;
    this.errorMessage = '';
    
    this.api.setTarget(
      this.targetForm.salespersonId,
      this.targetForm.month,
      this.targetForm.year,
      this.targetForm.targetAmount
    ).subscribe({
      next: () => {
        this.isSaving = false;
        this.successMessage = 'Target set successfully!';
        this.closeModal();
        this.loadMonthTargets();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = err.error?.message || 'Failed to set target';
      }
    });
  }

  recalculateTargets(): void {
    if (!confirm('Recalculate all achievements for this month? This will update achievement amounts based on actual orders.')) {
      return;
    }

    this.api.recalculateTargets(this.selectedMonth, this.selectedYear).subscribe({
      next: () => {
        this.successMessage = 'Targets recalculated successfully!';
        this.loadMonthTargets();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: () => {
        this.errorMessage = 'Failed to recalculate targets';
        setTimeout(() => this.errorMessage = '', 3000);
      }
    });
  }

  deleteTarget(targetId: number): void {
    if (!confirm('Delete this target? This action cannot be undone.')) {
      return;
    }

    this.api.deleteTarget(targetId).subscribe({
      next: () => {
        this.successMessage = 'Target deleted successfully!';
        this.loadMonthTargets();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: () => {
        this.errorMessage = 'Failed to delete target';
        setTimeout(() => this.errorMessage = '', 3000);
      }
    });
  }

  getStatusClass(percentage: number): string {
    if (percentage >= 100) return 'status-success';
    if (percentage >= 75) return 'status-warning';
    return 'status-danger';
  }

  getMonthLabel(month: number): string {
    return this.months.find(m => m.value === month)?.label || '';
  }

  canEdit(): boolean {
    return this.currentUserRole === 'SUPER_ADMIN' || this.currentUserRole === 'TENANT_ADMIN';
  }
}
