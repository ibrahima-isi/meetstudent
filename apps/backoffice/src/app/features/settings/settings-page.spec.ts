import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SettingsPage } from './settings-page';
import { API_URL } from '@services/api-config';

const api = 'http://api.test/api/v1';
const url = `${api}/site-settings`;
const settings = { contactEmail: 'contact@meetstudent.com', aboutFr: 'Bonjour.\n\nSuite.', aboutEn: 'Hello.' };

describe('SettingsPage', () => {
  let fixture: ComponentFixture<SettingsPage>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const settle = () => fixture.detectChanges();
  const load = () => {
    backend.expectOne(url).flush(settings);
    settle();
  };
  const type = (sel: string, value: string) => {
    const field = q<HTMLInputElement | HTMLTextAreaElement>(sel)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    settle();
  };
  const submit = () => {
    q<HTMLFormElement>('form[data-form]')!.dispatchEvent(new Event('submit'));
    settle();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('is titled "Paramètres du site" and shows a loading state, then the current values', () => {
    expect(q('h1')?.textContent).toContain('Paramètres du site');
    expect(el().textContent).toContain('Chargement');
    load();
    expect(el().textContent).not.toContain('Chargement');
    expect(q<HTMLInputElement>('#settings-contactEmail')!.value).toBe('contact@meetstudent.com');
    expect(q<HTMLTextAreaElement>('#settings-aboutFr')!.value).toBe('Bonjour.\n\nSuite.');
    expect(q<HTMLTextAreaElement>('#settings-aboutEn')!.value).toBe('Hello.');
  });

  it('shows an error with a retry that reloads', () => {
    backend.expectOne(url).flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role=alert]')?.textContent).toContain('Impossible de charger');
    expect(q('form[data-form]')).toBeNull();

    q<HTMLButtonElement>('button[data-retry]')!.click();
    settle();
    load();
    expect(q('form[data-form]')).toBeTruthy();
  });

  it('saves only what changed, so one language can change without the other', () => {
    load();
    type('#settings-aboutEn', '  New English text.  ');
    submit();

    const req = backend.expectOne(url);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ aboutEn: 'New English text.' });
    req.flush({ ...settings, aboutEn: 'New English text.' });
    settle();

    expect(q('[role=status]')?.textContent).toContain('Paramètres enregistrés');
    expect(q<HTMLTextAreaElement>('#settings-aboutEn')!.value).toBe('New English text.');
  });

  it('sends nothing when nothing changed', () => {
    load();
    submit();
    expect(q('[role=status]')?.textContent).toContain('Aucune modification');
  });

  it('refuses a blank text, an invalid email and an overlong text without calling the API', () => {
    load();
    type('#settings-aboutFr', '   ');
    type('#settings-contactEmail', 'not-an-email');
    type('#settings-aboutEn', 'a'.repeat(5001));
    submit();

    expect(q('[data-error=aboutFr]')?.textContent).toContain('obligatoire');
    expect(q('[data-error=contactEmail]')?.textContent).toContain('adresse e-mail valide');
    expect(q('[data-error=aboutEn]')?.textContent).toContain('5000');
    expect(q('#settings-aboutFr')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('shows the messages of the API under their fields', () => {
    load();
    type('#settings-contactEmail', 'a@b.sn');
    submit();
    backend.expectOne(url).flush({ contactEmail: 'contactEmail must be a valid email address' }, { status: 400, statusText: 'Bad Request' });
    settle();

    expect(q('[data-error=contactEmail]')?.textContent).toContain('must be a valid email address');
    expect(q('[role=status]')).toBeNull();
  });

  it('reports a failed save and keeps what was typed', () => {
    load();
    type('#settings-aboutFr', 'Nouveau texte.');
    submit();
    backend.expectOne(url).flush(null, { status: 500, statusText: 'err' });
    settle();

    expect(q('[role=alert]')?.textContent).toContain("L'enregistrement a échoué");
    expect(q<HTMLTextAreaElement>('#settings-aboutFr')!.value).toBe('Nouveau texte.');
  });

  it('shows how many characters each text uses', () => {
    load();
    expect(q('[data-count=aboutEn]')?.textContent).toContain('6 / 5000');
  });

  describe('mail test', () => {
    const mailUrl = `${api}/admin/mail/test`;
    const button = () => q<HTMLButtonElement>('[data-mail-test]')!;
    const result = () => q('[data-mail-test-result]');
    const click = () => {
      button().click();
      settle();
    };
    const answer = (status: number, body: string | object | null, headers: Record<string, string> = {}) => {
      backend.expectOne(mailUrl).flush(body, { status, statusText: 'x', headers });
      settle();
    };

    beforeEach(() => load());

    it('has a titled section, a button and says the mail goes to the admin\'s own address', () => {
      expect(q('[data-mail-test-section]')!.textContent).toContain("Tester l'envoi d'e-mails");
      expect(q('[data-mail-test-section]')!.textContent).toContain('votre propre adresse');
      expect(button().textContent).toContain("Envoyer un e-mail de test");
      expect(button().disabled).toBeFalse();
      expect(result()).toBeNull();
    });

    it('is available even when the site settings failed to load', () => {
      fixture = TestBed.createComponent(SettingsPage);
      fixture.detectChanges();
      backend.expectOne(url).flush(null, { status: 500, statusText: 'err' });
      settle();
      expect(q('[data-mail-test]')).not.toBeNull();
    });

    it('posts once, shows a loading state and disables the button while in flight', () => {
      click();
      expect(button().disabled).toBeTrue();
      expect(el().querySelector('[data-mail-test-section]')!.textContent).toContain('Envoi en cours');
      expect(button().getAttribute('aria-busy')).toBe('true');
      button().click();
      const req = backend.expectOne(mailUrl);
      expect(req.request.method).toBe('POST');
      req.flush({ status: 'SENT' });
      settle();
      expect(button().disabled).toBeFalse();
      expect(button().getAttribute('aria-busy')).toBeNull();
    });

    const outcomes: [number, string | object, string, 'status' | 'alert'][] = [
      [200, { status: 'SENT' }, "E-mail de test envoyé : vérifiez votre boîte de réception", 'status'],
      [200, { status: 'LOGGED' }, "Mode développement : l'e-mail a seulement été écrit dans les journaux de l'API, rien n'a été envoyé", 'status'],
      [503, { status: 'DISABLED' }, "L'envoi d'e-mails est désactivé (MAIL_ENABLED=false)", 'alert'],
      [502, { status: 'AUTH_FAILED' }, 'Identifiants SMTP refusés : vérifiez SPRING_MAIL_USERNAME et SPRING_MAIL_PASSWORD', 'alert'],
      [502, { status: 'CONNECTION_FAILED' }, 'Serveur SMTP injoignable : vérifiez SPRING_MAIL_HOST et SPRING_MAIL_PORT', 'alert'],
      [502, { status: 'TLS_FAILED' }, 'Échec TLS/STARTTLS : vérifiez le port et les options STARTTLS/SSL', 'alert'],
      [502, { status: 'REJECTED' }, 'Adresse refusée par le serveur SMTP : vérifiez MAIL_FROM (expéditeur validé chez le fournisseur) et votre adresse', 'alert'],
      [502, { status: 'FAILED' }, "Échec de l'envoi (cause non précisée) : consultez les journaux de l'API", 'alert'],
      [429, { message: 'Too many' }, 'Patientez quelques secondes avant un nouvel essai', 'alert'],
    ];
    for (const [status, body, text, role] of outcomes) {
      it(`HTTP ${status} ${JSON.stringify(body)} reads "${text.slice(0, 30)}…" in a role=${role} region`, () => {
        click();
        answer(status, body);
        expect(result()!.textContent).toContain(text);
        expect(result()!.getAttribute('role')).toBe(role);
      });
    }

    it('uses Retry-After in the 429 message when present', () => {
      click();
      answer(429, { message: 'x' }, { 'Retry-After': '8' });
      expect(result()!.textContent).toContain('Patientez 8 secondes avant un nouvel essai');
    });

    it('says 1 seconde, singular, for Retry-After: 1', () => {
      click();
      answer(429, { message: 'x' }, { 'Retry-After': '1' });
      expect(result()!.textContent).toContain('Patientez 1 seconde avant un nouvel essai');
    });

    it('never shows raw server text, and replaces the previous result on a new try', () => {
      click();
      answer(502, { status: 'FAILED', message: 'smtp.secret.host: password=hunter2 <b>x</b>' });
      expect(el().textContent).not.toContain('hunter2');
      expect(el().textContent).not.toContain('smtp.secret.host');
      click();
      answer(200, { status: 'SENT' });
      expect(result()!.textContent).toContain('E-mail de test envoyé');
      expect(result()!.textContent).not.toContain("Échec de l'envoi");
    });

    it('reports an unexpected failure (500, network) generically, without server text', () => {
      click();
      answer(500, { message: 'stacktrace here' });
      expect(result()!.getAttribute('role')).toBe('alert');
      expect(result()!.textContent).toContain("Le test d'envoi a échoué");
      expect(el().textContent).not.toContain('stacktrace');
      expect(button().disabled).toBeFalse();
    });

    it('does not touch the site settings form', () => {
      type('#settings-aboutEn', 'Edited');
      click();
      answer(200, { status: 'SENT' });
      expect(q<HTMLTextAreaElement>('#settings-aboutEn')!.value).toBe('Edited');
    });
  });

  describe('social links', () => {
    const links = [
      ['whatsappUrl', 'WhatsApp'],
      ['telegramUrl', 'Telegram'],
      ['discordUrl', 'Discord'],
      ['facebookUrl', 'Facebook'],
      ['twitterUrl', 'X (Twitter)'],
      ['instagramUrl', 'Instagram'],
      ['tiktokUrl', 'TikTok'],
    ] as const;
    const withLinks = {
      ...settings,
      whatsappUrl: 'https://wa.me/221770000000',
      telegramUrl: null,
      discordUrl: null,
      facebookUrl: null,
      twitterUrl: null,
      instagramUrl: null,
      tiktokUrl: null,
    };
    const loadWithLinks = () => {
      backend.expectOne(url).flush(withLinks);
      settle();
    };

    it('shows the seven link fields with their label and hint, empty when the API sends null', () => {
      loadWithLinks();
      for (const [name, label] of links) {
        expect(q(`label[for=settings-${name}]`)?.textContent).toContain(label);
        expect(q(`#settings-${name}-hint`)?.textContent).toContain('URL complète en https, vide pour retirer');
      }
      expect(q<HTMLInputElement>('#settings-whatsappUrl')!.value).toBe('https://wa.me/221770000000');
      expect(q<HTMLInputElement>('#settings-telegramUrl')!.value).toBe('');
    });

    it('treats links missing from an older API answer as empty and sends nothing', () => {
      load();
      expect(q<HTMLInputElement>('#settings-tiktokUrl')!.value).toBe('');
      submit();
      expect(q('[role=status]')?.textContent).toContain('Aucune modification');
    });

    it('sends only the link that changed, without its surrounding blanks', () => {
      loadWithLinks();
      type('#settings-telegramUrl', '  https://t.me/meetstudent ');
      submit();

      const req = backend.expectOne(url);
      expect(req.request.body).toEqual({ telegramUrl: 'https://t.me/meetstudent' });
      req.flush({ ...withLinks, telegramUrl: 'https://t.me/meetstudent' });
      settle();
      expect(q<HTMLInputElement>('#settings-telegramUrl')!.value).toBe('https://t.me/meetstudent');
    });

    it('sends an empty string to erase a link that had a value', () => {
      loadWithLinks();
      type('#settings-whatsappUrl', '   ');
      submit();

      const req = backend.expectOne(url);
      expect(req.request.body).toEqual({ whatsappUrl: '' });
      req.flush({ ...withLinks, whatsappUrl: null });
      settle();
      expect(q<HTMLInputElement>('#settings-whatsappUrl')!.value).toBe('');
      expect(q('[role=status]')?.textContent).toContain('Paramètres enregistrés');
    });

    it('does not send a link left empty that was already empty', () => {
      loadWithLinks();
      type('#settings-discordUrl', '');
      submit();
      expect(q('[role=status]')?.textContent).toContain('Aucune modification');
    });

    it('refuses a link that is not a full https URL, or is over 255 characters, without calling the API', () => {
      loadWithLinks();
      const bad: [string, string][] = [
        ['whatsappUrl', 'http://wa.me/1'],
        ['telegramUrl', 'javascript:alert(1)'],
        ['discordUrl', '/relative'],
        ['facebookUrl', 'https://'],
        ['twitterUrl', 'https://exa mple.com'],
        ['instagramUrl', 'HTTPS://instagram.com/x'],
        ['tiktokUrl', 'https://tiktok.com/' + 'a'.repeat(240)],
      ];
      for (const [name, value] of bad) type(`#settings-${name}`, value);
      submit();

      for (const [name] of bad) {
        expect(q(`[data-error=${name}]`)?.textContent).toBeTruthy();
        expect(q(`#settings-${name}`)!.getAttribute('aria-invalid')).toBe('true');
      }
      expect(q('[data-error=tiktokUrl]')?.textContent).toContain('255');
      expect(q('[data-error=whatsappUrl]')?.textContent).toContain('https');
    });

    it('shows the API message of a link under its field', () => {
      loadWithLinks();
      type('#settings-facebookUrl', 'https://facebook.com/x');
      submit();
      backend
        .expectOne(url)
        .flush({ facebookUrl: 'facebookUrl must be a valid https URL' }, { status: 400, statusText: 'Bad Request' });
      settle();

      expect(q('[data-error=facebookUrl]')?.textContent).toContain('must be a valid https URL');
      expect(q('[role=status]')).toBeNull();
    });
  });
});
