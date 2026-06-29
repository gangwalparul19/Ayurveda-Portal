import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-billing',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './billing.component.html',
  styleUrls: ['./billing.component.scss']
})
export class BillingComponent {
  dateFrom = '';
  dateTo = '';
  isExporting = false;
  successMessage = '';
  errorMessage = '';

  exportHistory: any[] = [];
  isLoadingHistory = true;

  constructor(private api: ApiService) {
    this.loadHistory();
    // Default date range: this month
    const now = new Date();
    this.dateFrom = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0];
    this.dateTo = now.toISOString().split('T')[0];
  }

  loadHistory(): void {
    this.isLoadingHistory = true;
    this.api['http'].get<any[]>(`${this.api['API']}/billing/history`).subscribe({
      next: (history) => {
        this.exportHistory = history;
        this.isLoadingHistory = false;
      },
      error: () => { this.isLoadingHistory = false; }
    });
  }

  exportVyapar(): void {
    if (!this.dateFrom || !this.dateTo) {
      this.errorMessage = 'Please select a date range';
      return;
    }
    this.isExporting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api.exportVyapar(this.dateFrom, this.dateTo, 'csv').subscribe({
      next: (blob) => {
        this.downloadFile(blob, `vyapar_export_${this.dateFrom}_${this.dateTo}.csv`);
        this.isExporting = false;
        this.successMessage = 'Vyapar CSV exported successfully!';
        this.loadHistory();
      },
      error: () => {
        this.isExporting = false;
        this.errorMessage = 'Failed to export. Ensure there are orders in the selected range.';
      }
    });
  }

  exportGst(): void {
    if (!this.dateFrom || !this.dateTo) {
      this.errorMessage = 'Please select a date range';
      return;
    }
    this.isExporting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.api['http'].post(`${this.api['API']}/billing/export/gst`,
      { dateFrom: this.dateFrom, dateTo: this.dateTo },
      { responseType: 'blob' }
    ).subscribe({
      next: (blob: Blob) => {
        this.downloadFile(blob, `gst_export_${this.dateFrom}_${this.dateTo}.json`);
        this.isExporting = false;
        this.successMessage = 'GST JSON exported successfully!';
        this.loadHistory();
      },
      error: () => {
        this.isExporting = false;
        this.errorMessage = 'Failed to export GST data.';
      }
    });
  }

  private downloadFile(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    window.URL.revokeObjectURL(url);
  }
}
