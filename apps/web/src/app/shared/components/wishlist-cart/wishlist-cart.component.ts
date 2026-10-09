import { Component, computed, signal, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { LucideAngularModule, ShoppingCart, X, GraduationCap } from 'lucide-angular';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { WishlistService } from '@services/wishlist.service';
import { TokenService } from '@services/token.service';
import { LocaleService } from '@services/locale.service';
import { TranslocoDirective } from '@jsverse/transloco';
import { pluralKey } from '@i18n/plural';

@Component({
  selector: 'app-wishlist-cart',
  imports: [CommonModule, LucideAngularModule, TranslocoDirective, ErrorStateComponent],
  template: `
    <div class="relative" *transloco="let t">
      <button
        (click)="handleCartClick()"
        class="relative flex items-center gap-2 px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors cursor-pointer"
      >
        <lucide-icon [img]="ShoppingCart" class="w-5 h-5"></lucide-icon>
        <span>{{ t('wishlist.button') }}</span>
        @if (isAuthenticated() && wishlist.schools().length > 0) {
          <span class="absolute -top-1 -right-1 w-5 h-5 bg-indigo-600 text-white rounded-full flex items-center justify-center text-xs">
            {{ wishlist.schools().length }}
          </span>
        }
      </button>

      @if (isOpen() && isAuthenticated()) {
        <div class="fixed inset-0 z-40" (click)="isOpen.set(false)"></div>
        
        <div class="absolute left-0 top-full mt-2 w-96 bg-white rounded-xl shadow-xl border border-gray-200 z-50 max-h-[80vh] overflow-hidden flex flex-col">
          <div class="p-4 border-b border-gray-200">
            <div class="flex items-center justify-between">
              <h3 class="text-gray-900 font-bold">{{ t('wishlist.title') }}</h3>
              <button
                (click)="isOpen.set(false)"
                class="p-1 hover:bg-gray-100 rounded-lg transition-colors cursor-pointer"
              >
                <lucide-icon [img]="X" class="w-5 h-5"></lucide-icon>
              </button>
            </div>
            <p class="text-gray-600 mt-1 text-sm">
              {{ t(plural('wishlist.count', wishlist.schools().length), { count: wishlist.schools().length }) }}
            </p>
          </div>

          <div class="flex-1 overflow-y-auto">
            @if (wishlist.status() === 'loading') {
              <p role="status" aria-busy="true" class="p-8 text-center text-gray-500 animate-pulse">{{ t('common.loading') }}</p>
            } @else if (wishlist.status() === 'error') {
              <app-error-state [message]="t('wishlist.loadError')" (retry)="wishlist.load()" />
            } @else if (wishlist.schools().length === 0) {
              <div class="p-8 text-center">
                <lucide-icon [img]="GraduationCap" class="w-12 h-12 text-gray-300 mx-auto mb-3"></lucide-icon>
                <p class="text-gray-600">{{ t('wishlist.empty') }}</p>
                <p class="text-gray-500 mt-1 text-sm">{{ t('wishlist.emptyHint') }}</p>
              </div>
            } @else {
              <div class="p-4 space-y-3">
                @for (school of wishlist.schools(); track school.id) {
                  <div class="flex items-start gap-3 p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                    <lucide-icon [img]="GraduationCap" class="w-5 h-5 text-indigo-600 flex-shrink-0 mt-1"></lucide-icon>
                    <div class="flex-1 min-w-0">
                      <h4 class="text-gray-900 line-clamp-2 mb-1 text-sm font-medium">{{ school.name }}</h4>
                      <p class="text-gray-600 text-xs font-semibold">{{ school.address.city }}</p>
                    </div>
                    <button
                      data-testid="wishlist-remove"
                      [attr.aria-label]="t('wishlist.removeAction')"
                      [disabled]="wishlist.isPending(school.id)"
                      (click)="wishlist.toggle(school)"
                      class="p-1 hover:bg-white rounded-lg transition-colors flex-shrink-0 cursor-pointer"
                    >
                      <lucide-icon [img]="X" class="w-4 h-4 text-gray-500 hover:text-red-600"></lucide-icon>
                    </button>
                  </div>
                }
              </div>
            }
          </div>

          @if (wishlist.schools().length > 0) {
            <div class="p-4 border-t border-gray-200 bg-gray-50">
              <button class="w-full px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors font-medium text-sm cursor-pointer">
                {{ t('wishlist.compare') }}
              </button>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class WishlistCartComponent implements OnInit {
  protected readonly wishlist = inject(WishlistService);
  private tokenService = inject(TokenService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  /**
   * Read from the session rather than passed in: the cart sits in a header
   * that has no reason to know, and the host had to hard-code `true` to say it.
   */
  readonly isAuthenticated = computed(() => this.tokenService.isAuthenticated());

  isOpen = signal(false);

  /** The key for a count, by the plural rule of the language being read. */
  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }

  readonly ShoppingCart = ShoppingCart;
  readonly X = X;
  readonly GraduationCap = GraduationCap;

  /** The server holds the wishlist; the header loads it once for the whole app. */
  ngOnInit() {
    if (this.isAuthenticated()) {
      this.wishlist.load();
    }
  }

  handleCartClick() {
    if (!this.isAuthenticated()) {
      // Navigations stay in the language the visitor is reading.
      void this.router.navigate(['/', this.locale.active(), 'login']);
      return;
    }
    this.isOpen.update(v => !v);
  }
}
