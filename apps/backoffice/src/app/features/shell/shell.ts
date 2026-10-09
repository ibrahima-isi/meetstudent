import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '@services/auth.service';
import { TokenService } from '@services/token.service';

interface NavItem {
  path: string;
  label: string;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-screen bg-slate-50 text-slate-900">
      <aside class="w-60 shrink-0 border-r border-slate-200 bg-white">
        <div class="px-5 py-4 text-lg font-semibold">MeetStudent <span class="text-xs font-normal text-slate-500">Admin</span></div>
        <nav aria-label="Navigation principale" class="flex flex-col gap-1 px-3">
          @for (item of nav; track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="bg-indigo-50 text-indigo-700"
              class="rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              {{ item.label }}
            </a>
          }
        </nav>
      </aside>
      <div class="flex min-w-0 flex-1 flex-col">
        <header class="flex items-center justify-end gap-4 border-b border-slate-200 bg-white px-6 py-3">
          <span class="text-sm text-slate-600">{{ displayName() }}</span>
          <button
            type="button"
            data-logout
            (click)="logout()"
            class="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100"
          >
            Se déconnecter
          </button>
        </header>
        <main class="flex-1 p-6"><router-outlet /></main>
      </div>
    </div>
  `,
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly user = inject(TokenService).user;

  readonly nav: NavItem[] = [
    { path: '/moderation', label: 'Modération' },
    { path: '/schools', label: 'Écoles' },
    { path: '/programs', label: 'Filières et cours' },
    { path: '/tags', label: 'Tags et accréditations' },
  ];
  readonly displayName = computed(() => {
    const u = this.user();
    return u ? `${u.firstname} ${u.lastname}` : '';
  });

  logout() {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
