import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import {
  Component,
  Directive,
  ElementRef,
  HostListener,
  Input,
  OnDestroy,
  inject,
} from '@angular/core';

@Component({
  selector: 'app-sidebar-tooltip-bubble',
  standalone: true,
  template: `<span>{{ text }}</span>`,
  styles: `
    :host {
      display: block;
      background: var(--popover);
      color: var(--popover-foreground);
      border: 1px solid var(--border);
      border-radius: 0.5rem;
      padding: 4px 10px;
      font-size: 0.75rem;
      font-weight: 500;
      white-space: nowrap;
      box-shadow: 0 4px 12px rgb(0 0 0 / 0.12);
    }
  `,
})
class SidebarTooltipBubbleComponent {
  text = '';
}

/**
 * Lightweight hover tooltip anchored to the right of the host, used for
 * collapsed sidebar items (icon-only) so the label is still discoverable.
 * Disabled entirely (no overlay created) when `enabled` is false, e.g. while
 * the sidebar is expanded and labels are already visible inline.
 */
@Directive({
  selector: '[appSidebarTooltip]',
  standalone: true,
})
export class SidebarTooltipDirective implements OnDestroy {
  @Input('appSidebarTooltip') text = '';
  @Input() enabled = true;

  private readonly overlay = inject(Overlay);
  private readonly elementRef = inject(ElementRef<HTMLElement>);
  private overlayRef: OverlayRef | null = null;

  @HostListener('mouseenter')
  onEnter(): void {
    if (!this.enabled || !this.text || this.overlayRef) return;

    const positionStrategy = this.overlay
      .position()
      .flexibleConnectedTo(this.elementRef)
      .withPositions([
        { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: 10 },
      ]);

    this.overlayRef = this.overlay.create({ positionStrategy });
    const portal = new ComponentPortal(SidebarTooltipBubbleComponent);
    const ref = this.overlayRef.attach(portal);
    ref.instance.text = this.text;
  }

  @HostListener('mouseleave')
  onLeave(): void {
    this.close();
  }

  ngOnDestroy(): void {
    this.close();
  }

  private close(): void {
    this.overlayRef?.dispose();
    this.overlayRef = null;
  }
}
