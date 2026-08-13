import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RegistrationFlowService } from './registration-flow.service';

describe('RegistrationFlowService', () => {
  let flow: RegistrationFlowService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    flow = TestBed.inject(RegistrationFlowService);
  });

  it('starts with no pending address, so /verify can tell it arrived cold', () => {
    expect(flow.pendingEmail()).toBe('');
  });

  it('carries the address the register form just used', () => {
    flow.remember('awa@example.com');

    expect(flow.pendingEmail()).toBe('awa@example.com');
  });

  it('forgets the address once verification is done', () => {
    flow.remember('awa@example.com');

    flow.clear();

    expect(flow.pendingEmail()).toBe('');
  });
});
