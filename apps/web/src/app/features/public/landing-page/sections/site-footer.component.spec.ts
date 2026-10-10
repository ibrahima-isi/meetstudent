import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { SiteFooterComponent } from './site-footer.component';

describe('SiteFooterComponent', () => {
  let fixture: ComponentFixture<SiteFooterComponent>;

  const root = () => fixture.nativeElement as HTMLElement;
  const hrefs = () =>
    Array.from(root().querySelectorAll('a')).map((a) => a.getAttribute('href'));

  async function render(lang: 'fr' | 'en', now?: Date) {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    if (now) {
      // Mock the clock only around construction, never while Transloco loads.
      jasmine.clock().install();
      jasmine.clock().mockDate(now);
    }
    try {
      fixture = TestBed.createComponent(SiteFooterComponent);
    } finally {
      if (now) {
        jasmine.clock().uninstall();
      }
    }
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter([]), provideTransloco(translocoOptions)],
    });
  });

  afterEach(() => {
    jasmine.clock().uninstall();
  });

  it('is a footer landmark', async () => {
    await render('fr');
    expect(root().querySelectorAll('footer').length).toBe(1);
    expect(root().querySelector('footer')!.getAttribute('role')).toBe('contentinfo');
  });

  it('links the brand to the language home', async () => {
    await render('fr');
    const brand = root().querySelector<HTMLAnchorElement>('footer a[data-testid="footer-brand"]')!;
    expect(brand).not.toBeNull();
    expect(brand.getAttribute('href')).toBe('/fr');
    expect(brand.textContent).toContain('MeetStudent');
  });

  it('links to schools, login and register in French', async () => {
    await render('fr');
    expect(hrefs()).toContain('/fr/schools');
    expect(hrefs()).toContain('/fr/login');
    expect(hrefs()).toContain('/fr/register');
  });

  it('links in English after switching language', async () => {
    await render('en');
    expect(hrefs()).toEqual(
      jasmine.arrayContaining(['/en', '/en/schools', '/en/login', '/en/register']),
    );
    expect(hrefs().some((h) => h?.startsWith('/fr'))).toBeFalse();
  });

  it('has only internal links', async () => {
    await render('fr');
    const links = Array.from(root().querySelectorAll('a'));
    expect(links.length).toBeGreaterThan(0);
    for (const a of links) {
      expect(a.getAttribute('href')).withContext(a.outerHTML).toMatch(/^\/(?!\/)/);
      expect(a.hasAttribute('target')).withContext(a.outerHTML).toBeFalse();
    }
  });

  it('shows the current year', async () => {
    await render('fr', new Date('2030-05-01T12:00:00Z'));
    expect(root().textContent).toContain('2030');
  });

  it('shows the rights text and tagline in French', async () => {
    await render('fr');
    expect(root().textContent).toContain('Tous droits réservés.');
    expect(root().textContent).toContain("Trouvez l'école qui vous correspond.");
  });

  it('shows the rights text and tagline in English', async () => {
    await render('en');
    expect(root().textContent).toContain('All rights reserved.');
    expect(root().textContent).toContain('Find the school that suits you.');
  });

  it('gives each nav landmark a distinct accessible name', async () => {
    await render('fr');
    const navs = Array.from(root().querySelectorAll('footer nav'));
    expect(navs.length).toBe(2);
    expect(navs.map((n) => n.getAttribute('aria-label'))).toEqual(['Produit', 'Compte']);
  });

  it('has no h1, so it never competes with the page title', async () => {
    await render('fr');
    expect(root().querySelector('footer')).not.toBeNull();
    expect(root().querySelectorAll('h1').length).toBe(0);
  });
});
