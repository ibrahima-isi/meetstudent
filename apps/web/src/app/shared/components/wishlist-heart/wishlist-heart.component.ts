import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { LucideAngularModule, Heart } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { Program } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';

/**
 * Heart button that adds a program to, or removes it from, the student's wishlist. Reusable on any
 * program card or page: it is hidden from experts (they have no wishlist), and an anonymous visitor
 * who presses it is sent to the login page, then brought back.
 */
@Component({
  selector: 'app-wishlist-heart',
  imports: [LucideAngularModule, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <button
        *transloco="let t"
        type="button"
        data-testid="program-wishlist-toggle"
        [attr.aria-pressed]="saved()"
        [attr.aria-label]="t(saved() ? 'wishlist.removeAction' : 'wishlist.addAction')"
        [disabled]="wishlist.isProgramPending(program().id)"
        (click)="onClick($event)"
        class="cursor-pointer rounded-full p-2 shadow-sm transition-colors disabled:cursor-not-allowed disabled:opacity-60"
        [class]="saved() ? 'bg-red-50 text-red-600 dark:bg-red-950 dark:text-red-400' : 'bg-card text-muted-foreground hover:text-foreground'"
      >
        <lucide-icon [img]="Heart" class="h-5 w-5" [class.fill-current]="saved()" aria-hidden="true"></lucide-icon>
      </button>
    }
  `,
})
export class WishlistHeartComponent {
  protected readonly wishlist = inject(WishlistService);
  private readonly tokenService = inject(TokenService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  readonly program = input.required<Program>();

  protected readonly Heart = Heart;
  protected readonly saved = computed(() => this.wishlist.hasProgram(this.program().id));
  /** Experts have no wishlist; anonymous visitors still see the heart, to be invited to sign in. */
  protected readonly visible = computed(() => !this.tokenService.isAuthenticated() || this.wishlist.available());

  protected onClick(event: Event): void {
    event.stopPropagation();
    if (!this.tokenService.isAuthenticated()) {
      void this.router.navigate(['/', this.locale.active(), 'login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.wishlist.toggleProgram(this.program());
  }
}
