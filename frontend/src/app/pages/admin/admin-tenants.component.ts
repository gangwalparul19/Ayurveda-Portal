import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { Tenant } from '../../core/models';

@Component({
  selector: 'app-admin-tenants',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-tenants.component.html',
  styleUrls: ['./admin-tenants.component.scss']
})
export class AdminTenantsComponent implements OnInit {
  tenants: Tenant[] = [];
  isLoading = true;
  showModal = false;
  showAnalytics = false;
  isSaving = false;
  isValidating = false;
  successMessage = '';
  errorMessage = '';
  validationMessage = '';

  platformAnalytics: any = null;

  currentStep: 'details' | 'database' | 'ui' = 'details';

  newTenant = {
    name: '',
    tenantKey: '',
    adminUsername: '',
    adminPassword: '',
    adminFullName: '',
    adminEmail: '',
    primaryColor: '#2E7D32',
    secondaryColor: '#1B5E20',
    accentColor: '#FF6F00',
    logoUrl: '',
    dbUrl: 'jdbc:mysql://localhost:3306/tenant_db',
    dbUsername: 'tenant_user',
    dbPassword: ''
  };

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadTenants();
    this.loadPlatformAnalytics();
  }

  loadTenants(): void {
    this.isLoading = true;
    this.api.getTenants().subscribe({
      next: (tenants) => { this.tenants = tenants; this.isLoading = false; },
      error: () => { this.tenants = []; this.isLoading = false; }
    });
  }

  loadPlatformAnalytics(): void {
    this.api.getPlatformAnalytics().subscribe({
      next: (data) => { this.platformAnalytics = data; },
      error: () => { this.platformAnalytics = null; }
    });
  }

  toggleAnalytics(): void {
    this.showAnalytics = !this.showAnalytics;
    if (this.showAnalytics && !this.platformAnalytics) {
      this.loadPlatformAnalytics();
    }
  }

  openModal(): void {
    this.currentStep = 'details';
    this.newTenant = {
      name: '', tenantKey: '', adminUsername: '', adminPassword: '',
      adminFullName: '', adminEmail: '', primaryColor: '#2E7D32',
      secondaryColor: '#1B5E20', accentColor: '#FF6F00', logoUrl: '',
      dbUrl: 'jdbc:mysql://localhost:3306/tenant_db',
      dbUsername: 'tenant_user',
      dbPassword: ''
    };
    this.errorMessage = '';
    this.validationMessage = '';
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.currentStep = 'details';
  }

  nextStep(): void {
    if (this.currentStep === 'details') {
      if (!this.newTenant.name || !this.newTenant.tenantKey || 
          !this.newTenant.adminUsername || !this.newTenant.adminPassword || 
          !this.newTenant.adminFullName || !this.newTenant.adminEmail) {
        this.errorMessage = 'Please fill all required fields';
        return;
      }
      this.currentStep = 'database';
    } else if (this.currentStep === 'database') {
      if (!this.newTenant.dbUrl || !this.newTenant.dbUsername || !this.newTenant.dbPassword) {
        this.errorMessage = 'Please fill all database fields';
        return;
      }
      this.currentStep = 'ui';
    }
    this.errorMessage = '';
  }

  prevStep(): void {
    if (this.currentStep === 'ui') {
      this.currentStep = 'database';
    } else if (this.currentStep === 'database') {
      this.currentStep = 'details';
    }
    this.errorMessage = '';
  }

  validateDatabase(): void {
    if (!this.newTenant.dbUrl || !this.newTenant.dbUsername || !this.newTenant.dbPassword) {
      this.errorMessage = 'Please fill all database fields';
      return;
    }

    this.isValidating = true;
    this.validationMessage = '';
    this.errorMessage = '';

    this.api.validateDatabaseConnection(
      this.newTenant.dbUrl,
      this.newTenant.dbUsername,
      this.newTenant.dbPassword
    ).subscribe({
      next: (result) => {
        this.isValidating = false;
        if (result.valid) {
          this.validationMessage = `✅ Connection successful! Database: ${result.databaseProductName} ${result.databaseProductVersion}`;
        } else {
          this.errorMessage = `❌ ${result.message}`;
        }
      },
      error: (err) => {
        this.isValidating = false;
        this.errorMessage = `❌ Connection failed: ${err.error?.message || 'Unknown error'}`;
      }
    });
  }

  autoFillKey(): void {
    if (!this.newTenant.tenantKey) {
      this.newTenant.tenantKey = this.newTenant.name
        .toLowerCase().replace(/[^a-z0-9]/g, '_').replace(/_+/g, '_').substring(0, 30);
    }
  }

  onboardTenant(): void {
    if (!this.newTenant.name || !this.newTenant.tenantKey || 
        !this.newTenant.adminUsername || !this.newTenant.adminPassword ||
        !this.newTenant.dbUrl || !this.newTenant.dbUsername || !this.newTenant.dbPassword) {
      this.errorMessage = 'All required fields must be filled';
      return;
    }
    this.isSaving = true;
    this.errorMessage = '';
    
    const payload = {
      companyName: this.newTenant.name,
      tenantKey: this.newTenant.tenantKey,
      adminUsername: this.newTenant.adminUsername,
      adminPassword: this.newTenant.adminPassword,
      adminFullName: this.newTenant.adminFullName,
      adminEmail: this.newTenant.adminEmail,
      dbUrl: this.newTenant.dbUrl,
      dbUsername: this.newTenant.dbUsername,
      dbPassword: this.newTenant.dbPassword,
      primaryColor: this.newTenant.primaryColor,
      secondaryColor: this.newTenant.secondaryColor,
      accentColor: this.newTenant.accentColor,
      logoUrl: this.newTenant.logoUrl || null
    };

    this.api.onboardTenant(payload).subscribe({
      next: () => {
        this.isSaving = false;
        this.successMessage = `Tenant "${this.newTenant.name}" onboarded successfully!`;
        this.closeModal();
        this.loadTenants();
        this.loadPlatformAnalytics();
        setTimeout(() => this.successMessage = '', 4000);
      },
      error: (err: any) => {
        this.isSaving = false;
        this.errorMessage = err.error?.message || 'Failed to onboard tenant. Key may already exist.';
      }
    });
  }

  toggleStatus(tenant: Tenant): void {
    const newStatus = tenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    this.api.updateTenantStatus(tenant.id, newStatus).subscribe({
      next: () => {
        this.loadTenants();
        this.loadPlatformAnalytics();
      }
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'status-active';
      case 'SUSPENDED': return 'status-suspended';
      default: return 'status-pending';
    }
  }

  getUserRoleEntries(): { role: string; count: number }[] {
    if (!this.platformAnalytics?.usersByRole) return [];
    return Object.entries(this.platformAnalytics.usersByRole)
      .map(([role, count]) => ({ role, count: count as number }));
  }

  getPlanEntries(): { plan: string; count: number }[] {
    if (!this.platformAnalytics?.tenantsByPlan) return [];
    return Object.entries(this.platformAnalytics.tenantsByPlan)
      .map(([plan, count]) => ({ plan, count: count as number }));
  }
}
