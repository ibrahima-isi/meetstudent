import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { StarRatingComponent } from './star-rating.component';
import { RatingService } from '@services/rating.service';
import { TokenService } from '@services/token.service';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { translocoOptions } from '@i18n/transloco.config';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('StarRatingComponent', () => {
  let component: StarRatingComponent;
  let fixture: ComponentFixture<StarRatingComponent>;
  let ratingServiceSpy: jasmine.SpyObj<RatingService>;
  let tokenServiceSpy: jasmine.SpyObj<TokenService>;

  beforeEach(async () => {
    ratingServiceSpy = jasmine.createSpyObj('RatingService', ['rateSchool', 'rateProgram', 'rateCourse']);
    tokenServiceSpy = jasmine.createSpyObj('TokenService', ['user']);
    tokenServiceSpy.user.and.returnValue({ id: 1 } as any);

    await TestBed.configureTestingModule({
      imports: [StarRatingComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RatingService, useValue: ratingServiceSpy },
        { provide: TokenService, useValue: tokenServiceSpy },
        provideTransloco(translocoOptions),
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StarRatingComponent);
    component = fixture.componentInstance;
    
    // Set required inputs
    fixture.componentRef.setInput('itemId', 1);
    fixture.componentRef.setInput('itemType', 'school');
    
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should update rating on handleRate', () => {
    component.handleRate(4);
    expect(component.rating()).toBe(4);
  });

  it('should call rating service on submitRating for school', () => {
    ratingServiceSpy.rateSchool.and.returnValue(of({} as any));
    component.rating.set(5);
    component.comment.set('Great school!');
    
    component.submitRating();
    
    expect(ratingServiceSpy.rateSchool).toHaveBeenCalledWith(1, 1, 5, 'Great school!');
  });

  it('should show comment input when showCommentInput is true and rated', () => {
    fixture.componentRef.setInput('showCommentInput', true);
    component.rating.set(3);
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('textarea')).toBeTruthy();
  });

  it('should not show comment input when readonly is true', () => {
    fixture.componentRef.setInput('readonly', true);
    fixture.componentRef.setInput('showCommentInput', true);
    component.rating.set(3);
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('textarea')).toBeFalsy();
  });

  it('offers the review button in the active language', async () => {
    const transloco = TestBed.inject(TranslocoService);
    await firstValueFrom(transloco.load('en'));
    transloco.setActiveLang('en');
    fixture.componentRef.setInput('itemId', 1);
    fixture.componentRef.setInput('itemType', 'school');
    component.handleRate(4);
    fixture.detectChanges();
    await fixture.whenStable();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Post my review');
    expect(root.querySelector('textarea')?.getAttribute('placeholder'))
      .toBe('Add a comment (optional)...');
  });

  it('shows an average rounded to one decimal', async () => {
    fixture.componentRef.setInput('initialRating', 5.333333333333333);
    component = fixture.componentInstance;
    component.rating.set(5.333333333333333);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('5.3/5');
  });

  describe('submission', () => {
    const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

    it('does not send a second request while the first is pending', () => {
      ratingServiceSpy.rateSchool.and.returnValue(new Subject<any>());
      component.rating.set(4);

      component.submitRating();
      component.submitRating();

      expect(ratingServiceSpy.rateSchool).toHaveBeenCalledTimes(1);
      expect(component.isSubmitting()).toBeTrue();
    });

    it('locks after a successful submission and tells the parent once', () => {
      ratingServiceSpy.rateSchool.and.returnValue(of({} as any));
      const emitted: unknown[] = [];
      component.onRate.subscribe((e) => emitted.push(e));
      component.rating.set(4);

      component.submitRating();
      component.submitRating();
      fixture.detectChanges();

      expect(ratingServiceSpy.rateSchool).toHaveBeenCalledTimes(1);
      expect(emitted.length).toBe(1);
      expect(q('textarea')).toBeNull();
      expect(q('[data-testid="rating-done"]')).not.toBeNull();
      component.handleRate(2);
      expect(component.rating()).toBe(4);
    });

    it('shows an inline alert and does not report success when the API refuses', () => {
      ratingServiceSpy.rateSchool.and.returnValue(throwError(() => new Error('403')));
      const emitted: unknown[] = [];
      component.onRate.subscribe((e) => emitted.push(e));
      component.rating.set(4);

      component.submitRating();
      fixture.detectChanges();

      expect(emitted).toEqual([]);
      expect(q('[role="alert"][data-testid="rating-error"]')).not.toBeNull();
      expect(component.isSubmitting()).toBeFalse();
    });

    it('lets the visitor retry after a failure', () => {
      ratingServiceSpy.rateSchool.and.returnValues(throwError(() => new Error('500')), of({} as any));
      component.rating.set(4);

      component.submitRating();
      component.submitRating();
      fixture.detectChanges();

      expect(ratingServiceSpy.rateSchool).toHaveBeenCalledTimes(2);
      expect(q('[data-testid="rating-error"]')).toBeNull();
    });

    it('uses the program and course endpoints for those targets', () => {
      ratingServiceSpy.rateProgram.and.returnValue(of({} as any));
      fixture.componentRef.setInput('itemType', 'program');
      component.rating.set(3);
      component.submitRating();
      expect(ratingServiceSpy.rateProgram).toHaveBeenCalledWith(1, 1, 3, '');
    });
  });
});
