import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { LocaleService } from '@services/locale.service';
import { ForumWriteState } from './forum-access';

/**
 * Says why the visitor cannot write: sign in (coming back here), teacher account pending or rejected
 * (with a link to the status), e-mail not confirmed (with a link to the profile, which can resend the link),
 * or administrator (read-only).
 */
@Component({
  selector: 'app-forum-gate',
  imports: [RouterLink, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (state() !== 'writer') {
    <div
      *transloco="let t"
      data-testid="forum-gate"
      role="note"
      class="flex flex-col gap-2 rounded-2xl border border-border bg-card p-4 text-sm text-foreground sm:flex-row sm:items-center sm:justify-between"
    >
      <p class="min-w-0 break-words">{{ t('forum.gate.' + key()) }}</p>
      @switch (state()) {
        @case ('anonymous') {
          <a class="btn btn-primary shrink-0" [routerLink]="['/', lang(), 'login']" [queryParams]="{ returnUrl: returnUrl() }">{{ t('forum.gate.login') }}</a>
        }
        @case ('pending') {
          <a class="btn btn-secondary shrink-0" [routerLink]="['/', lang(), 'profile']">{{ t('forum.gate.status') }}</a>
        }
        @case ('rejected') {
          <a class="btn btn-secondary shrink-0" [routerLink]="['/', lang(), 'profile']">{{ t('forum.gate.status') }}</a>
        }
        @case ('unverified') {
          <a class="btn btn-secondary shrink-0" [routerLink]="['/', lang(), 'profile']">{{ t('forum.gate.profile') }}</a>
        }
      }
    </div>
    }
  `,
})
export class ForumGateComponent {
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  /** Anything but `writer`: a writer needs no explanation. */
  readonly state = input.required<ForumWriteState>();

  protected readonly lang = computed(() => this.locale.active());
  protected readonly key = computed(() => this.state());
  /** The page being read, query string included; it follows the navigation (page changes keep the same component). */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );
  /** Where to come back to after signing in. */
  protected readonly returnUrl = computed(() => (this.state() === 'anonymous' ? this.url() : ''));
}
