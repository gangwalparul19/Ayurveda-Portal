import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { IconComponent } from '../../shared/ui/icon.component';

interface PlatformUser {
  id: number;
  username: string;
  fullName: string;
  email: string;
  role: string;
  isActive: boolean;
  createdAt: string;
}

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss']
})
export class UsersComponent implements OnInit {
  users: PlatformUser[] = [];
  isLoading = true;
  showModal = false;
  isEditing = false;
  isSaving = false;
  successMessage = '';
  errorMessage = '';

  editUser: Partial<PlatformUser & { password?: string }> = {};

  roles = ['SALESPERSON', 'MANAGER', 'TENANT_ADMIN'];
  currentUserRole = '';

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.currentUserRole = this.authService.getCurrentUser()?.role || '';
    if (this.currentUserRole === 'SUPER_ADMIN') {
      this.roles.push('SUPER_ADMIN');
    }
    this.loadUsers();
  }

  loadUsers(): void {
    this.isLoading = true;
    const token = this.authService.getAccessToken();
    
    fetch('/api/admin/users?page=0&size=100', {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    .then(res => res.json())
    .then(data => {
      this.users = Array.isArray(data) ? data : (data.content || []);
      this.isLoading = false;
    })
    .catch(() => {
      this.users = [];
      this.isLoading = false;
    });
  }

  openCreateModal(): void {
    this.isEditing = false;
    this.editUser = { username: '', fullName: '', email: '', role: 'SALESPERSON', password: '', isActive: true };
    this.errorMessage = '';
    this.showModal = true;
  }

  openEditModal(user: PlatformUser): void {
    this.isEditing = true;
    this.editUser = { ...user, password: '' };
    this.errorMessage = '';
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
  }

  saveUser(): void {
    if (!this.editUser.username?.trim() || !this.editUser.fullName?.trim()) {
      this.errorMessage = 'Username and full name are required';
      return;
    }
    if (!this.isEditing && !this.editUser.password?.trim()) {
      this.errorMessage = 'Password is required for new users';
      return;
    }

    this.isSaving = true;
    this.errorMessage = '';
    const token = this.authService.getAccessToken();
    const method = this.isEditing ? 'PUT' : 'POST';
    const url = this.isEditing ? `/api/admin/users/${this.editUser.id}` : '/api/admin/users';

    fetch(url, {
      method,
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(this.editUser)
    })
    .then(res => {
      if (!res.ok) throw new Error('Failed');
      return res.json();
    })
    .then(() => {
      this.isSaving = false;
      this.successMessage = this.isEditing ? 'User updated!' : 'User created!';
      this.closeModal();
      this.loadUsers();
      setTimeout(() => this.successMessage = '', 3000);
    })
    .catch(() => {
      this.isSaving = false;
      this.errorMessage = 'Failed to save user. Username may already exist.';
    });
  }

  toggleUserStatus(user: PlatformUser): void {
    const token = this.authService.getAccessToken();
    fetch(`/api/admin/users/${user.id}/status`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ isActive: !user.isActive })
    })
    .then(() => this.loadUsers())
    .catch(() => {});
  }

  getRoleClass(role: string): string {
    switch (role) {
      case 'SUPER_ADMIN': return 'role-super';
      case 'TENANT_ADMIN': return 'role-admin';
      case 'MANAGER': return 'role-manager';
      default: return 'role-sales';
    }
  }

  getInitials(name: string): string {
    return name?.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase() || '?';
  }
}
