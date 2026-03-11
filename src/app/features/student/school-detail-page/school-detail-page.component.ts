import { Component, input, output, signal, computed, effect, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, ArrowLeft, MapPin, Heart, GraduationCap, Clock, Calendar, Users, ArrowUpDown } from 'lucide-angular';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';
import { StarRatingComponent } from '@shared/components/star-rating/star-rating.component';
import { PROGRAMMES as MOCK_PROGRAMMES } from '@data/programmes';
import { School, Program, Tag } from '@models/entities';
import { ProgramService } from '@services/program.service';

@Component({
  selector: 'app-school-detail-page',
  imports: [CommonModule, FormsModule, LucideAngularModule, ImageWithFallbackComponent, StarRatingComponent],
  templateUrl: './school-detail-page.component.html'
})
export class SchoolDetailPageComponent implements OnInit, OnDestroy {
  school = input.required<School>();
  isAuthenticated = input<boolean>(false);
  
  onBack = output<void>();
  onLoginPrompt = output<void>();

  private programService = inject(ProgramService);

  readonly ArrowLeft = ArrowLeft;
  readonly MapPin = MapPin;
  readonly Heart = Heart;
  readonly GraduationCap = GraduationCap;
  readonly Clock = Clock;
  readonly Calendar = Calendar;
  readonly Users = Users;
  readonly ArrowUpDown = ArrowUpDown;

  programs = signal<Program[]>([]);
  wishlist = signal<number[]>([]);
  showLoginPrompt = signal(false);
  sortBy = signal<'name' | 'places'>('name');

  private listener = () => this.updateWishlist();

  constructor() {
    effect(() => {
      this.updateWishlist();
    });
  }

  ngOnInit() {
    this.loadPrograms();
    if (typeof window !== 'undefined') {
      window.addEventListener('wishlistUpdated', this.listener);
    }
  }

  loadPrograms() {
    const schoolId = this.school().id;
    // Since ProgramController doesn't have schoolId filter directly, 
    // we use the programs field in the School object if populated, 
    // or fetch all programs and filter them (less efficient but works for now).
    
    if (this.school().programs && this.school().programs!.length > 0) {
      this.programs.set(this.school().programs!);
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
    this.onLoginPrompt.emit();
  }
}
