import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { SocialLinksComponent } from './social-links.component';

describe('SocialLinksComponent', () => {
  let fixture: ComponentFixture<SocialLinksComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const links = () => Array.from(el().querySelectorAll('a')) as HTMLAnchorElement[];

  const all = {
    whatsappUrl: 'https://wa.me/221770000000',
    telegramUrl: 'https://t.me/meetstudent',
    discordUrl: 'https://discord.gg/abc',
    facebookUrl: 'https://facebook.com/meetstudent',
    twitterUrl: 'https://x.com/meetstudent',
    instagramUrl: 'https://instagram.com/meetstudent',
    tiktokUrl: 'https://tiktok.com/@meetstudent',
  };

  const show = async (value: unknown) => {
    fixture.componentRef.setInput('links', value);
    fixture.detectChanges();
    await fixture.whenStable();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection(), provideTransloco(translocoOptions)] });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    fixture = TestBed.createComponent(SocialLinksComponent);
  });

  it('renders the seven networks in a stable order, each opening safely in a new tab', async () => {
    await show(all);

    expect(links().length).toBe(7);
    expect(links().map((a) => a.dataset['network'])).toEqual(['whatsapp', 'telegram', 'discord', 'facebook', 'x', 'instagram', 'tiktok']);
    expect(links().map((a) => a.getAttribute('href'))).toEqual(Object.values(all));
    for (const a of links()) {
      expect(a.getAttribute('target')).toBe('_blank');
      expect(a.getAttribute('rel')).toBe('noopener noreferrer');
    }
  });

  it('names each link after its network and says it opens in a new tab, with the icon hidden from assistive tech', async () => {
    await show(all);

    const names = links().map((a) => a.textContent!.replace(/\s+/g, ' ').trim());
    expect(names[0]).toContain('WhatsApp');
    expect(names[4]).toContain('X (Twitter)');
    expect(names[6]).toContain('TikTok');
    for (const a of links()) {
      expect(a.textContent).toContain('nouvel onglet');
      expect(a.querySelector('.sr-only')).toBeTruthy();
      expect(a.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    }
  });

  it('does not render a network whose link is null, empty or blank', async () => {
    await show({ ...all, telegramUrl: null, discordUrl: '', facebookUrl: '   ', tiktokUrl: undefined });

    expect(links().map((a) => a.dataset['network'])).toEqual(['whatsapp', 'x', 'instagram']);
  });

  it('hides the whole block when no link is set', async () => {
    await show({ whatsappUrl: null, telegramUrl: '' });
    expect(el().querySelector('ul')).toBeNull();
    expect(el().querySelector('a')).toBeNull();

    await show(null);
    expect(el().querySelector('ul')).toBeNull();
  });

  it('only renders https links: never javascript:, data:, http: or a relative path', async () => {
    await show({
      whatsappUrl: 'javascript:alert(1)',
      telegramUrl: 'data:text/html,x',
      discordUrl: 'http://discord.gg/abc',
      facebookUrl: '//evil.example',
      twitterUrl: '/relative',
      instagramUrl: 'HTTPS://instagram.com/ok',
      tiktokUrl: 'https://tiktok.com/@ok',
    });

    expect(links().map((a) => a.dataset['network'])).toEqual(['instagram', 'tiktok']);
    expect(el().innerHTML).not.toContain('javascript:');
  });
});
