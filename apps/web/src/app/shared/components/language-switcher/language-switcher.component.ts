import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
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
          [class]="
            locale === activeLocale()
              ? 'rounded-md px-2 py-1 text-sm font-semibold text-indigo-700 bg-indigo-50'
              : 'rounded-md px-2 py-1 text-sm text-gray-600 hover:bg-gray-100 cursor-pointer'
          "
          (click)="switchTo(locale)"
        >
          {{ t('language.' + locale) }}
        </button>
      }
    </div>
  `,
})
export class LanguageSwitcherComponent {
  private readonly locale = inject(LocaleService);
  private readonly router = inject(Router);

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
