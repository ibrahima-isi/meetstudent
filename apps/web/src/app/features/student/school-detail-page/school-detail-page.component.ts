import { Component, input, signal, computed, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, MapPin, Heart, GraduationCap, Clock, Calendar, Users, ArrowUpDown, Book, Star, X } from 'lucide-angular';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';
import { StarRatingComponent } from '@shared/components/star-rating/star-rating.component';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { School, Program, Tag, Course } from '@models/entities';
import { ProgramService } from '@services/program.service';
import { CourseService } from '@services/course.service';
import { SchoolService } from '@services/school.service';
import { TokenService } from '@services/token.service';
import { LocaleService } from '@services/locale.service';
import { WishlistService } from '@services/wishlist.service';
import { TranslocoDirective } from '@jsverse/transloco';
import { pluralKey } from '@i18n/plural';

@Component({
  selector: 'app-school-detail-page',
  imports: [CommonModule, FormsModule, LucideAngularModule, ImageWithFallbackComponent, StarRatingComponent, TranslocoDirective, ErrorStateComponent],
  templateUrl: './school-detail-page.component.html'
})
export class SchoolDetailPageComponent {
  /**
   * Bound from `/:lang/schools/:id` by `withComponentInputBinding()`, so it is
   * the raw URL segment — a string, never a number.
   */
  id = input.required<string>();

  private programService = inject(ProgramService);
  private courseService = inject(CourseService);
  private readonly schoolService = inject(SchoolService);
  private readonly tokenService = inject(TokenService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  protected readonly wishlist = inject(WishlistService);

  /**
   * Null until the API answers. The page used to receive a whole `School` from
   * its parent; it is reachable by URL now, so it fetches its own.
   */
  school = signal<School | null>(null);
  /** The school request: 'error' shows the retry surface, never placeholder data. */
  schoolStatus = signal<'loading' | 'loaded' | 'error'>('loading');
  /** The programmes request, tracked apart so a failure does not hide the school. */
  programsStatus = signal<'loading' | 'loaded' | 'error'>('loading');
  /** Placeholder rows shown while the programmes load. */
  protected readonly skeletons = [0, 1];
  readonly isAuthenticated = computed(() => this.tokenService.isAuthenticated());

  readonly ArrowLeft = ArrowLeft;
  readonly MapPin = MapPin;
  readonly Heart = Heart;
  readonly GraduationCap = GraduationCap;
  readonly Clock = Clock;
  readonly Calendar = Calendar;
  readonly Users = Users;
  readonly ArrowUpDown = ArrowUpDown;
  readonly Book = Book;
  readonly Star = Star;
  readonly X = X;

  programs = signal<Program[]>([]);
  showLoginPrompt = signal(false);
  sortBy = signal<'name' | 'places'>('name');

  // Course Modal State
  selectedProgram = signal<Program | null>(null);
  courses = signal<Course[]>([]);
  showCoursesModal = signal(false);
  isLoadingCourses = signal(false);

  constructor() {
    // The wishlist lives on the server: fetch it so the heart reflects it after a reload.
    if (this.isAuthenticated()) {
      this.wishlist.load();
    }

    // Re-runs when the id changes, so /schools/7 → /schools/8 reloads even
    // though the router reuses the component instance.
    effect(() => {
      this.load(Number(this.id()));
    });
  }

  /** Fetches the school named by the URL; also the retry action of the error state. */
  protected retry(): void {
    this.load(Number(this.id()));
  }

  private load(id: number): void {
    if (!Number.isInteger(id)) {
      this.school.set(null);
      this.schoolStatus.set('error');
      return;
    }

    this.schoolStatus.set('loading');
    this.schoolService.getSchool(id).subscribe({
      next: (school) => {
        this.school.set(school);
        this.schoolStatus.set('loaded');
        this.loadPrograms();
      },
      error: () => {
        this.school.set(null);
        this.schoolStatus.set('error');
      },
    });
  }

  openCoursesModal(program: Program) {
    this.selectedProgram.set(program);
    this.showCoursesModal.set(true);
    this.isLoadingCourses.set(true);
    
    if (program.id) {
      this.courseService.getCoursesByProgram(program.id).subscribe({
        next: (courses) => {
          this.courses.set(courses);
          this.isLoadingCourses.set(false);
        },
        error: () => {
          this.courses.set([]);
          this.isLoadingCourses.set(false);
        }
      });
    }
  }

  closeCoursesModal() {
    this.showCoursesModal.set(false);
    this.selectedProgram.set(null);
    this.courses.set([]);
  }

  loadPrograms() {
    const school = this.school();
    if (!school) {
      return;
    }

    // ProgramController has no schoolId filter, so when the school did not
    // embed its programmes we fetch a page of them and keep the ones that
    // belong to it.
    if (school.programs && school.programs.length > 0) {
      this.programs.set(school.programs);
      this.programsStatus.set('loaded');
      return;
    }

    const schoolId = school.id;
    if (schoolId === undefined) {
      this.programs.set([]);
      this.programsStatus.set('loaded');
      return;
    }

    this.programsStatus.set('loading');
    this.programService.getPrograms(0, 100).subscribe({
      next: (page) => {
        this.programs.set((page?.content ?? []).filter((p) => p.school?.id === schoolId));
        this.programsStatus.set('loaded');
      },
      error: () => {
        this.programs.set([]);
        this.programsStatus.set('error');
      },
    });
  }

  sortedProgrammes = computed(() => {
    const progs = [...this.programs()];
    const sortType = this.sortBy();
    const locale = this.locale.active();

    return progs.sort((a, b) => {
      if (sortType === 'name') {
        return (a.name || '').localeCompare(b.name || '', locale);
      } else if (sortType === 'places') {
        const aPlaces = (a.capacity || 0) - (a.enrolled || 0);
        const bPlaces = (b.capacity || 0) - (b.enrolled || 0);
        return bPlaces - aPlaces;
      }
      return 0;
    });
  });

  /** The key for a count, by the plural rule of the language being read. */
  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }

  /** Seats left in a programme; never negative. */
  protected placesLeft(programme: Program): number {
    return Math.max((programme.capacity || 0) - (programme.enrolled || 0), 0);
  }

  toggleWishlist(school: School) {
    if (!this.isAuthenticated()) {
      this.showLoginPrompt.set(true);
      return;
    }
    this.wishlist.toggle(school);
  }

  handleLoginClick() {
    this.showLoginPrompt.set(false);
    this.goTo('login');
  }

  /**
   * The page is reachable from both the public landing and the student home,
   * so "back" follows the visitor's status rather than browser history — the
   * same rule the old view state machine applied.
   */
  goBack(): void {
    if (this.isAuthenticated()) {
      this.goTo('home');
      return;
    }
    this.goTo();
  }

  /** Navigations stay in the language the visitor is reading. */
  private goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }
}
