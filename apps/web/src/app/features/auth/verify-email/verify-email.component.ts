import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule, AlertCircle, CheckCircle } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';

type State = 'verifying' | 'success' | 'expired' | 'invalid' | 'error';

/** The confirmation link keeps its token in the URL fragment, which no server or log ever receives. */
function tokenFrom(fragment: string | null): string | null {
  return (fragment && new URLSearchParams(fragment).get('token')) || null;
}

/** What the link confirms: the account's address (default), or a new address replacing it (`type=change`). */
type Kind = 'verification' | 'change';

function kindFrom(fragment: string | null): Kind {
  return fragment && new URLSearchParams(fragment).get('type') === 'change' ? 'change' : 'verification';
}

/** The messages that read differently for an address change; the rest is shared. */
const CHANGE_WORDING = new Set(['verifyingTitle', 'verifyingText', 'successTitle', 'successText', 'expiredText', 'invalidText']);

/**
 * Confirms the email address. The token is read once from the fragment into
 * memory, removed from the address bar and history right after the first
 * render, posted straight away, and never written anywhere else.
 */
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, LucideAngularModule, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './verify-email.component.html',
})
export class VerifyEmailComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly meta = inject(Meta);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly locale = inject(LocaleService);
  protected readonly tokens = inject(TokenService);

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  /** Memory only; kept after a failure of the network alone, so the visitor can retry. */
  private token: string | null;
  private readonly kind: Kind;
  readonly state = signal<State>('verifying');

  protected readonly AlertCircle = AlertCircle;
  protected readonly CheckCircle = CheckCircle;

  constructor() {
    this.meta.updateTag({ name: 'robots', content: 'noindex,nofollow' });
    this.meta.updateTag({ name: 'referrer', content: 'no-referrer' });
    this.destroyRef.onDestroy(() => {
      this.meta.removeTag('name="robots"');
      this.meta.removeTag('name="referrer"');
    });

    const route = inject(ActivatedRoute);
    const fragment = route.snapshot.fragment;
    this.token = tokenFrom(fragment);
    this.kind = kindFrom(fragment);

    if (fragment) {
      // After the first render, not now: navigating while the router is still
      // activating this very route would cancel the navigation in flight.
      afterNextRender(
        () => {
          void this.router.navigate([], {
            relativeTo: route,
            queryParamsHandling: 'preserve',
            replaceUrl: true,
          });
        },
        { injector: this.injector },
      );
    }

    if (this.token) {
      this.confirm();
    } else {
      this.state.set('invalid');
    }
  }

  /** Translation key of a message of this page, worded for the kind of link that was opened. */
  protected key(name: string): string {
    return `auth.verify.${this.kind === 'change' && CHANGE_WORDING.has(name) ? `change${name[0].toUpperCase()}${name.slice(1)}` : name}`;
  }

  protected retry(): void {
    this.state.set('verifying');
    this.confirm();
  }

  private confirm(): void {
    const token = this.token;
    if (!token) {
      this.finish('invalid');
      return;
    }

    const confirmation = this.kind === 'change' ? this.auth.confirmEmailChange(token) : this.auth.confirmEmailVerification(token);
    confirmation.subscribe({
      next: () => {
        this.token = null;
        this.finish('success');
        if (this.tokens.isAuthenticated()) {
          // Best effort: the cached copy feeds the banner and, after a change, the address shown everywhere.
          this.auth.refreshCurrentUser().subscribe({ error: () => undefined });
        }
      },
      error: (err: unknown) => {
        const body = err instanceof HttpErrorResponse && err.status === 400 ? err.error : null;
        if (body && typeof body === 'object' && 'token' in body) {
          this.token = null;
          this.finish('expired');
        } else {
          this.finish('error');
        }
      },
    });
  }

  private finish(state: State): void {
    this.state.set(state);
    afterNextRender(() => this.heading()?.nativeElement.focus(), { injector: this.injector });
  }
}
