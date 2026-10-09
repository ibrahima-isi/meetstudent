import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, Search, MapPin, Star, Filter, LogIn, UserPlus, ArrowUpDown } from 'lucide-angular';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';
import { LanguageSwitcherComponent } from '@shared/components/language-switcher/language-switcher.component';
import { ThemeToggleComponent } from '@shared/components/theme-toggle/theme-toggle.component';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { School } from '@models/entities';
import { SchoolService } from '@services/school.service';
import { LocaleService } from '@services/locale.service';
import { TranslocoDirective } from '@jsverse/transloco';
import { pluralKey } from '@i18n/plural';

@Component({
  selector: 'app-landing-page',
  imports: [
    CommonModule,
    FormsModule,
    LucideAngularModule,
    ImageWithFallbackComponent,
    LanguageSwitcherComponent,
    ThemeToggleComponent,
    TranslocoDirective,
    ErrorStateComponent,
  ],
  templateUrl: './landing-page.component.html'
})
export class LandingPageComponent implements OnInit {
  private schoolService = inject(SchoolService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  readonly Search = Search;
  readonly MapPin = MapPin;
  readonly Star = Star;
  readonly Filter = Filter;
  readonly LogIn = LogIn;
  readonly UserPlus = UserPlus;
  readonly ArrowUpDown = ArrowUpDown;

  schools = signal<School[]>([]);
  searchQuery = signal('');
  /** '' means no filter; the template labels that choice in the active language. */
  selectedCity = signal('');
  selectedType = signal('');
  showFilters = signal(false);
  sortBy = signal<'name' | 'city' | 'places'>('name');

  /** 'loading' until the first answer; 'error' shows the retry surface, never placeholder data. */
  status = signal<'loading' | 'loaded' | 'error'>('loading');
  /** Placeholder cards shown while loading. */
  protected readonly skeletons = [0, 1, 2];

  ngOnInit() {
    this.load();
  }

  /** Fetches the first page of schools; also the retry action of the error state. */
  protected load(): void {
    this.status.set('loading');
    this.schoolService.getSchools(0, 50).subscribe({
      next: (page) => {
        this.schools.set(page?.content ?? []);
        this.status.set('loaded');
      },
      error: () => {
        this.schools.set([]);
        this.status.set('error');
      },
    });
  }

  /** Filter choices come from the schools actually loaded, not a bundled list. */
  cities = computed(() => this.distinct((school) => school.address?.city));
  types = computed(() => this.distinct((school) => school.type));

  private distinct(pick: (school: School) => string | undefined): string[] {
    const values = this.schools().map(pick).filter((v): v is string => !!v);
    return [...new Set(values)].sort((a, b) => a.localeCompare(b, this.locale.active()));
  }

  activeFiltersCount = computed(() => {
    let count = 0;
    if (this.selectedCity() !== '') count++;
    if (this.selectedType() !== '') count++;
    return count;
  });

  sortedSchools = computed(() => {
    const query = this.searchQuery().toLowerCase();
    const city = this.selectedCity();
    const type = this.selectedType();

    const filtered = this.schools().filter((school: School) => {
      const matchesSearch = (school.name || '').toLowerCase().includes(query) ||
                            (school.description || '').toLowerCase().includes(query);
      const matchesCity = city === '' || school.address.city === city;
      const matchesType = type === '' || school.type === type;
      return matchesSearch && matchesCity && matchesType;
    });

    const sortType = this.sortBy();
    const locale = this.locale.active();
    return filtered.sort((a: School, b: School) => {
      if (sortType === 'name') {
        return (a.name || '').localeCompare(b.name || '', locale);
      } else if (sortType === 'city') {
        return (a.address.city || '').localeCompare(b.address.city || '', locale);
      } else if (sortType === 'places') {
        return this.placesLeft(b) - this.placesLeft(a);
      }
      return 0;
    });
  });

  /** Seats left across the programmes the API attached to a school. */
  private placesLeft(school: School): number {
    return (school.programs ?? []).reduce(
      (sum, p) => sum + Math.max((p.capacity || 0) - (p.enrolled || 0), 0),
      0,
    );
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

  /** The key for a count, by the plural rule of the language being read. */
  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }

  /** Navigations stay in the language the visitor is reading. */
  protected goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }
}
