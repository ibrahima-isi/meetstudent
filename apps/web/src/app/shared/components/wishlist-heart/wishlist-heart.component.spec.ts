import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { Program } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
import { WishlistHeartComponent } from './wishlist-heart.component';

describe('WishlistHeartComponent', () => {
  const program: Program = { id: 11, name: 'Licence Info' };
  let fixture: ComponentFixture<WishlistHeartComponent>;
  let session: ReturnType<typeof signal<{ id: number; role: { name: string } } | null>>;
  let saved: ReturnType<typeof signal<Program[]>>;
  let available: ReturnType<typeof signal<boolean>>;
  let toggleProgram: jasmine.Spy;
  let pending: ReturnType<typeof signal<boolean>>;

  const button = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-testid="program-wishlist-toggle"]');

  async function render(lang: 'fr' | 'en' = 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(WishlistHeartComponent);
    fixture.componentRef.setInput('program', program);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    session = signal<{ id: number; role: { name: string } } | null>({ id: 9, role: { name: 'ROLE_STUDENT' } });
    saved = signal<Program[]>([]);
    available = signal(true);
    toggleProgram = jasmine.createSpy('toggleProgram');
    pending = signal(false);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { user: session, isAuthenticated: () => session() !== null } },
        {
          provide: WishlistService,
          useValue: {
            available,
            programs: saved,
            hasProgram: (id: number) => saved().some((p) => p.id === id),
            isProgramPending: () => pending(),
            toggleProgram,
          },
        },
      ],
    });
  });

  it('offers to add a program that is not in the list yet', async () => {
    await render();

    expect(button()?.getAttribute('aria-pressed')).toBe('false');
    expect(button()?.getAttribute('aria-label')).toBe('Add to wishlist');
  });

  it('shows a program already in the list as pressed, in French too', async () => {
    saved.set([program]);
    await render('fr');

    expect(button()?.getAttribute('aria-pressed')).toBe('true');
    expect(button()?.getAttribute('aria-label')).toBe('Retirer des souhaits');
  });

  it('toggles the program for a student, without bubbling to a clickable card', async () => {
    await render();
    const parentClick = jasmine.createSpy('parent');
    (fixture.nativeElement as HTMLElement).addEventListener('click', parentClick);

    button()?.click();

    expect(toggleProgram).toHaveBeenCalledWith(program);
    expect(parentClick).not.toHaveBeenCalled();
  });

  it('is disabled while the request is in flight, so it cannot be pressed twice', async () => {
    pending.set(true);
    await render();

    expect(button()?.disabled).toBeTrue();
  });

  it('is hidden from an expert', async () => {
    available.set(false);
    await render();

    expect(button()).toBeNull();
  });

  it('invites an anonymous visitor to sign in instead of changing a list', async () => {
    session.set(null);
    await render('fr');
    const navigate = spyOn(TestBed.inject(Router), 'navigate');

    button()?.click();

    expect(toggleProgram).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'login'], { queryParams: { returnUrl: jasmine.any(String) } });
  });
});
