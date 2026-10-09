import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Alert } from './alert';

describe('Alert', () => {
  const render = (message: string) => {
    TestBed.configureTestingModule({
      imports: [Alert],
      providers: [provideZonelessChangeDetection()],
    });
    const fixture = TestBed.createComponent(Alert);
    fixture.componentRef.setInput('message', message);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('renders the message with the alert role', () => {
    const el = render('Oups');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Oups');
  });

  it('renders nothing for an empty message', () => {
    expect(render('').querySelector('[role="alert"]')).toBeNull();
  });
});
