import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

export const SITE_NAME = 'MeetStudent';

/**
 * The one place that writes `<title>`. `Title` works on the server too, so the
 * crawler and the first paint get the same title the browser keeps.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleService {
  private readonly title = inject(Title);

  /** "<page> | MeetStudent", or the site name alone when the page has no name. */
  set(page?: string | null): void {
    this.title.setTitle(page ? `${page} | ${SITE_NAME}` : SITE_NAME);
  }
}

/**
 * Routes carry a translation key in `title`. `localeGuard` has loaded the
 * language's bundle by the time a navigation ends, and a language switch is a
 * navigation to the other locale's URL, so translating here is always current.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleStrategy extends TitleStrategy {
  private readonly pageTitle = inject(PageTitleService);
  private readonly transloco = inject(TranslocoService);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const key = this.buildTitle(snapshot);
    this.pageTitle.set(key ? this.transloco.translate(key) : null);
  }
}
