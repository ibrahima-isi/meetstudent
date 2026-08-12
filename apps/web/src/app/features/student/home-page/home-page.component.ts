import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, Search, MapPin, Star, Filter, LogOut, User, ArrowUpDown } from 'lucide-angular';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';
import { StarRatingComponent } from '@shared/components/star-rating/star-rating.component';
import { WishlistCartComponent } from '@shared/components/wishlist-cart/wishlist-cart.component';
import { SCHOOLS as MOCK_SCHOOLS, CITIES, TYPES } from '@data/schools';
import { PROGRAMMES } from '@data/programmes';
import { School, Program } from '@models/entities';
import { SchoolService } from '@services/school.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';

@Component({
  selector: 'app-home-page',
  imports: [CommonModule, FormsModule, LucideAngularModule, ImageWithFallbackComponent, StarRatingComponent, WishlistCartComponent],
  templateUrl: './home-page.component.html'
})
export class HomePageComponent implements OnInit {
  private schoolService = inject(SchoolService);
  private readonly tokenService = inject(TokenService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  readonly Search = Search;
  readonly MapPin = MapPin;
  readonly Star = Star;
  readonly Filter = Filter;
  readonly LogOut = LogOut;
  readonly UserIcon = User;
  readonly ArrowUpDown = ArrowUpDown;

  schools = signal<School[]>(this.schoolService.schools());
  searchQuery = signal('');
  selectedCity = signal('Toutes les villes');
  selectedType = signal('Tous les types');
  showFilters = signal(false);
  sortBy = signal<'name' | 'city' | 'places'>('name');

  CITIES = CITIES;
  TYPES = TYPES;

  ngOnInit() {
    this.schoolService.getSchools(0, 50).subscribe({
      next: (page) => {
        if (page && page.content) {
          this.schools.set(page.content);
        }
      },
      error: (err) => {
        console.error('API Error:', err);
        if (this.schools().length === 0) {
          this.schools.set(MOCK_SCHOOLS);
        }
      }
    });
  }

  activeFiltersCount = computed(() => {
    let count = 0;
    if (this.selectedCity() !== 'Toutes les villes') count++;
    if (this.selectedType() !== 'Tous les types') count++;
    return count;
  });

  sortedSchools = computed(() => {
    const query = this.searchQuery().toLowerCase();
    const city = this.selectedCity();
    const type = this.selectedType();

    const filtered = this.schools().filter((school: School) => {
      const matchesSearch = (school.name || '').toLowerCase().includes(query) ||
                            (school.description || '').toLowerCase().includes(query);
      const matchesCity = city === 'Toutes les villes' || school.address.city === city;
      const matchesType = type === 'Tous les types' || school.type === type;
      return matchesSearch && matchesCity && matchesType;
    });

    const sortType = this.sortBy();
    return filtered.sort((a: School, b: School) => {
      if (sortType === 'name') {
        return (a.name || '').localeCompare(b.name || '', 'fr');
      } else if (sortType === 'city') {
        return (a.address.city || '').localeCompare(b.address.city || '', 'fr');
      } else if (sortType === 'places') {
        const aId = a.id || 0;
        const bId = b.id || 0;
        const aPrograms = PROGRAMMES[aId] || [];
        const bPrograms = PROGRAMMES[bId] || [];
        const aPlaces = aPrograms.reduce((sum: number, p: Program) => sum + ((p.capacity || 0) - (p.enrolled || 0)), 0);
        const bPlaces = bPrograms.reduce((sum: number, p: Program) => sum + ((p.capacity || 0) - (p.enrolled || 0)), 0);
        return bPlaces - aPlaces;
      }
      return 0;
    });
  });

  protected logout(): void {
    this.tokenService.clear();
    this.goTo();
  }

  /**
   * A school the API returned always carries an id; the type says otherwise
   * because the same interface is used for writes. Without one there is no
   * address to route to, so the click is dropped rather than sent to `/schools/`.
   */
  protected openSchool(school: School): void {
    if (school.id !== undefined) {
      this.goTo('schools', school.id);
    }
  }

  /** Navigations stay in the language the visitor is reading. */
  protected goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }
}
