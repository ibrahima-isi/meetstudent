import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { Program } from '@models/entities';
import { WishlistProgramItemComponent } from './wishlist-program-item.component';

describe('WishlistProgramItemComponent', () => {
  let fixture: ComponentFixture<WishlistProgramItemComponent>;

  const program = (extra: Partial<Program> = {}): Program => ({
    id: 11,
    name: 'Licence Informatique',
    schoolId: 4,
    schoolName: 'UGB',
    duration: 3,
    ...extra,
  });
  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent ?? '';

  async function render(value: Program, lang: 'fr' | 'en' = 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(WishlistProgramItemComponent);
    fixture.componentRef.setInput('program', value);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
      ],
    });
  });

  it('shows the programme name, its school and its duration', async () => {
    await render(program());

    expect(text()).toContain('Licence Informatique');
    expect(text()).toContain('UGB');
    expect(text()).toContain('3 years');
  });

  it('links to the programme page in the language being read', async () => {
    await render(program(), 'fr');

    const link = Array.from(el().querySelectorAll('a')).find((a) => /voir plus/i.test(a.textContent ?? ''));
    expect(link?.getAttribute('href')).toBe('/fr/programs/11');
  });

  it('still links to the programme when the API did not say which school it belongs to', async () => {
    await render(program({ schoolId: undefined }));

    expect(el().querySelector('a')?.getAttribute('href')).toBe('/en/programs/11');
  });

  it('reports the remove click and disables the button while pending', async () => {
    await render(program());
    const removed: Program[] = [];
    fixture.componentInstance.removed.subscribe((p) => removed.push(p));

    (el().querySelector('[data-testid="wishlist-program-remove"]') as HTMLButtonElement).click();
    expect(removed.map((p) => p.id)).toEqual([11]);

    fixture.componentRef.setInput('removing', true);
    fixture.detectChanges();
    expect((el().querySelector('[data-testid="wishlist-program-remove"]') as HTMLButtonElement).disabled).toBeTrue();
  });

  it('copes with a programme that has no school name or duration', async () => {
    await render(program({ schoolName: null, duration: null }));

    expect(text()).not.toContain('null');
    expect(text()).not.toContain('undefined');
    expect(text()).not.toContain('years');
  });
});
