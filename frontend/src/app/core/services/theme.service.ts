import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'theme';

/**
 * Angular equivalent of next-themes: tracks the user's chosen theme
 * ('light' | 'dark' | 'system'), persists it, and keeps the `dark` class on
 * <html> in sync — including live OS-preference changes while 'system' is
 * selected. The initial class is already applied by an inline script in
 * index.html before Angular bootstraps, so this service only takes over
 * for changes made after load.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.readStored());
  /** The currently rendered light/dark state — resolves 'system' against the OS preference. Set correctly in the constructor, once `media` exists. */
  readonly isDark = signal(false);

  private readonly media = window.matchMedia('(prefers-color-scheme: dark)');

  constructor() {
    this.media.addEventListener('change', () => {
      if (this.theme() === 'system') {
        this.applyClass(this.media.matches);
      }
    });
    this.applyClass(this.resolveIsDark(this.theme()));
  }

  setTheme(value: Theme): void {
    this.theme.set(value);
    localStorage.setItem(STORAGE_KEY, value);
    this.applyClass(this.resolveIsDark(value));
  }

  /** Quick toggle for a single icon button — flips between explicit light/dark (leaves 'system' if not currently active). */
  toggle(): void {
    this.setTheme(this.isDark() ? 'light' : 'dark');
  }

  private resolveIsDark(theme: Theme): boolean {
    return theme === 'dark' || (theme === 'system' && this.media.matches);
  }

  private applyClass(isDark: boolean): void {
    document.documentElement.classList.toggle('dark', isDark);
    this.isDark.set(isDark);
  }

  private readStored(): Theme {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
  }
}
