import { Component, input, output, signal, effect, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, ShoppingCart, X, GraduationCap } from 'lucide-angular';
import { PROGRAMMES } from '@data/programmes';
import { Program } from '@models/entities';
import { UserService } from '@services/user.service';
import { TokenService } from '@services/token.service';

@Component({
  selector: 'app-wishlist-cart',
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="relative">
      <button
        (click)="handleCartClick()"
        class="relative flex items-center gap-2 px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors cursor-pointer"
      >
        <lucide-icon [img]="ShoppingCart" class="w-5 h-5"></lucide-icon>
        <span>Souhaits</span>
        @if (isAuthenticated() && wishlist().length > 0) {
          <span class="absolute -top-1 -right-1 w-5 h-5 bg-indigo-600 text-white rounded-full flex items-center justify-center text-xs">
            {{ wishlist().length }}
          </span>
        }
      </button>

      @if (isOpen() && isAuthenticated()) {
        <div class="fixed inset-0 z-40" (click)="isOpen.set(false)"></div>
        
        <div class="absolute left-0 top-full mt-2 w-96 bg-white rounded-xl shadow-xl border border-gray-200 z-50 max-h-[80vh] overflow-hidden flex flex-col">
          <div class="p-4 border-b border-gray-200">
            <div class="flex items-center justify-between">
              <h3 class="text-gray-900 font-bold">Mes formations souhaitées</h3>
              <button
                (click)="isOpen.set(false)"
                class="p-1 hover:bg-gray-100 rounded-lg transition-colors cursor-pointer"
              >
                <lucide-icon [img]="X" class="w-5 h-5"></lucide-icon>
              </button>
            </div>
            <p class="text-gray-600 mt-1 text-sm">
              {{ wishlist().length }} formation{{ wishlist().length !== 1 ? 's' : '' }}
            </p>
          </div>

          <div class="flex-1 overflow-y-auto">
            @if (wishlistProgrammes().length === 0) {
              <div class="p-8 text-center">
                <lucide-icon [img]="GraduationCap" class="w-12 h-12 text-gray-300 mx-auto mb-3"></lucide-icon>
                <p class="text-gray-600">Aucune formation dans vos souhaits</p>
                <p class="text-gray-500 mt-1 text-sm">Ajoutez des formations pour les retrouver facilement</p>
              </div>
            } @else {
              <div class="p-4 space-y-3">
                @for (programme of wishlistProgrammes(); track programme.id) {
                  <div class="flex items-start gap-3 p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                    <lucide-icon [img]="GraduationCap" class="w-5 h-5 text-indigo-600 flex-shrink-0 mt-1"></lucide-icon>
                    <div class="flex-1 min-w-0">
                      <h4 class="text-gray-900 line-clamp-2 mb-1 text-sm font-medium">{{ programme.name }}</h4>
                      <p class="text-gray-600 text-xs font-semibold">{{ programme.level }} • {{ programme.duration }}</p>
                    </div>
                    <button
                      (click)="programme.id !== undefined && removeFromWishlist(programme.id)"
                      class="p-1 hover:bg-white rounded-lg transition-colors flex-shrink-0 cursor-pointer"
                    >
                      <lucide-icon [img]="X" class="w-4 h-4 text-gray-500 hover:text-red-600"></lucide-icon>
                    </button>
                  </div>
                }
              </div>
            }
          </div>

          @if (wishlistProgrammes().length > 0) {
            <div class="p-4 border-t border-gray-200 bg-gray-50">
              <button class="w-full px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors font-medium text-sm cursor-pointer">
                Comparer les formations
              </button>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class WishlistCartComponent implements OnInit, OnDestroy {
  isAuthenticated = input.required<boolean>();
  onLoginPrompt = output<void>();

  private userService = inject(UserService);
  private tokenService = inject(TokenService);

  isOpen = signal(false);
  wishlist = signal<number[]>([]);

  readonly ShoppingCart = ShoppingCart;
  readonly X = X;
  readonly GraduationCap = GraduationCap;

  private listener = () => this.updateWishlist();

  constructor() {
    effect(() => {
      this.updateWishlist();
    });
  }

  ngOnInit() {
    if (typeof window !== 'undefined') {
      window.addEventListener('wishlistUpdated', this.listener);
    }
  }

  ngOnDestroy() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('wishlistUpdated', this.listener);
    }
  }

  updateWishlist() {
    if (this.isAuthenticated() && typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem('wishlist');
      this.wishlist.set(saved ? JSON.parse(saved) : []);
    } else {
      this.wishlist.set([]);
    }
  }

  handleCartClick() {
    if (!this.isAuthenticated()) {
      this.onLoginPrompt.emit();
      return;
    }
    this.isOpen.update(v => !v);
  }

  removeFromWishlist(programmeId: number) {
    const user = this.tokenService.user();
    if (user && user.id) {
      const allProgrammes = Object.values(PROGRAMMES).flat();
      const prog = allProgrammes.find(p => p.id === programmeId);
      // Backend expects schoolId for wishlist
      if (prog && prog.school && prog.school.id) {
        this.userService.removeFromWishlist(user.id, prog.school.id).subscribe();
      }
    }

    const newWishlist = this.wishlist().filter(id => id !== programmeId);
    this.wishlist.set(newWishlist);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('wishlist', JSON.stringify(newWishlist));
      window.dispatchEvent(new Event('wishlistUpdated'));
    }
  }

  get wishlistProgrammes(): () => Program[] {
    return () => {
      const allProgrammes = Object.values(PROGRAMMES).flat();
      return allProgrammes.filter(p => p.id !== undefined && this.wishlist().includes(p.id));
    };
  }
}
