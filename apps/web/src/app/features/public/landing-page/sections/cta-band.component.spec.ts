import { provideZonelessChangeDetection, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { CtaBandComponent } from './cta-band.component';

describe('CtaBandComponent', () => {
  let fixture: ComponentFixture<CtaBandComponent>;
  let authenticated: WritableSignal<boolean>;

  const root = () => fixture.nativeElement as HTMLElement;

  async function render(lang: 'fr' | 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(CtaBandComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    authenticated = signal(false);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { isAuthenticated: authenticated } },
      ],
    });
  });

  describe('before hydration, when the pre-paint script found a session', () => {
    beforeEach(() => document.documentElement.classList.add('has-session'));
    afterEach(() => document.documentElement.classList.remove('has-session'));

    it('keeps the whole band hidden until the component takes over, so no flash and no layout shift', async () => {
      await render('fr');
      const host = root();
      host.classList.remove('hydrated');
      expect(getComputedStyle(host).display).toBe('none');
    });

    it('shows the band again once hydrated and the session turns out invalid', async () => {
      await render('fr');
      expect(root().classList).toContain('hydrated');
      expect(getComputedStyle(root()).display).not.toBe('none');
    });
  });

  it('has one h2 with the title and no h1', async () => {
    await render('fr');
    expect(root().querySelectorAll('h1').length).toBe(0);
    expect(root().querySelectorAll('h2').length).toBe(1);
    expect(root().querySelector('h2')!.textContent!.trim()).toBe('Prêt à trouver votre voie ?');
  });

  it('offers the primary action to /fr/register', async () => {
    await render('fr');
    const primary = root().querySelector<HTMLAnchorElement>('a.btn.btn-primary.btn-lg')!;
    expect(primary).not.toBeNull();
    expect(primary.getAttribute('href')).toBe('/fr/register');
    expect(primary.textContent!.trim()).toBe('Créer un compte gratuit');
  });

  it('offers the secondary action to /fr/schools', async () => {
    await render('fr');
    const secondary = root().querySelector<HTMLAnchorElement>('a.btn.btn-secondary.btn-lg')!;
    expect(secondary).not.toBeNull();
    expect(secondary.getAttribute('href')).toBe('/fr/schools');
    expect(secondary.textContent!.trim()).toBe('Parcourir les établissements');
  });

  it('follows the active language', async () => {
    await render('en');
    expect(root().querySelector('a.btn-primary')!.getAttribute('href')).toBe('/en/register');
    expect(root().querySelector('a.btn-secondary')!.getAttribute('href')).toBe('/en/schools');
    expect(root().querySelector('h2')!.textContent!.trim()).toBe('Ready to find your path?');
  });

  it('shows the supporting text', async () => {
    await render('en');
    expect(root().textContent).toContain('Create your free account and save your favourite schools.');
  });

  it('puts the horizontal gutter on the host and centres the section', async () => {
    await render('fr');
    const host = root();
    for (const cls of ['px-4', 'sm:px-6', 'lg:px-8']) {
      expect(host.classList).withContext(cls).toContain(cls);
    }
    const section = root().querySelector('section')!;
    expect(section.classList).toContain('mx-auto');
    expect(section.classList).toContain('max-w-7xl');
    expect(section.className).not.toMatch(/(^|\s)(mx-4|sm:mx-6|lg:mx-auto)(\s|$)/);
  });

  it('creates a stacking context for the mesh backdrop', async () => {
    await render('fr');
    const container = root().querySelector('app-mesh-background')!.parentElement!;
    for (const cls of ['relative', 'isolate', 'overflow-hidden']) {
      expect(container.classList).withContext(cls).toContain(cls);
    }
  });

  describe('when the visitor is signed in', () => {
    beforeEach(() => authenticated.set(true));

    it('renders no section, text or buttons', async () => {
      await render('fr');
      expect(root().querySelector('section')).toBeNull();
      expect(root().querySelector('h2')).toBeNull();
      expect(root().querySelectorAll('a').length).toBe(0);
      expect(root().textContent!.trim()).toBe('');
    });

    it('leaves no spacing classes on the host, so no gap remains', async () => {
      await render('fr');
      expect(root().className).not.toMatch(/(^|\s)(block|px-4|py-20)(\s|$)/);
    });
  });

  describe('on a server-rendered document', () => {
    beforeEach(() => document.body.setAttribute('ng-server-context', 'ssr'));
    afterEach(() => document.body.removeAttribute('ng-server-context'));

    it('renders the band first, so hydration matches the server markup', () => {
      authenticated.set(true);
      fixture = TestBed.createComponent(CtaBandComponent);
      expect((fixture.componentInstance as unknown as { signedIn(): boolean }).signedIn()).toBeFalse();
    });

    it('hides the band for a signed-in user once the first render is done', async () => {
      authenticated.set(true);
      await render('fr');
      expect(root().querySelector('section')).toBeNull();
      expect(root().classList).not.toContain('py-20');
    });
  });

  describe('when the visitor is signed out', () => {
    it('keeps both actions and the host spacing', async () => {
      await render('fr');
      expect(root().querySelectorAll('a.btn').length).toBe(2);
      expect(root().classList).toContain('py-20');
    });
  });

  it('follows login and logout without a reload', async () => {
    await render('fr');
    expect(root().querySelector('section')).not.toBeNull();

    authenticated.set(true);
    fixture.detectChanges();
    expect(root().querySelector('section')).toBeNull();
    expect(root().classList).not.toContain('py-20');

    authenticated.set(false);
    fixture.detectChanges();
    expect(root().querySelectorAll('a.btn').length).toBe(2);
    expect(root().classList).toContain('py-20');
  });
});
