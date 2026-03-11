import { Component, input, output, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Star, MessageSquare } from 'lucide-angular';
import { RatingService } from '@services/rating.service';
import { TokenService } from '@services/token.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-star-rating',
  imports: [CommonModule, FormsModule, LucideAngularModule],
  template: `
    <div class="flex flex-col gap-3">
      <div class="flex items-center gap-1">
        @for (value of [1, 2, 3, 4, 5]; track value) {
          <button
            type="button"
            (click)="handleRate(value)"
            (mouseenter)="handleMouseEnter(value)"
            (mouseleave)="handleMouseLeave()"
            [disabled]="readonly()"
            class="transition-transform outline-none"
            [class]="readonly() ? 'cursor-default' : 'cursor-pointer hover:scale-110 focus:scale-110'"
          >
            <lucide-icon
              [img]="Star"
              class="w-6 h-6"
              [class]="value <= displayRating() ? 'fill-yellow-400 text-yellow-400' : 'fill-gray-200 text-gray-200'"
            ></lucide-icon>
          </button>
        }
        @if (rating() > 0 && showValue()) {
          <span class="ml-2 text-sm font-medium text-gray-600">{{ rating() }}/5</span>
        }
      </div>

      @if (!readonly() && showCommentInput() && rating() > 0) {
        <div class="flex flex-col gap-2 animate-in fade-in slide-in-from-top-1">
          <textarea
            [(ngModel)]="comment"
            placeholder="Ajouter un commentaire (optionnel)..."
            class="w-full p-3 text-sm border border-gray-200 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none resize-none"
            rows="3"
          ></textarea>
          <button
            (click)="submitRating()"
            [disabled]="isSubmitting()"
            class="self-end px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:bg-gray-400 transition-colors"
          >
            {{ isSubmitting() ? 'Envoi...' : 'Publier mon avis' }}
          </button>
        </div>
      }
    </div>
  `
})
export class StarRatingComponent {
  itemId = input.required<number>();
  itemType = input.required<'school' | 'program' | 'course'>();
  initialRating = input<number>(0);
  readonly = input<boolean>(false);
  showValue = input<boolean>(true);
  showCommentInput = input<boolean>(true);
  
  onRate = output<{note: number, comment?: string}>();

  private ratingService = inject(RatingService);
  private tokenService = inject(TokenService);

  rating = signal<number>(0);
  hoverRating = signal<number>(0);
  comment = signal<string>('');
  isSubmitting = signal(false);

  readonly Star = Star;
  readonly MessageSquare = MessageSquare;

  constructor() {
    // Initialize rating from input
    const ratingSub = signal(0);
    this.rating.set(this.initialRating() || 0);
  }

  handleRate(value: number) {
    if (this.readonly()) return;
    this.rating.set(value);
    if (!this.showCommentInput()) {
      this.submitRating();
    }
  }

  submitRating() {
    const note = this.rating();
    if (note === 0) return;

    const user = this.tokenService.user();
    if (!user || !user.id) {
      this.onRate.emit({ note, comment: this.comment() });
      return;
    }

    this.isSubmitting.set(true);
    const userId = user.id;
    const commentText = this.comment();

    const obs: Observable<any> = this.itemType() === 'school' 
      ? this.ratingService.rateSchool(this.itemId(), userId, note, commentText)
      : this.itemType() === 'program'
      ? this.ratingService.rateProgram(this.itemId(), userId, note, commentText)
      : this.ratingService.rateCourse(this.itemId(), userId, note, commentText);

    obs.subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.onRate.emit({ note, comment: commentText });
      },
      error: () => {
        this.isSubmitting.set(false);
        // Fallback or error handling
        this.onRate.emit({ note, comment: commentText });
      }
    });
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

  displayRating() {
    return this.hoverRating() || this.rating();
  }
}
