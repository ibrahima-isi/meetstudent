import { Component, input, signal, computed, effect, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, MapPin, Heart, GraduationCap, Clock, Calendar, Users, ArrowUpDown, Book, Star, X } from 'lucide-angular';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';
import { StarRatingComponent } from '@shared/components/star-rating/star-rating.component';
import { PROGRAMMES as MOCK_PROGRAMMES } from '@data/programmes';
import { School, Program, Tag, Course } from '@models/entities';
import { ProgramService } from '@services/program.service';
import { CourseService } from '@services/course.service';
import { SchoolService } from '@services/school.service';
import { TokenService } from '@services/token.service';
import { LocaleService } from '@services/locale.service';

@Component({
  selector: 'app-school-detail-page',
  imports: [CommonModule, FormsModule, LucideAngularModule, ImageWithFallbackComponent, StarRatingComponent],
  templateUrl: './school-detail-page.component.html'
})
export class SchoolDetailPageComponent implements OnInit, OnDestroy {
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

  /**
   * Null until the API answers. The page used to receive a whole `School` from
   * its parent; it is reachable by URL now, so it fetches its own.
   */
  school = signal<School | null>(null);
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
  wishlist = signal<number[]>([]);
  showLoginPrompt = signal(false);
  sortBy = signal<'name' | 'places'>('name');

  // Course Modal State
  selectedProgram = signal<Program | null>(null);
  courses = signal<Course[]>([]);
  showCoursesModal = signal(false);
  isLoadingCourses = signal(false);

  private listener = () => this.updateWishlist();

  constructor() {
    effect(() => {
      this.updateWishlist();
    });

    // Re-runs when the id changes, so /schools/7 → /schools/8 reloads even
    // though the router reuses the component instance.
    effect(() => {
      const id = Number(this.id());
      if (!Number.isInteger(id)) {
        this.school.set(null);
        return;
      }

      this.schoolService.getSchool(id).subscribe({
        next: (school) => {
          this.school.set(school);
          this.loadPrograms();
        },
        error: () => this.school.set(null),
      });
    });
  }

  ngOnInit() {
    if (typeof window !== 'undefined') {
      window.addEventListener('wishlistUpdated', this.listener);
    }
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

    const schoolId = school.id;
    // Since ProgramController doesn't have schoolId filter directly,
    // we use the programs field in the School object if populated,
    // or fetch all programs and filter them (less efficient but works for now).

    if (school.programs && school.programs.length > 0) {
      this.programs.set(school.programs);
      return;
    }

    if (schoolId) {
      this.programService.getPrograms(0, 100).subscribe({
        next: (page) => {
          if (page && page.content && page.content.length > 0) {
            // Filter programs belonging to this school locally
            const schoolProgs = page.content.filter(p => p.school?.id === schoolId);
            if (schoolProgs.length > 0) {
              this.programs.set(schoolProgs);
            } else {
              this.programs.set(MOCK_PROGRAMMES[schoolId] || []);
            }
          } else {
            this.programs.set(MOCK_PROGRAMMES[schoolId] || []);
          }
        },
        error: () => {
          this.programs.set(MOCK_PROGRAMMES[schoolId] || []);
        }
      });
    }
  }

  ngOnDestroy() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('wishlistUpdated', this.listener);
    }
  }

  updateWishlist() {
    if (typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem('wishlist');
      this.wishlist.set(saved ? JSON.parse(saved) : []);
    }
  }

  sortedProgrammes = computed(() => {
    const progs = [...this.programs()];
    const sortType = this.sortBy();

    return progs.sort((a, b) => {
      if (sortType === 'name') {
        return (a.name || '').localeCompare(b.name || '', 'fr');
      } else if (sortType === 'places') {
        const aPlaces = (a.capacity || 0) - (a.enrolled || 0);
        const bPlaces = (b.capacity || 0) - (b.enrolled || 0);
        return bPlaces - aPlaces;
      }
      return 0;
    });
  });

  toggleWishlist(programmeId: number) {
    if (!this.isAuthenticated()) {
      this.showLoginPrompt.set(true);
      return;
    }

    const currentList = this.wishlist();
    const newWishlist = currentList.includes(programmeId)
      ? currentList.filter(id => id !== programmeId)
      : [...currentList, programmeId];
    
    this.wishlist.set(newWishlist);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('wishlist', JSON.stringify(newWishlist));
      window.dispatchEvent(new Event('wishlistUpdated'));
    }
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
