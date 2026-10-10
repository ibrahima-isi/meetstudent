import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Locale, SUPPORTED_LOCALES, urlInLocale } from '@i18n/locale';
import { LocaleService } from '@services/locale.service';

/**
 * Switching does two things and delegates the third: it remembers the choice,
 * then navigates to the mirrored URL. It deliberately does **not** call
 * `use()` — `localeGuard` runs on the new URL and does that, including awaiting
 * the translations. Calling both would load twice.
 */
@Component({
  selector: 'app-language-switcher',
  imports: [TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-1" *transloco="let t">
      @for (locale of locales; track locale) {
        <button
          type="button"
          [lang]="locale"
          [attr.aria-current]="locale === activeLocale() ? 'true' : null"
          [attr.aria-label]="compact() ? t('language.' + locale) : null"
          [class]="
            locale === activeLocale()
              ? 'rounded-full px-2.5 py-1 text-sm font-semibold bg-brand-soft text-brand-soft-foreground'
              : 'rounded-full px-2.5 py-1 text-sm text-muted-foreground hover:bg-accent hover:text-foreground cursor-pointer transition-colors'
          "
          (click)="switchTo(locale)"
        >
          {{ compact() ? locale.toUpperCase() : t('language.' + locale) }}
        </button>
      }
    </div>
  `,
})
export class LanguageSwitcherComponent {
  private readonly locale = inject(LocaleService);
  private readonly router = inject(Router);

  /** Two-letter codes instead of full names, for tight spaces such as the dock navbar. */
  readonly compact = input(false);

  protected readonly locales = SUPPORTED_LOCALES;
  protected readonly activeLocale = this.locale.active;

  protected switchTo(locale: Locale): void {
    if (locale === this.locale.active()) {
      return;
    }

    // The one case `remember()` exists for: a choice the visitor made, not a
    // locale negotiated on their behalf.
    this.locale.remember(locale);
    void this.router.navigateByUrl(urlInLocale(this.router.url, locale));
  }
}
