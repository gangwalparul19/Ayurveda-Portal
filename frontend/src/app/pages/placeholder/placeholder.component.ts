import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

/**
 * Generic placeholder component for pages not yet built.
 * Displays the page title, icon, and description from route data.
 */
@Component({
  selector: 'app-placeholder',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="placeholder-page animate-fade-in">
      <div class="placeholder-card glass-panel">
        <div class="placeholder-icon">{{ icon }}</div>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
        <div class="placeholder-badge">
          <span class="badge badge-pending">Under Development</span>
        </div>
        <div class="placeholder-features">
          <div class="feature-dot"></div>
          <div class="feature-dot"></div>
          <div class="feature-dot"></div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .placeholder-page {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 60vh;
    }
    .placeholder-card {
      text-align: center;
      padding: 64px 48px;
      max-width: 480px;
      width: 100%;
    }
    .placeholder-icon {
      font-size: 4rem;
      margin-bottom: 20px;
      filter: grayscale(0.3);
    }
    h2 {
      font-size: var(--font-size-2xl);
      font-weight: 800;
      color: var(--text-primary);
      margin-bottom: 12px;
    }
    p {
      color: var(--text-muted);
      font-size: var(--font-size-sm);
      line-height: 1.6;
      margin-bottom: 20px;
    }
    .placeholder-badge {
      margin-bottom: 32px;
    }
    .placeholder-features {
      display: flex;
      justify-content: center;
      gap: 8px;
    }
    .feature-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--primary-color);
      opacity: 0.3;
      animation: pulse 1.5s ease-in-out infinite;
    }
    .feature-dot:nth-child(2) { animation-delay: 0.3s; }
    .feature-dot:nth-child(3) { animation-delay: 0.6s; }
  `]
})
export class PlaceholderComponent {
  title = '';
  icon = '🔧';
  description = '';

  constructor(private route: ActivatedRoute) {
    this.route.data.subscribe(data => {
      this.title = data['title'] || 'Coming Soon';
      this.icon = data['icon'] || '🔧';
      this.description = data['description'] || 'This feature is under development.';
    });
  }
}
