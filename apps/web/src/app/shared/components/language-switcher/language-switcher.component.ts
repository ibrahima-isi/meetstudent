import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Check, ChevronDown, Globe, LucideAngularModule } from 'lucide-angular';
import { Locale, SUPPORTED_LOCALES, urlInLocale } from '@i18n/locale';
import { LocaleService } from '@services/locale.service';

/**
 * One button that opens the list of languages.
 *
 * Switching does two things and delegates the third: it remembers the choice,
 * then navigates to the mirrored URL. It deliberately does **not** call
 * `use()` — `localeGuard` runs on the new URL and does that, including awaiting
 * the translations. Calling both would load twice.
 */
@Component({
  selector: 'app-language-switcher',
  imports: [TranslocoDirective, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'relative inline-block',
    '(document:pointerdown)': 'onPointerDown($event)',
    '(document:keydown.escape)': 'onEscape()',
  },
  template: `
    <ng-container *transloco="let t">
      <button
        type="button"
        class="lang-trigger inline-flex h-9 items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground cursor-pointer transition-colors"
        [attr.aria-expanded]="isOpen()"
        [attr.aria-controls]="isOpen() ? 'language-menu' : null"
        [attr.aria-label]="t('nav.language') + ' : ' + t('language.' + activeLocale())"
        (click)="toggle()"
      >
        <lucide-icon [img]="Globe" class="h-4 w-4" aria-hidden="true" />
        <span>{{ compact() ? activeLocale().toUpperCase() : t('language.' + activeLocale()) }}</span>
        <lucide-icon [img]="ChevronDown" class="h-3.5 w-3.5" aria-hidden="true" />
      </button>
      @if (isOpen()) {
        <ul
          id="language-menu"
          role="list"
          [class]="
            compact()
              ? 'absolute right-0 top-full z-10 mt-2 min-w-40 rounded-2xl border border-border-strong bg-card p-1.5 shadow-xl'
              : 'mt-1 rounded-2xl border border-border bg-card p-1.5'
          "
        >
          @for (locale of locales; track locale) {
            <li>
              <button
                type="button"
                class="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2 text-left text-sm text-foreground hover:bg-accent cursor-pointer"
                [lang]="locale"
                [attr.aria-current]="locale === activeLocale() ? 'true' : null"
                (click)="switchTo(locale)"
              >
                <span>{{ t('language.' + locale) }}</span>
                @if (locale === activeLocale()) {
                  <lucide-icon [img]="Check" class="h-4 w-4 text-brand" aria-hidden="true" />
                }
              </button>
            </li>
          }
        </ul>
      }
    </ng-container>
  `,
})
export class LanguageSwitcherComponent {
  private readonly locale = inject(LocaleService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  /** The two-letter code and a floating list, for tight spaces such as the dock navbar. */
  readonly compact = input(false);
  /** Lets the host keep a single overlay open at a time. */
  readonly openChange = output<boolean>();

  protected readonly Globe = Globe;
  protected readonly ChevronDown = ChevronDown;
  protected readonly Check = Check;
  protected readonly locales = SUPPORTED_LOCALES;
  protected readonly activeLocale = this.locale.active;
  protected readonly isOpen = signal(false);
  /** Read by the host to know whether any list is still open. */
  readonly open = this.isOpen.asReadonly();

  close(): void {
    this.setOpen(false);
  }

  protected toggle(): void {
    this.setOpen(!this.isOpen());
  }

  protected onPointerDown(event: Event): void {
    if (!this.host.contains(event.target as Node | null)) {
      this.close();
    }
  }

  protected onEscape(): void {
    if (!this.isOpen()) {
      return;
    }
    this.close();
    this.host.querySelector<HTMLElement>('.lang-trigger')?.focus();
  }

  protected switchTo(locale: Locale): void {
    this.close();
    if (locale === this.locale.active()) {
      return;
    }

    // The one case `remember()` exists for: a choice the visitor made, not a
    // locale negotiated on their behalf.
    this.locale.remember(locale);
    void this.router.navigateByUrl(urlInLocale(this.router.url, locale));
  }

  private setOpen(open: boolean): void {
    if (open !== this.isOpen()) {
      this.isOpen.set(open);
      this.openChange.emit(open);
    }
  }
}
