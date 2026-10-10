import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, Monitor, Moon, Sun } from 'lucide-angular';
import { ThemeChoice, ThemeService } from '@services/theme.service';

const NEXT: Record<ThemeChoice, ThemeChoice> = {
  system: 'light',
  light: 'dark',
  dark: 'system',
};

/** One button that cycles system, light, dark; its label names both ends of the step. */
@Component({
  selector: 'app-theme-toggle',
  imports: [TranslocoDirective, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-container *transloco="let t">
      <button
        type="button"
        class="inline-flex h-9 w-9 items-center justify-center rounded-full text-muted-foreground hover:bg-accent hover:text-foreground cursor-pointer transition-colors"
        [attr.data-theme]="choice()"
        [attr.aria-label]="t('theme.switch', { current: t('theme.' + choice()), next: t('theme.' + next()) })"
        [attr.title]="t('theme.' + choice())"
        (click)="cycle()"
      >
        @switch (choice()) {
          @case ('light') {
            <lucide-icon [img]="Sun" class="h-5 w-5" aria-hidden="true" />
          }
          @case ('dark') {
            <lucide-icon [img]="Moon" class="h-5 w-5" aria-hidden="true" />
          }
          @default {
            <lucide-icon [img]="Monitor" class="h-5 w-5" aria-hidden="true" />
          }
        }
      </button>
    </ng-container>
  `,
})
export class ThemeToggleComponent {
  private readonly theme = inject(ThemeService);

  protected readonly Sun = Sun;
  protected readonly Moon = Moon;
  protected readonly Monitor = Monitor;

  protected readonly choice = this.theme.choice;
  protected readonly next = computed(() => NEXT[this.choice()]);

  protected cycle(): void {
    this.theme.set(this.next());
  }
}
