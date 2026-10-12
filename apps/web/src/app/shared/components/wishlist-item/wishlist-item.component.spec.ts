import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { School } from '@models/entities';
import { WishlistItemComponent } from './wishlist-item.component';

describe('WishlistItemComponent', () => {
  let fixture: ComponentFixture<WishlistItemComponent>;

  const school = (extra: Partial<School> = {}): School =>
    ({
      id: 4,
      name: 'Université Gaston Berger de Saint-Louis',
      address: { location: 'Sanar', city: 'Saint-Louis', country: 'Sénégal' },
      tags: [{ id: 1, name: 'Université' }, { id: 2, name: 'Établissement public' }, { id: 3, name: 'Santé' }],
      programs: [{ id: 1 }, { id: 2 }, { id: 3 }],
      rating: 4.26,
      ...extra,
    }) as School;

  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent ?? '';

  async function render(value: School, lang: 'fr' | 'en' = 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(WishlistItemComponent);
    fixture.componentRef.setInput('school', value);
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

  it('shows the name, the city, the first tags, the number of programmes and the rating', async () => {
    await render(school());

    expect(text()).toContain('Université Gaston Berger de Saint-Louis');
    expect(text()).toContain('Saint-Louis');
    expect(text()).toContain('Université');
    expect(text()).toContain('Établissement public');
    expect(text()).not.toContain('Santé'); // two tags at most: the card stays compact
    expect(text()).toContain('3 programmes');
    expect(text()).toContain('4.3');
  });

  it('says "1 programme" for one and counts in French too', async () => {
    await render(school({ programs: [{ id: 1 } as never] }), 'fr');

    expect(text()).toContain('1 programme');
    expect(text()).not.toContain('1 programmes');
  });

  it('has a "View more" link to the school page, in the language being read', async () => {
    await render(school());

    const link = Array.from(el().querySelectorAll('a')).find((a) => /view more/i.test(a.textContent ?? ''));
    expect(link).toBeDefined();
    expect(link!.getAttribute('href')).toBe('/en/schools/4');
  });

  it('links to the French page when French is active', async () => {
    await render(school(), 'fr');

    const link = Array.from(el().querySelectorAll('a')).find((a) => /voir plus/i.test(a.textContent ?? ''));
    expect(link!.getAttribute('href')).toBe('/fr/schools/4');
  });

  it('shows the logo when the school has one, else its initials', async () => {
    await render(school({ logo: { id: 9, publicUrl: '/uploads/public/logo.png' } as never }));
    expect(el().querySelector('img')?.getAttribute('src')).toContain('/uploads/public/logo.png');

    await render(school({ logo: null }));
    expect(el().querySelector('img')).toBeNull();
    expect(el().querySelector('[data-testid="school-monogram"]')?.textContent?.trim()).toBe('UG');
  });

  it('tells the parent when the heart is removed, and does not remove anything itself', async () => {
    await render(school());
    const removed: School[] = [];
    fixture.componentInstance.removed.subscribe((s) => removed.push(s));

    (el().querySelector('[data-testid="wishlist-remove"]') as HTMLButtonElement).click();

    expect(removed.map((s) => s.id)).toEqual([4]);
  });

  it('disables the remove button while a change is pending', async () => {
    await render(school());
    fixture.componentRef.setInput('removing', true);
    fixture.detectChanges();

    expect((el().querySelector('[data-testid="wishlist-remove"]') as HTMLButtonElement).disabled).toBeTrue();
  });

  it('copes with a school that has no address, tags, programmes or rating', async () => {
    await render({ id: 5, name: 'Bare', address: null as never } as School);

    expect(text()).toContain('Bare');
    expect(text()).not.toContain('undefined');
    expect(text()).not.toContain('null');
    expect(text()).not.toContain('programmes');
  });
});
