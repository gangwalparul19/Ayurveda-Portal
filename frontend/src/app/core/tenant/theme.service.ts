import { Injectable } from '@angular/core';
import { TenantUiConfig } from '../models';

/**
 * Dynamically applies tenant-specific theming by setting CSS custom properties
 * on the document root. Called after login with the tenant's UI config.
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService {

  private readonly defaultTheme: TenantUiConfig = {
    primaryColor: '#2E7D32',
    secondaryColor: '#1B5E20',
    accentColor: '#FF9800',
    logoUrl: '',
    faviconUrl: '',
    fontFamily: 'Inter',
    customCss: '',
    storefrontEnabled: false
  };

  applyTheme(config: TenantUiConfig): void {
    const root = document.documentElement;

    root.style.setProperty('--primary-color', config.primaryColor || this.defaultTheme.primaryColor);
    root.style.setProperty('--primary-light', this.lighten(config.primaryColor || this.defaultTheme.primaryColor, 40));
    root.style.setProperty('--primary-dark', this.darken(config.primaryColor || this.defaultTheme.primaryColor, 20));
    root.style.setProperty('--secondary-color', config.secondaryColor || this.defaultTheme.secondaryColor);
    root.style.setProperty('--accent-color', config.accentColor || this.defaultTheme.accentColor);
    root.style.setProperty('--font-family', config.fontFamily || this.defaultTheme.fontFamily);

    // Update favicon
    if (config.faviconUrl) {
      const favicon = document.getElementById('app-favicon') as HTMLLinkElement;
      if (favicon) {
        favicon.href = config.faviconUrl;
      }
    }

    // Inject custom CSS
    if (config.customCss) {
      let styleEl = document.getElementById('tenant-custom-css');
      if (!styleEl) {
        styleEl = document.createElement('style');
        styleEl.id = 'tenant-custom-css';
        document.head.appendChild(styleEl);
      }
      styleEl.textContent = config.customCss;
    }
  }

  resetTheme(): void {
    this.applyTheme(this.defaultTheme);
    const customStyle = document.getElementById('tenant-custom-css');
    if (customStyle) {
      customStyle.remove();
    }
  }

  /**
   * Simple hex color lighten utility.
   */
  private lighten(hex: string, percent: number): string {
    return this.adjustColor(hex, percent);
  }

  private darken(hex: string, percent: number): string {
    return this.adjustColor(hex, -percent);
  }

  private adjustColor(hex: string, percent: number): string {
    const num = parseInt(hex.replace('#', ''), 16);
    const r = Math.min(255, Math.max(0, (num >> 16) + Math.round(2.55 * percent)));
    const g = Math.min(255, Math.max(0, ((num >> 8) & 0x00FF) + Math.round(2.55 * percent)));
    const b = Math.min(255, Math.max(0, (num & 0x0000FF) + Math.round(2.55 * percent)));
    return '#' + (0x1000000 + (r << 16) + (g << 8) + b).toString(16).slice(1);
  }
}
