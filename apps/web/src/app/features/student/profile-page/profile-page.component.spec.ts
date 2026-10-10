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
  let setUser: jasmine.Spy;

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
    setUser = jasmine.createSpy('setUser');
    user = signal<Partial<User> | null>({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_STUDENT' } });

    TestBed.configureTestingModule({
      imports: [ProfilePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { user, setUser } },
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

  it('has no theme toggle of its own (the shell navbar has it) but keeps the back button', async () => {
    await render('en');
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('app-theme-toggle')).toBeNull();
    expect(text()).toContain('Back');
    expect(root.querySelectorAll('h1').length).toBe(1);
  });

  it('shows the translated role, never the raw API name', async () => {
    await render('fr');
    expect(text()).toContain('Étudiant');
    expect(text()).not.toContain('ROLE_STUDENT');

    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_EXPERT' } });
    await render('en');
    expect(text()).toContain('Expert / Teacher');
    expect(text()).not.toContain('ROLE_EXPERT');
  });

  it('shows the documents panel and the wishlist to a student, and not the expert specialty', async () => {
    await render('en');

    expect(fixture.nativeElement.querySelector('app-user-documents')).not.toBeNull();
    expect(text()).toContain('BAC type');
    expect(text()).not.toContain('Specialty');
  });

  it('shows the specialty to an expert, with no documents panel', async () => {
    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_EXPERT' } });
    await render('en');

    expect(fixture.nativeElement.querySelector('app-user-documents')).toBeNull();
    expect(text()).toContain('Specialty');
    expect(text()).not.toContain('BAC type');
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
    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_STUDENT' }, qualification: 'bac-pro' });

    await render('fr');
    expect(readOnlyBac()).toBe('Bac Professionnel');
    expect(text()).not.toContain('bac-pro');

    await render('en');
    expect(readOnlyBac()).toBe('Vocational Baccalaureate');
  });

  it('shows a stored qualification that is not a known bac type as-is', async () => {
    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_STUDENT' }, qualification: 'Licence 2' });

    await render('en');

    expect(readOnlyBac()).toBe('Licence 2');
  });

  describe('saving', () => {
    const url = `${environment.apiUrl}/users/5`;
    let http: HttpTestingController;

    function field(selector: string): HTMLInputElement {
      return fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    }

    function type(selector: string, value: string): void {
      const input = field(selector);
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }

    async function settle() {
      fixture.detectChanges();
      await fixture.whenStable();
    }

    async function edit(role = 'ROLE_STUDENT'): Promise<void> {
      user.set({ id: 5, firstname: 'Awa', lastname: 'Diop', email: 'awa@example.com', role: { name: role } });
      await render('en');
      http = TestBed.inject(HttpTestingController);
      fixture.componentInstance.isEditing.set(true);
      await settle();
    }

    function save(): void {
      (fixture.nativeElement.querySelector('[data-testid="profile-save"]') as HTMLButtonElement).click();
    }

    it('PATCHes the edited fields to the API, without a password when it is left empty', async () => {
      await edit();
      type('[data-testid="profile-firstname"]', 'Awa Marie');

      save();

      const req = http.expectOne(url);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({
        firstname: 'Awa Marie',
        lastname: 'Diop',
        email: 'awa@example.com',
        qualification: '',
      });
      req.flush({ id: 5, firstname: 'Awa Marie', lastname: 'Diop', email: 'awa@example.com', role: { name: 'ROLE_STUDENT' } });
    });

    it('stores the saved user, leaves edit mode and says so', async () => {
      await edit();
      type('[data-testid="profile-firstname"]', 'Awa Marie');
      save();

      http.expectOne(url).flush({ id: 5, firstname: 'Awa Marie', lastname: 'Diop', email: 'awa@example.com', role: { name: 'ROLE_STUDENT' } });
      await settle();

      expect(setUser).toHaveBeenCalledWith(jasmine.objectContaining({ id: 5, firstname: 'Awa Marie' }));
      expect(fixture.componentInstance.isEditing()).toBeFalse();
      expect(text()).toContain('Awa Marie');
      expect(text()).toContain('Profile updated.');
    });

    it('sends a new password when one is typed', async () => {
      await edit();
      type('[data-testid="profile-password"]', 'n3wpassword');

      save();

      const req = http.expectOne(url);
      expect(req.request.body.password).toBe('n3wpassword');
      req.flush({ id: 5, firstname: 'Awa', lastname: 'Diop', email: 'awa@example.com', role: { name: 'ROLE_STUDENT' } });
    });

    it('does not call the API for a password under 8 characters, and says why', async () => {
      await edit();
      type('[data-testid="profile-password"]', 'short');

      save();
      await settle();

      http.expectNone(url);
      expect(text()).toContain('at least 8 characters');
      expect(fixture.componentInstance.isEditing()).toBeTrue();
    });

    it('does not call the API when a required field is empty', async () => {
      await edit();
      type('[data-testid="profile-firstname"]', '');

      save();
      await settle();

      http.expectNone(url);
      expect(text()).toContain('This field is required.');
    });

    it('shows the API message under the field it names and stays in edit mode', async () => {
      await edit();
      save();

      http.expectOne(url).flush({ email: 'Cet email est déjà utilisé' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      const message = fixture.nativeElement.querySelector('[data-testid="profile-error-email"]') as HTMLElement;
      expect(message.textContent).toContain('Cet email est déjà utilisé');
      expect(text()).toContain('Please correct the highlighted fields.');
      expect(setUser).not.toHaveBeenCalled();
      expect(fixture.componentInstance.isEditing()).toBeTrue();
    });

    it('drops the API message when the field is edited', async () => {
      await edit();
      save();
      http.expectOne(url).flush({ email: 'Cet email est déjà utilisé' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      type('[data-testid="profile-email"]', 'other@example.com');
      await settle();

      expect(fixture.nativeElement.querySelector('[data-testid="profile-error-email"]')).toBeNull();
    });

    it('shows a translated failure for any other error, keeping the edits', async () => {
      await edit();
      save();

      http.expectOne(url).flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
      await settle();

      expect(text()).toContain('Could not save your profile. Please try again.');
      expect(text()).not.toContain('boom');
      expect(fixture.componentInstance.isEditing()).toBeTrue();
      expect(field('[data-testid="profile-firstname"]').value).toBe('Awa');
    });

    it('disables the save button while the request is in flight', async () => {
      await edit();
      save();
      await settle();

      const button = fixture.nativeElement.querySelector('[data-testid="profile-save"]') as HTMLButtonElement;
      expect(button.disabled).toBeTrue();
      expect(button.textContent).toContain('Saving...');

      http.expectOne(url).flush({ id: 5, firstname: 'Awa', lastname: 'Diop', email: 'awa@example.com', role: { name: 'ROLE_STUDENT' } });
    });

    it('saves an expert specialty as the qualification', async () => {
      await edit('ROLE_EXPERT');
      type('[data-testid="profile-qualification"]', 'Physics');

      save();

      const req = http.expectOne(url);
      expect(req.request.body.qualification).toBe('Physics');
      req.flush({ id: 5, firstname: 'Awa', lastname: 'Diop', email: 'awa@example.com', qualification: 'Physics', role: { name: 'ROLE_EXPERT' } });
    });

    it('puts the saved values back when the edit is cancelled', async () => {
      await edit();
      type('[data-testid="profile-firstname"]', 'Changed');

      (fixture.nativeElement.querySelector('[data-testid="profile-cancel"]') as HTMLButtonElement).click();
      fixture.componentInstance.isEditing.set(true);
      await settle();

      expect(field('[data-testid="profile-firstname"]').value).toBe('Awa');
    });
  });
});
