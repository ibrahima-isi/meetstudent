import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { CtaBandComponent } from './cta-band.component';

describe('CtaBandComponent', () => {
  let fixture: ComponentFixture<CtaBandComponent>;

  const root = () => fixture.nativeElement as HTMLElement;

  async function render(lang: 'fr' | 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(CtaBandComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter([]), provideTransloco(translocoOptions)],
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

  it('creates a stacking context for the mesh backdrop', async () => {
    await render('fr');
    const container = root().querySelector('app-mesh-background')!.parentElement!;
    for (const cls of ['relative', 'isolate', 'overflow-hidden']) {
      expect(container.classList).withContext(cls).toContain(cls);
    }
  });
});
