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
import { User } from '@models/entities';
import { environment } from '../../../../environments/environment';
import { ProfilePageComponent } from './profile-page.component';

describe('ProfilePageComponent translations', () => {
  let fixture: ComponentFixture<ProfilePageComponent>;
  let user: ReturnType<typeof signal<Partial<User> | null>>;

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
});
