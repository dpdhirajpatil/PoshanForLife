import { Component, ElementRef, inject } from '@angular/core';
import { SIDE_PANEL_MAX_WIDTH, SIDE_PANEL_MIN_WIDTH, SIDE_PANEL_WIDTH_STORAGE_KEY } from './side-panel';

/**
 * Drag handle for a side-panel dialog — first element in the dialog's own
 * template so it lands inside the CDK overlay pane it needs to resize;
 * finds that pane via `.closest('.cdk-overlay-pane')` rather than any
 * dialog-ref plumbing, since MatDialogRef exposes no public handle to the
 * pane element itself. Resizes by growing/shrinking away from the panel's
 * anchored right edge, and persists the chosen width so every side panel
 * opens at the size the user last left it at (matches the old Next.js
 * app's adjustable-sidebar behavior).
 */
@Component({
  selector: 'app-side-panel-handle',
  standalone: true,
  template: `<div class="side-panel-handle" (mousedown)="startResize($event)" aria-hidden="true"></div>`,
  styles: `
    .side-panel-handle {
      position: absolute;
      left: -3px;
      top: 0;
      bottom: 0;
      width: 7px;
      cursor: col-resize;
      z-index: 10;
      touch-action: none;
    }
    .side-panel-handle:hover,
    .side-panel-handle.active {
      background: var(--primary);
      opacity: 0.35;
    }
  `,
})
export class SidePanelHandleComponent {
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  protected startResize(event: MouseEvent): void {
    event.preventDefault();
    const pane = this.elementRef.nativeElement.closest('.cdk-overlay-pane') as HTMLElement | null;
    if (!pane) return;

    const handle = event.currentTarget as HTMLElement;
    handle.classList.add('active');
    const startX = event.clientX;
    const startWidth = pane.getBoundingClientRect().width;

    const onMove = (moveEvent: MouseEvent) => {
      // Panel is anchored to the right edge — dragging the left-edge handle
      // further left grows the panel, so width moves opposite to clientX.
      const delta = startX - moveEvent.clientX;
      const maxWidth = Math.min(SIDE_PANEL_MAX_WIDTH, window.innerWidth - 48);
      const width = Math.min(Math.max(startWidth + delta, SIDE_PANEL_MIN_WIDTH), maxWidth);
      pane.style.width = `${width}px`;
    };
    const onUp = () => {
      handle.classList.remove('active');
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      localStorage.setItem(SIDE_PANEL_WIDTH_STORAGE_KEY, String(Math.round(pane.getBoundingClientRect().width)));
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  }
}
