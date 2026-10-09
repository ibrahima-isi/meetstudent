import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ModerationPage } from './moderation-page';
import { API_URL } from '@services/api-config';
import { Media } from '@models/entities';

const api = 'http://api.test/api/v1';

const media = (id: number, extra: Partial<Media> = {}): Media => ({
  id,
  category: 'DIPLOMA',
  visibility: 'PRIVATE',
  verificationStatus: 'PENDING',
  rejectionReason: null,
  originalFilename: `doc-${id}.pdf`,
  contentType: 'application/pdf',
  sizeBytes: 2048,
  publicUrl: null,
  ...extra,
});

const pageOf = (content: Media[], extra: object = {}) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
  ...extra,
});

describe('ModerationPage', () => {
  let fixture: ComponentFixture<ModerationPage>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  const flushList = (items: Media[], extra: object = {}) => {
    backend.expectOne((r) => r.url === `${api}/media`).flush(pageOf(items, extra));
  };
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const click = async (selector: string, index = 0) => {
    (el.querySelectorAll(selector)[index] as HTMLElement).click();
    await settle();
  };
  const rows = () => el.querySelectorAll('[data-row]');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ModerationPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('shows a loading state then loads PENDING by default', async () => {
    expect(el.querySelector('[data-loading]')).not.toBeNull();
    const req = backend.expectOne((r) => r.url === `${api}/media`);
    expect(req.request.params.get('status')).toBe('PENDING');
    req.flush(pageOf([media(1), media(2)]));
    await settle();
    expect(el.querySelector('[data-loading]')).toBeNull();
    expect(rows().length).toBe(2);
    expect(rows()[0].textContent).toContain('Diplôme');
    expect(rows()[0].textContent).toContain('doc-1.pdf');
    expect(rows()[0].textContent).toContain('En attente');
    expect(rows()[0].textContent).toContain('2 Ko');
  });

  it('shows an empty state', async () => {
    flushList([]);
    await settle();
    expect(el.querySelector('[data-empty]')).not.toBeNull();
  });

  it('shows an error with retry that reloads', async () => {
    backend.expectOne((r) => r.url === `${api}/media`).flush('x', { status: 500, statusText: 'err' });
    await settle();
    expect(el.querySelector('[role="alert"]')).not.toBeNull();
    await click('[data-retry]');
    flushList([media(1)]);
    await settle();
    expect(rows().length).toBe(1);
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  it('reloads from page 0 when another status tab is chosen', async () => {
    flushList([media(1)]);
    await settle();
    await click('[data-tab="REJECTED"]');
    const req = backend.expectOne((r) => r.url === `${api}/media`);
    expect(req.request.params.get('status')).toBe('REJECTED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(pageOf([media(9, { verificationStatus: 'REJECTED', rejectionReason: 'Flou' })]));
    await settle();
    expect(rows()[0].textContent).toContain('Rejeté');
    expect(rows()[0].textContent).toContain('Flou');
    expect(el.querySelector('[data-approve]')).toBeNull();
  });

  it('paginates', async () => {
    flushList([media(1)], { totalPages: 3, totalElements: 41 });
    await settle();
    await click('[data-next]');
    const req = backend.expectOne((r) => r.url === `${api}/media`);
    expect(req.request.params.get('page')).toBe('1');
    req.flush(pageOf([media(2)], { totalPages: 3, number: 1 }));
    await settle();
    expect(rows().length).toBe(1);
  });

  describe('viewing', () => {
    it('fetches the blob with HttpClient, opens an object URL and revokes it later', async () => {
      jasmine.clock().install();
      try {
        const create = spyOn(URL, 'createObjectURL').and.returnValue('blob:abc');
        const revoke = spyOn(URL, 'revokeObjectURL');
        const open = spyOn(window, 'open').and.returnValue({} as Window);
        flushList([media(1)]);
        await settle();
        await click('[data-view]');
        const req = backend.expectOne(`${api}/media/1`);
        expect(req.request.responseType).toBe('blob');
        req.flush(new Blob(['x'], { type: 'application/pdf' }));
        await settle();
        expect(create).toHaveBeenCalled();
        expect(open).toHaveBeenCalledWith('blob:abc', '_blank');
        expect(revoke).not.toHaveBeenCalled();
        jasmine.clock().tick(60_000);
        expect(revoke).toHaveBeenCalledWith('blob:abc');
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('revokes at once and alerts when the popup is blocked', async () => {
      spyOn(URL, 'createObjectURL').and.returnValue('blob:abc');
      const revoke = spyOn(URL, 'revokeObjectURL');
      spyOn(window, 'open').and.returnValue(null);
      flushList([media(1)]);
      await settle();
      await click('[data-view]');
      backend.expectOne(`${api}/media/1`).flush(new Blob(['x']));
      await settle();
      expect(revoke).toHaveBeenCalledWith('blob:abc');
      expect(el.querySelector('[role="alert"]')).not.toBeNull();
    });

    it('alerts when the download fails', async () => {
      flushList([media(1)]);
      await settle();
      await click('[data-view]');
      backend.expectOne(`${api}/media/1`).flush(new Blob(['x']), { status: 404, statusText: 'nf' });
      await settle();
      expect(el.querySelector('[role="alert"]')).not.toBeNull();
    });
  });

  describe('approving', () => {
    beforeEach(async () => {
      flushList([media(1), media(2)]);
      await settle();
    });

    it('asks for confirmation, removes the row at once and sends VERIFIED', async () => {
      await click('[data-approve]');
      expect(rows().length).toBe(2);
      backend.expectNone(`${api}/media/1/verification`);
      await click('[data-confirm]');
      expect(rows().length).toBe(1);
      const req = backend.expectOne(`${api}/media/1/verification`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ status: 'VERIFIED' });
      req.flush(media(1, { verificationStatus: 'VERIFIED' }));
      await settle();
      expect(rows().length).toBe(1);
    });

    it('does nothing when the confirmation is cancelled', async () => {
      await click('[data-approve]');
      await click('[data-cancel]');
      expect(el.querySelector('[data-confirm]')).toBeNull();
      expect(rows().length).toBe(2);
    });

    it('restores the row at its place and alerts on failure', async () => {
      await click('[data-approve]', 0);
      await click('[data-confirm]');
      backend.expectOne(`${api}/media/1/verification`).flush('x', { status: 500, statusText: 'err' });
      await settle();
      expect(rows().length).toBe(2);
      expect(rows()[0].textContent).toContain('doc-1.pdf');
      expect(el.querySelector('[role="alert"]')).not.toBeNull();
    });
  });

  describe('rejecting', () => {
    beforeEach(async () => {
      flushList([media(1), media(2)]);
      await settle();
      await click('[data-reject]');
    });

    const type = async (text: string) => {
      const input = el.querySelector('[data-reason]') as HTMLTextAreaElement;
      input.value = text;
      input.dispatchEvent(new Event('input'));
      await settle();
    };

    it('requires a reason and sends nothing without one', async () => {
      await click('[data-confirm]');
      expect(el.querySelector('[data-reason-error]')).not.toBeNull();
      backend.expectNone(`${api}/media/1/verification`);
      expect(rows().length).toBe(2);
      await type('   ');
      await click('[data-confirm]');
      backend.expectNone(`${api}/media/1/verification`);
    });

    it('sends REJECTED with the trimmed reason and removes the row', async () => {
      await type('  Document illisible ');
      await click('[data-confirm]');
      expect(rows().length).toBe(1);
      const req = backend.expectOne(`${api}/media/1/verification`);
      expect(req.request.body).toEqual({ status: 'REJECTED', reason: 'Document illisible' });
      req.flush(media(1, { verificationStatus: 'REJECTED' }));
    });

    it('rolls back and alerts on failure', async () => {
      await type('Flou');
      await click('[data-confirm]');
      backend.expectOne(`${api}/media/1/verification`).flush('x', { status: 500, statusText: 'err' });
      await settle();
      expect(rows().length).toBe(2);
      expect(el.querySelector('[role="alert"]')).not.toBeNull();
    });
  });
});
