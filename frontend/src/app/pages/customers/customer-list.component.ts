import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../core/api/api.service';

interface Customer {
  id: number;
  name: string;
  phone: string;
  email: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  pincode: string;
  gstin: string;
}

import { IconComponent } from '../../shared/ui/icon.component';

@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, IconComponent],
  templateUrl: './customer-list.component.html',
  styleUrls: ['./customer-list.component.scss']
})
export class CustomerListComponent implements OnInit {
  customers: Customer[] = [];
  totalElements = 0;
  totalPages = 0;
  currentPage = 0;
  pageSize = 20;
  isLoading = true;
  searchQuery = '';

  // Edit modal
  showModal = false;
  isEditing = false;
  isSaving = false;
  editCustomer: Partial<Customer> = {};
  errorMessage = '';
  successMessage = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadCustomers();
  }

  loadCustomers(): void {
    this.isLoading = true;
    if (this.searchQuery.trim()) {
      this.api.searchCustomers(this.searchQuery).subscribe({
        next: (customers: any) => {
          this.customers = customers;
          this.totalElements = customers.length;
          this.isLoading = false;
        },
        error: () => { this.isLoading = false; }
      });
    } else {
      this.api.getCustomers(this.currentPage, this.pageSize).subscribe({
        next: (page: any) => {
          this.customers = page.content;
          this.totalElements = page.totalElements;
          this.totalPages = page.totalPages;
          this.isLoading = false;
        },
        error: () => { this.isLoading = false; }
      });
    }
  }

  onSearch(): void {
    this.currentPage = 0;
    this.loadCustomers();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.currentPage = 0;
    this.loadCustomers();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadCustomers();
    }
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.editCustomer = { name: '', phone: '', email: '', addressLine1: '', city: '', state: '', pincode: '' };
    this.errorMessage = '';
    this.showModal = true;
  }

  openEditModal(customer: Customer): void {
    this.isEditing = true;
    this.editCustomer = { ...customer };
    this.errorMessage = '';
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.editCustomer = {};
  }

  saveCustomer(): void {
    if (!this.editCustomer.name?.trim()) {
      this.errorMessage = 'Name is required';
      return;
    }
    this.isSaving = true;
    this.errorMessage = '';

    const request = this.isEditing
      ? this.api.updateCustomer(this.editCustomer.id!, this.editCustomer)
      : this.api.createCustomer(this.editCustomer);

    request.subscribe({
      next: () => {
        this.isSaving = false;
        this.successMessage = this.isEditing ? 'Customer updated!' : 'Customer created!';
        this.closeModal();
        this.loadCustomers();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err: any) => {
        this.isSaving = false;
        this.errorMessage = err.error?.message || 'Failed to save customer';
      }
    });
  }

  getInitials(name: string): string {
    return name?.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase() || '?';
  }

  get pageNumbers(): number[] {
    const pages: number[] = [];
    const start = Math.max(0, this.currentPage - 2);
    const end = Math.min(this.totalPages, start + 5);
    for (let i = start; i < end; i++) pages.push(i);
    return pages;
  }
}
