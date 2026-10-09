import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
import { User } from '@models/entities';
import { environment } from '../../../../environments/environment';
import { ProfilePageComponent } from './profile-page.component';

describe('ProfilePageComponent translations', () => {
  let fixture: ComponentFixture<ProfilePageComponent>;
  let user: ReturnType<typeof signal<Partial<User> | null>>;
  let saved: ReturnType<typeof signal<unknown[]>>;
  let toggle: jasmine.Spy;

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function render(lang: 'fr' | 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();
    // The embedded documents panel asks for the user's media on init.
    TestBed.inject(HttpTestingController)
      .match(`${environment.apiUrl}/media/mine`)
      .forEach((req) => req.flush([]));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    saved = signal<unknown[]>([]);
    toggle = jasmine.createSpy('toggle');
    user = signal<Partial<User> | null>({ firstname: 'Awa', lastname: '', email: '', role: { name: 'STUDENT' } });

    TestBed.configureTestingModule({
      imports: [ProfilePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { user } },
        { provide: WishlistService, useValue: { schools: saved, load: () => {}, toggle } },
      ],
    });
  });

  it('renders in French, with French authored for the name fields', async () => {
    await render('fr');

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Mon Profil');
    expect(text()).toContain('Prénom');
    expect(text()).not.toContain('First Name');
    expect(text()).toContain('Non renseigné');
  });

  it('renders in English when English is active', async () => {
    await render('en');

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('My Profile');
    expect(text()).toContain('First Name');
    expect(text()).toContain('Not provided');
    expect(text()).toContain('Student');
    expect(text()).toContain('My documents');
  });

  it('lists the wishlisted schools from the API and removes one through the service', async () => {
    const school = { id: 3, name: 'Real School', address: { location: '', city: '', country: '' } };
    saved.set([school]);
    await render('en');

    expect(text()).toContain('Real School');
    (fixture.nativeElement.querySelector('[data-testid="wishlist-remove"]') as HTMLButtonElement).click();

    expect(toggle).toHaveBeenCalledWith(school);
  });

  function bacOptions(): { value: string; label: string }[] {
    const select = fixture.nativeElement.querySelector('select:has(option[value="bac-general"])') as HTMLSelectElement;
    return Array.from(select.options)
      .filter((option) => option.value !== '')
      .map((option) => ({ value: option.value, label: option.textContent!.trim() }));
  }

  it('labels the bac options in French', async () => {
    await render('fr');
    fixture.componentInstance.isEditing.set(true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(bacOptions()).toEqual([
      { value: 'bac-general', label: 'Bac Général' },
      { value: 'bac-techno-sti2d', label: 'Bac Technologique - STI2D' },
      { value: 'bac-techno-stmg', label: 'Bac Technologique - STMG' },
      { value: 'bac-pro', label: 'Bac Professionnel' },
    ]);
  });

  it('labels the bac options in English without changing their values', async () => {
    await render('en');
    fixture.componentInstance.isEditing.set(true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(bacOptions()).toEqual([
      { value: 'bac-general', label: 'General Baccalaureate' },
      { value: 'bac-techno-sti2d', label: 'Technological Baccalaureate - STI2D' },
      { value: 'bac-techno-stmg', label: 'Technological Baccalaureate - STMG' },
      { value: 'bac-pro', label: 'Vocational Baccalaureate' },
    ]);
    expect(text()).not.toContain('Bac Général');
    expect(text()).not.toContain('Bac Professionnel');
  });

  function readOnlyBac(): string {
    const label = Array.from(fixture.nativeElement.querySelectorAll('label') as NodeListOf<HTMLElement>)
      .find((el) => el.querySelector('*') === null && /BAC|Bac/.test(el.textContent ?? ''))!;
    return label.parentElement!.querySelector('span')!.textContent!.trim();
  }

  it('shows the translated bac label, not the stored slug, outside edit mode', async () => {
    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'STUDENT' }, qualification: 'bac-pro' });

    await render('fr');
    expect(readOnlyBac()).toBe('Bac Professionnel');
    expect(text()).not.toContain('bac-pro');

    await render('en');
    expect(readOnlyBac()).toBe('Vocational Baccalaureate');
  });

  it('shows a stored qualification that is not a known bac type as-is', async () => {
    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'STUDENT' }, qualification: 'Licence 2' });

    await render('en');

    expect(readOnlyBac()).toBe('Licence 2');
  });
});
