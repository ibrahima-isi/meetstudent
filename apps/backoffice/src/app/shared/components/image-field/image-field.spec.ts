import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ImageField } from './image-field';

describe('ImageField', () => {
  let fixture: ComponentFixture<ImageField>;
  let emitted: (File | null)[];

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const pick = (f: File) => {
    const dt = new DataTransfer();
    dt.items.add(f);
    const input = q<HTMLInputElement>('input[type=file]')!;
    input.files = dt.files;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const png = (name = 'a.png') => new File([new Uint8Array(20)], name, { type: 'image/png' });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    fixture = TestBed.createComponent(ImageField);
    emitted = [];
    fixture.componentInstance.fileChange.subscribe((f) => emitted.push(f));
    fixture.componentRef.setInput('fieldId', 'photo');
    fixture.componentRef.setInput('label', 'Photo');
    fixture.detectChanges();
  });

  it('labels the file input and states the limits', () => {
    expect(q('label[for="photo"]')!.textContent).toContain('Photo');
    expect(q('#photo')!.getAttribute('type')).toBe('file');
    expect(el().textContent).toContain('10 Mo');
    expect(q('img[data-preview]')).toBeNull();
  });

  it('shows the current image', () => {
    fixture.componentRef.setInput('current', 'http://srv.test/p.png');
    fixture.detectChanges();
    expect(q<HTMLImageElement>('img[data-preview]')!.getAttribute('src')).toBe('http://srv.test/p.png');
  });

  it('says an image exists when it has none to display', () => {
    fixture.componentRef.setInput('existing', true);
    fixture.detectChanges();
    expect(el().textContent).toContain('déjà enregistrée');
  });

  it('emits a valid file and previews it locally', () => {
    const file = png();
    pick(file);
    expect(emitted).toEqual([file]);
    expect(q<HTMLImageElement>('img[data-preview]')!.getAttribute('src')).toMatch(/^blob:/);
    expect(q('[data-error]')).toBeNull();
  });

  it('rejects a bad file with a message and emits null', () => {
    pick(new File(['x'], 'doc.pdf', { type: 'application/pdf' }));
    expect(emitted).toEqual([null]);
    expect(q('[data-error]')!.textContent).toContain('JPEG, PNG ou WebP');
    expect(q('img[data-preview]')).toBeNull();
  });
});
