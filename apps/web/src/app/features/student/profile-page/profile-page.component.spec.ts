import { ComponentFixture, TestBed } from '@angular/core/testing';
import { computed, provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
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
  let savedPrograms: ReturnType<typeof signal<unknown[]>>;
  let toggleProgram: jasmine.Spy;
  let programsError: ReturnType<typeof signal<boolean>>;
  let toggle: jasmine.Spy;
  let load: jasmine.Spy;
  let setUser: jasmine.Spy;
  let clear: jasmine.Spy;

  function q(selector: string): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(selector);
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function render(lang: 'fr' | 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();
    // The embedded documents panel asks for the user's media on init.
    const backend = TestBed.inject(HttpTestingController);
    backend.match(`${environment.apiUrl}/media/mine`).forEach((req) => req.flush([]));
    // So does the embedded email section: whether mail can be sent, and any pending change.
    backend.match(`${environment.apiUrl}/auth/capabilities`).forEach((req) => req.flush({ emailEnabled: false }));
    backend
      .match((req) => req.method === 'GET' && req.url.endsWith('/email-change'))
      .forEach((req) => req.flush({}));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    saved = signal<unknown[]>([]);
    savedPrograms = signal<unknown[]>([]);
    toggleProgram = jasmine.createSpy('toggleProgram');
    programsError = signal(false);
    toggle = jasmine.createSpy('toggle');
    load = jasmine.createSpy('load');
    setUser = jasmine.createSpy('setUser');
    clear = jasmine.createSpy('clear').and.callFake(() => user.set(null));
    user = signal<Partial<User> | null>({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_STUDENT' } });

    TestBed.configureTestingModule({
      imports: [ProfilePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { user, setUser, clear } },
        { provide: WishlistService, useValue: { available: computed(() => user()?.role?.name === 'ROLE_STUDENT'), schools: saved, programs: savedPrograms, programsError, load, toggle, toggleProgram, isPending: () => false, isProgramPending: () => false } },
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

  it('shows a pending expert that the account awaits validation', async () => {
    user.set({ firstname: 'Awa', lastname: 'D', email: '', role: { name: 'ROLE_EXPERT' }, expertStatus: 'PENDING' });
    await render('fr');

    expect(q('[data-testid="expert-status"]')?.textContent).toContain('En attente de validation');
  });

  it('shows a rejected expert the decision and the reason', async () => {
    user.set({ firstname: 'Awa', lastname: 'D', email: '', role: { name: 'ROLE_EXPERT' }, expertStatus: 'REJECTED', expertStatusReason: 'Diplôme illisible' });
    await render('en');

    expect(q('[data-testid="expert-status"]')?.textContent).toContain('rejected');
    expect(q('[data-testid="expert-status-reason"]')?.textContent).toContain('Diplôme illisible');
  });

  it('shows an approved expert the confirmation', async () => {
    user.set({ firstname: 'Awa', lastname: 'D', email: '', role: { name: 'ROLE_EXPERT' }, expertStatus: 'APPROVED' });
    await render('en');

    expect(q('[data-testid="expert-status"]')?.textContent).toContain('approved');
    expect(q('[data-testid="expert-status-reason"]')).toBeNull();
  });

  it('shows no approval status to a student', async () => {
    await render('fr');

    expect(q('[data-testid="expert-status"]')).toBeNull();
  });

  it('refreshes the status of an expert from the API so a decision shows without signing in again', async () => {
    user.set({ id: 7, firstname: 'Awa', lastname: 'D', email: '', role: { name: 'ROLE_EXPERT' }, expertStatus: 'PENDING' });
    await render('en');
    const req = TestBed.inject(HttpTestingController).expectOne(`${environment.apiUrl}/users/id/7`);
    req.flush({ id: 7, firstname: 'Awa', lastname: 'D', email: '', role: { name: 'ROLE_EXPERT' }, expertStatus: 'APPROVED' });
    fixture.detectChanges();

    expect(q('[data-testid="expert-status"]')?.textContent).toContain('approved');
    expect(setUser).toHaveBeenCalledWith(jasmine.objectContaining({ expertStatus: 'APPROVED' }));
  });

  it('shows the specialty to an expert, with no documents panel', async () => {
    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_EXPERT' } });
    await render('en');

    expect(fixture.nativeElement.querySelector('app-user-documents')).toBeNull();
    expect(text()).toContain('Specialty');
    expect(text()).not.toContain('BAC type');
  });

  it('does not load or show a student wishlist to an expert', async () => {
    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_EXPERT' } });
    saved.set([{ id: 3, name: 'Old student school' }]);
    await render('en');

    expect(load).not.toHaveBeenCalled();
    expect(text()).not.toContain('My wishlist');
    expect(fixture.nativeElement.querySelector('[data-testid="wishlist-remove"]')).toBeNull();
  });

  it('hides the wishlist when the current session changes from student to expert', async () => {
    await render('en');
    expect(text()).toContain('My wishlist');

    user.set({ firstname: 'Awa', lastname: '', email: '', role: { name: 'ROLE_EXPERT' } });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(text()).not.toContain('My wishlist');
  });

  it('lists the wishlisted schools from the API and removes one through the service', async () => {
    const school = { id: 3, name: 'Real School', address: { location: '', city: '', country: '' } };
    saved.set([school]);
    await render('en');

    expect(text()).toContain('Real School');
    (fixture.nativeElement.querySelector('[data-testid="wishlist-remove"]') as HTMLButtonElement).click();

    expect(toggle).toHaveBeenCalledWith(school);
  });

  it('introduces the user in the header: initials, full name and role', async () => {
    user.set({ firstname: 'Awa', lastname: 'Diop', email: 'awa@example.com', role: { name: 'ROLE_STUDENT' } });
    await render('en');

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="profile-avatar"]')?.textContent?.trim()).toBe('AD');
    expect(root.querySelector('[data-testid="profile-name"]')?.textContent?.trim()).toBe('Awa Diop');
    expect(root.querySelector('[data-testid="profile-role"]')?.textContent?.trim()).toBe('Student');
  });

  it('copes with a user who has only a first name', async () => {
    await render('en');

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="profile-avatar"]')?.textContent?.trim()).toBe('A');
    expect(root.querySelector('[data-testid="profile-name"]')?.textContent?.trim()).toBe('Awa');
  });

  it('shows each wishlisted school in detail, with a "View more" link to its page', async () => {
    saved.set([{ id: 3, name: 'Real School', address: { location: '', city: 'Dakar', country: '' }, programs: [{ id: 1 }] }]);
    await render('en');

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('app-wishlist-item')).not.toBeNull();
    const link = Array.from(root.querySelectorAll('a')).find((a) => /view more/i.test(a.textContent ?? ''));
    expect(link?.getAttribute('href')).toBe('/en/schools/3');
    expect(text()).toContain('1 programme');
  });

  it('shows the wishlisted programmes too, and removes one through the service', async () => {
    const programme = { id: 11, name: 'Licence Informatique', schoolId: 3, schoolName: 'Real School' };
    savedPrograms.set([programme]);
    await render('en');

    expect(text()).toContain('Licence Informatique');
    expect(text()).not.toContain('Explore schools');
    (fixture.nativeElement.querySelector('[data-testid="wishlist-program-remove"]') as HTMLButtonElement).click();
    expect(toggleProgram).toHaveBeenCalledWith(programme);
  });

  it('names the two groups when schools and programmes are both saved', async () => {
    saved.set([{ id: 3, name: 'Real School', address: { location: '', city: '', country: '' } }]);
    savedPrograms.set([{ id: 11, name: 'Licence Informatique', schoolId: 3 }]);
    await render('en');

    expect(text()).toContain('Schools');
    expect(text()).toContain('Programmes');
  });

  it('keeps the schools and notes it quietly when only the programmes failed to load', async () => {
    saved.set([{ id: 3, name: 'Real School', address: { location: '', city: '', country: '' } }]);
    programsError.set(true);
    await render('en');

    expect(text()).toContain('Real School');
    expect(text()).toContain('Your saved programmes could not be loaded.');
  });

  it('invites a student with an empty wishlist to explore the schools', async () => {
    await render('en');

    const link = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a')).find((a) => /explore schools/i.test(a.textContent ?? ''));
    expect(link?.getAttribute('href')).toBe('/en/schools');
  });

  it('keeps the email in its own card, apart from the personal information', async () => {
    await render('en');

    const email = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="profile-email-card"]');
    expect(email).not.toBeNull();
    expect(email!.querySelector('app-profile-email-section')).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="profile-info-card"] app-profile-email-section')).toBeNull();
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
        qualification: '',
      });
      req.flush({ id: 5, firstname: 'Awa Marie', lastname: 'Diop', email: 'awa@example.com', role: { name: 'ROLE_STUDENT' } });
    });

    it('leaves the email to its own section: no email field in the form, and the section is there', async () => {
      await edit();

      expect(fixture.nativeElement.querySelector('[data-testid="profile-email"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('[data-testid="profile-email-section"]')).not.toBeNull();
      expect(text()).toContain('awa@example.com');
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

    it('sends both passwords then clears the session and returns to localized login', async () => {
      await edit();
      const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      type('[data-testid="profile-password"]', 'n3wpassword');
      type('[data-testid="profile-current-password"]', 'old-password');
      save();
      const req = http.expectOne(url);
      expect(req.request.body.password).toBe('n3wpassword');
      expect(req.request.body.currentPassword).toBe('old-password');
      req.flush({ id: 5, firstname: 'Awa', lastname: 'Diop' });
      await settle();
      expect(user()).toBeNull();
      expect(setUser).not.toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledWith(['/', 'en', 'login'], { queryParams: { passwordChanged: '1' } });
    });

    it('associates a rejected current password with its input and preserves the session', async () => {
      await edit();
      type('[data-testid="profile-password"]', 'n3wpassword');
      type('[data-testid="profile-current-password"]', 'wrong-password');
      save();
      http.expectOne(url).flush({ currentPassword: 'Mot de passe incorrect' }, { status: 400, statusText: 'Bad Request' });
      await settle();
      const input = field('[data-testid="profile-current-password"]');
      expect(input.getAttribute('aria-invalid')).toBe('true');
      expect(fixture.nativeElement.querySelector('#' + input.getAttribute('aria-describedby'))?.textContent).toContain('Mot de passe incorrect');
      expect(clear).not.toHaveBeenCalled();
      expect(fixture.componentInstance.isEditing()).toBeTrue();
    });

    it('requires the current password before sending a new password', async () => {
      await edit();
      type('[data-testid="profile-password"]', 'n3wpassword');
      save();
      await settle();
      http.expectNone(url);
      expect(text()).toContain('This field is required.');
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

      http.expectOne(url).flush({ firstname: 'Prénom invalide' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      const message = fixture.nativeElement.querySelector('[data-testid="profile-error-firstname"]') as HTMLElement;
      expect(message.textContent).toContain('Prénom invalide');
      expect(text()).toContain('Please correct the highlighted fields.');
      expect(setUser).not.toHaveBeenCalled();
      expect(fixture.componentInstance.isEditing()).toBeTrue();
    });

    it('drops the API message when the field is edited', async () => {
      await edit();
      save();
      http.expectOne(url).flush({ firstname: 'Prénom invalide' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      type('[data-testid="profile-firstname"]', 'Awa Marie');
      await settle();

      expect(fixture.nativeElement.querySelector('[data-testid="profile-error-firstname"]')).toBeNull();
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
