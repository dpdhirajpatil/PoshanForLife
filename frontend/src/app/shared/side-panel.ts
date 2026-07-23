import { ComponentType } from '@angular/cdk/portal';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

/**
 * Every create/edit form dialog in the app opens as a full-height panel
 * anchored to the right edge (replaces the old centered popup) — matches
 * the adjustable side panel from the previous Next.js app. Detail-only and
 * confirm dialogs stay as regular centered MatDialogs; don't route those
 * through here.
 */
export const SIDE_PANEL_MIN_WIDTH = 360;
export const SIDE_PANEL_MAX_WIDTH = 960;
export const SIDE_PANEL_DEFAULT_WIDTH = 480;
export const SIDE_PANEL_WIDTH_STORAGE_KEY = 'sidePanelWidth';

function currentWidth(): number {
  const stored = Number(localStorage.getItem(SIDE_PANEL_WIDTH_STORAGE_KEY));
  return stored >= SIDE_PANEL_MIN_WIDTH && stored <= SIDE_PANEL_MAX_WIDTH ? stored : SIDE_PANEL_DEFAULT_WIDTH;
}

/** Drop-in replacement for `dialog.open(...)` for form dialogs — same return type, same `.afterClosed()` usage. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function openSidePanel<T, D = any, R = any>(
  dialog: MatDialog,
  component: ComponentType<T>,
  config?: MatDialogConfig<D>,
): MatDialogRef<T, R> {
  return dialog.open<T, D, R>(component, {
    ...config,
    panelClass: ['side-panel', ...toArray(config?.panelClass)],
    position: { top: '0', right: '0' },
    width: `${currentWidth()}px`,
    maxWidth: '100vw',
    height: '100vh',
    maxHeight: '100vh',
  });
}

function toArray(value: string | string[] | undefined): string[] {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}
