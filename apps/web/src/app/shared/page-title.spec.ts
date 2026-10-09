import { Component, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { Route, Router, TitleStrategy, provideRouter } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { routes } from '../app.routes';
import { PageTitleService, PageTitleStrategy } from './page-title';

@Component({ template: '' })
class BlankComponent {}

describe('page titles', () => {
  const title = () => TestBed.inject(Title).getTitle();

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'login', title: 'pageTitle.login', component: BlankComponent },
          { path: 'untitled', component: BlankComponent },
        ]),
        provideTransloco(translocoOptions),
        { provide: TitleStrategy, useExisting: PageTitleStrategy },
      ],
    });
  });

  async function activate(lang: 'fr' | 'en') {
    const transloco = TestBed.inject(TranslocoService);
    await firstValueFrom(transloco.load(lang));
    transloco.setActiveLang(lang);
  }

  it('names the site and the page, translated into French', async () => {
    await activate('fr');
    await TestBed.inject(Router).navigateByUrl('/login');

    expect(title()).toBe('Connexion | MeetStudent');
  });

  it('names the site and the page, translated into English', async () => {
    await activate('en');
    await TestBed.inject(Router).navigateByUrl('/login');

    expect(title()).toBe('Sign in | MeetStudent');
  });

  it('falls back to the site name for a route without a title', async () => {
    await activate('en');
    await TestBed.inject(Router).navigateByUrl('/untitled');

    expect(title()).toBe('MeetStudent');
  });

  it('lets a page put its own name (a school) in the title', () => {
    TestBed.inject(PageTitleService).set('Harvard');

    expect(title()).toBe('Harvard | MeetStudent');
  });

  it('gives every screen of the app a title', () => {
    const untitled = (list: Route[], prefix = ''): string[] =>
      list.flatMap((route) => {
        const path = [prefix, route.path].filter(Boolean).join('/');
        if (route.children) {
          return untitled(route.children, path);
        }
        return route.loadComponent && !route.title ? [path] : [];
      });

    expect(untitled(routes)).toEqual([]);
  });
});
