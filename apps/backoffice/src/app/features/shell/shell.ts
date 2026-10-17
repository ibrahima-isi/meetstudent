import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '@services/auth.service';
import { ThemeService } from '@services/theme.service';
import { TokenService } from '@services/token.service';

interface NavItem {
  path: string;
  label: string;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(keydown.escape)': 'closeMenu(true)' },
  template: `
    <div class="flex min-h-screen bg-background text-foreground">
      @if (menuOpen()) {
        <div data-backdrop (click)="closeMenu()" class="fixed inset-0 z-30 bg-foreground/40 md:hidden" aria-hidden="true"></div>
      }
      <aside
        id="main-sidebar"
        #sidebar
        [class.hidden]="!menuOpen()"
        class="fixed inset-y-0 left-0 z-40 w-60 shrink-0 overflow-y-auto border-r border-border bg-card md:static md:z-auto md:block md:overflow-visible"
      >
        <div class="px-5 py-4 text-lg font-semibold">MeetStudent <span class="text-xs font-normal text-muted-foreground">Admin</span></div>
        <nav aria-label="Navigation principale" class="flex flex-col gap-1 px-3">
          @for (item of nav; track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="bg-brand-soft text-brand-soft-foreground"
              class="rounded-md px-3 py-2 text-sm font-medium text-foreground hover:bg-accent"
            >
              {{ item.label }}
            </a>
          }
        </nav>
      </aside>
      <div class="flex min-w-0 flex-1 flex-col">
        <header class="flex items-center gap-3 border-b border-border bg-card px-4 py-3 md:justify-end md:gap-4 md:px-6">
          <button
            #menuToggle
            type="button"
            data-menu-toggle
            aria-controls="main-sidebar"
            [attr.aria-expanded]="menuOpen()"
            aria-label="Menu"
            (click)="toggleMenu()"
            class="btn btn-secondary md:hidden"
          >
            <span aria-hidden="true">&#9776;</span>
          </button>
          <span class="ml-auto min-w-0 truncate text-sm text-muted-foreground md:ml-0">{{ displayName() }}</span>
          <button
            type="button"
            data-theme-toggle
            aria-label="Basculer le thème"
            [attr.aria-pressed]="theme.resolved() === 'dark'"
            (click)="theme.toggle()"
            class="btn btn-ghost shrink-0"
          >
            <span aria-hidden="true">{{ theme.resolved() === 'dark' ? '☀' : '☾' }}</span>
          </button>
          <button
            type="button"
            data-logout
            (click)="logout()"
            class="btn btn-secondary shrink-0"
          >
            Se déconnecter
          </button>
        </header>
        <main class="min-w-0 flex-1 p-4 md:p-6"><router-outlet /></main>
      </div>
    </div>
  `,
})
export class Shell {
  protected readonly theme = inject(ThemeService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly user = inject(TokenService).user;
  private readonly toggleButton = viewChild<ElementRef<HTMLButtonElement>>('menuToggle');
  private readonly sidebar = viewChild<ElementRef<HTMLElement>>('sidebar');

  readonly menuOpen = signal(false);

  readonly nav: NavItem[] = [
    { path: '/moderation', label: 'Modération' },
    { path: '/schools', label: 'Écoles' },
    { path: '/programs', label: 'Filières et cours' },
    { path: '/tags', label: 'Tags et accréditations' },
    { path: '/settings', label: 'Paramètres' },
  ];
  readonly displayName = computed(() => {
    const u = this.user();
    return u ? `${u.firstname} ${u.lastname}` : '';
  });

  constructor() {
    const sub = this.router.events.subscribe((e) => {
      if (e instanceof NavigationEnd) this.menuOpen.set(false);
    });
    inject(DestroyRef).onDestroy(() => sub.unsubscribe());
  }

  toggleMenu() {
    this.menuOpen.update((open) => !open);
    if (this.menuOpen()) {
      queueMicrotask(() => this.sidebar()?.nativeElement.querySelector<HTMLElement>('a')?.focus());
    }
  }

  closeMenu(restoreFocus = false) {
    if (!this.menuOpen()) return;
    this.menuOpen.set(false);
    if (restoreFocus) this.toggleButton()?.nativeElement.focus();
  }

  logout() {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
