import { Component, input, output, signal, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, Star } from 'lucide-angular';
import { RatingService } from '@services/rating.service';
import { TokenService } from '@services/token.service';
import { User } from '@models/entities';

@Component({
  selector: 'app-star-rating',
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="flex items-center gap-1">
      @for (value of [1, 2, 3, 4, 5]; track value) {
        <button
          (click)="handleClick(value)"
          (mouseenter)="handleMouseEnter(value)"
          (mouseleave)="handleMouseLeave()"
          [disabled]="readonly()"
          class="transition-transform"
          [class]="readonly() ? 'cursor-default' : 'cursor-pointer hover:scale-110'"
        >
          <lucide-icon
            [img]="Star"
            class="w-5 h-5"
            [class]="value <= displayRating() ? 'fill-yellow-400 text-yellow-400' : 'fill-gray-200 text-gray-200'"
          ></lucide-icon>
        </button>
      }
      @if (rating() > 0) {
        <span class="ml-2 text-gray-600">{{ rating() }}/5</span>
      }
    </div>
  `
})
export class StarRatingComponent implements OnInit {
  itemId = input.required<number>();
  itemType = input.required<'school' | 'programme'>();
  initialRating = input<number | undefined>(0);
  readonly = input<boolean>(false);
  
  onRate = output<number>();

  private ratingService = inject(RatingService);
  private tokenService = inject(TokenService);

  rating = signal<number>(0);
  hoverRating = signal<number>(0);

  readonly Star = Star;

  ngOnInit() {
    if (typeof localStorage !== 'undefined') {
      const savedRatings = localStorage.getItem(`${this.itemType()}_ratings`);
      if (savedRatings) {
        const ratings = JSON.parse(savedRatings);
        this.rating.set(ratings[this.itemId()] || this.initialRating() || 0);
      } else {
        this.rating.set(this.initialRating() || 0);
      }
    } else {
      this.rating.set(this.initialRating() || 0);
    }
  }

  handleClick(value: number) {
    if (this.readonly()) return;
    
    this.rating.set(value);
    
    const user: User | null = this.tokenService.user();
    if (user && user.id) {
      if (this.itemType() === 'school') {
        this.ratingService.rateSchool(this.itemId(), user.id, value).subscribe();
      } else if (this.itemType() === 'programme') {
        this.ratingService.rateProgram(this.itemId(), user.id, value).subscribe();
      }
    }

    if (typeof localStorage !== 'undefined') {
      const savedRatings = localStorage.getItem(`${this.itemType()}_ratings`);
      const ratings = savedRatings ? JSON.parse(savedRatings) : {};
      ratings[this.itemId()] = value;
      localStorage.setItem(`${this.itemType()}_ratings`, JSON.stringify(ratings));
    }
    
    this.onRate.emit(value);
  }

  handleMouseEnter(value: number) {
    if (!this.readonly()) {
      this.hoverRating.set(value);
    }
  }

  handleMouseLeave() {
    if (!this.readonly()) {
      this.hoverRating.set(0);
    }
  }

  get displayRating() {
    return () => this.hoverRating() || this.rating();
  }
}
