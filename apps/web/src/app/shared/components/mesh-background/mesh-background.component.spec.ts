import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MeshBackgroundComponent } from './mesh-background.component';

describe('MeshBackgroundComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] }));

  it('is decorative: hidden from assistive technology and not clickable', () => {
    const fixture = TestBed.createComponent(MeshBackgroundComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.getAttribute('aria-hidden')).toBe('true');
    expect(getComputedStyle(host).pointerEvents).toBe('none');
  });

  it('draws three orbs', () => {
    const fixture = TestBed.createComponent(MeshBackgroundComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.orb').length).toBe(3);
  });
});
