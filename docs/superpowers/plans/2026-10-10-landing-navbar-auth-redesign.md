# Landing, Dock navbar and auth redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Execution method chosen by the owner: subagents, four per task (coder, reviewer, security reviewer, regression checker) — see "Execution protocol".**

**Goal:** Give MeetStudent a refined marketing landing page, a floating auto-hiding "Dock" navbar and split-screen auth pages, in three successive PRs.

**Architecture:** A semantic brand layer (tokens, button classes, motion utilities) in `styles.css`; small single-purpose Angular components (navbar, shell, sections); CSS-3D/SVG visuals only (no WebGL). The current catalogue moves unchanged to `/:lang/schools`; the landing takes `/:lang`. Everything stays SSR-safe, zoneless and signal-based.

**Tech Stack:** Angular 20 (standalone, signals, OnPush, zoneless, SSR), Tailwind v4, Transloco, lucide-angular, Karma + Jasmine, `@fontsource-variable/inter` (only new dependency).

**Spec:** `docs/superpowers/specs/2026-10-10-landing-navbar-auth-redesign-design.md`

**Approval note:** the owner's approval of this plan authorises editing every file listed under a task's **Files** (CLAUDE.md asks permission before touching more than 3 files; this is that permission). Any file not listed needs a new approval.

## Global Constraints

- Work only in `apps/web`; no API/backend change; never touch `apps/web/meetstudent/` (legacy React prototype) or `apps/backoffice`.
- Angular style (`apps/web/.claude/CLAUDE.md`): standalone (no `standalone: true`), signals + `computed()`, `inject()`, `ChangeDetectionStrategy.OnPush`, `input()`/`output()`, native control flow (`@if/@for/@switch`), reactive forms, `class`/`style` bindings (no `ngClass`/`ngStyle`), **no `@HostListener`/`@HostBinding` — use the `host` object**, no `mutate` on signals, no `any`.
- The app is zoneless with hydration + event replay: state flows through signals; anything that differs between server and first client render causes hydration mismatch.
- Colours: use semantic tokens (`bg-card`, `text-foreground`, `bg-brand`…) or pair every raw palette class with a `dark:` class — `shared/dark-mode.guard.spec.ts` fails otherwise. Every new component must also be added to that spec's `cases`.
- i18n: Transloco, **keys in English**, French is the source language, `fr.json` and `en.json` edited together in the same commit.
- Public pages stay server-rendered; guarded screens (`home`, `profile`, `schools/:id`) stay `RenderMode.Client` (`app.routes.server.spec.ts` enforces it).
- No mock data: a failing API shows the shared `error-state` or hides the section, never fake schools/figures. Testimonials are labelled examples. Decorative visuals show no school names, ratings or numbers.
- Motion: every animation is disabled under `prefers-reduced-motion: reduce`.
- Branching: each PR starts from an up-to-date `dev` on a new branch, ends with commit → push → PR into `dev`, merged with **squash**; never commit on `dev`/`main`; delete local branches after merge; leave remote deletion to the owner. Docker stacks started for checks are torn down with `docker compose down`.
- Commit messages: Conventional Commits (`feat(web): …`, `test(web): …`, `docs: …`).
- Test commands run from `apps/web`: single spec `npm test -- --no-watch --browsers=ChromeHeadless --include='**/<name>.spec.ts'`; full suite `npm test -- --no-watch --browsers=ChromeHeadless`; SSR build `npm run build`.

## Review Focus

Failure modes the spec implies but the happy path never exercises; each is pinned by a test in the task named.

1. **Hydration mismatch of the navbar for a signed-in visitor** — the server renders anonymous markup; the client must render the same first, then switch to "My space" (Task 4).
2. **Hero search input** — blank, whitespace-only, `a&b=c#d`, 500-character and non-Latin input must navigate to a valid `/schools` URL with the term preserved and encoded, never throw or build a malformed URL (Task 9).
3. **API down or empty** — key figures hide, featured schools show the retry state, the hero and the rest of the page still render (Tasks 10, 12).
4. **Auto-hide must never trap a user** — bar stays visible while keyboard focus is inside it, while the mobile menu is open, near the page top, and under reduced motion/touch (Tasks 3, 4).
5. **Deep links after the catalogue moved** — `/fr/schools?q=dakar`, `/en/schools`, `/fr#reviews` must work signed-out, and the language switch must keep query and fragment (Tasks 6, 8).

## File Structure

New or changed, by responsibility (all under `apps/web/src/app` unless noted):

| Path | Responsibility |
|---|---|
| `../styles.css` | Brand tokens, Inter, `.btn*`, `.field-input`, motion utilities |
| `shared/directives/reveal.directive.ts` | Fade-in on first scroll into view |
| `shared/components/mesh-background/mesh-background.component.ts` | Animated gradient/orb backdrop (pure CSS) |
| `shared/components/dock-navbar/dock-visibility.ts` | Pure auto-hide rule |
| `shared/components/dock-navbar/dock-navbar.component.ts` | The floating pill, desktop + mobile menu |
| `shared/components/language-switcher/…`, `theme-toggle/…` | `compact` mode; token styling |
| `shared/layouts/public-shell/public-shell.component.ts` | Navbar + skip link + outlet for public pages |
| `shared/layouts/auth-layout/auth-layout.component.ts`, `auth-brand-panel.component.ts` | Split-screen auth shell (PR 3) |
| `features/public/schools-page/…` | The existing catalogue, moved from `landing-page/` |
| `features/public/landing-page/…` | New landing + `sections/*` (hero, key-figures, how-it-works, featured-schools, testimonials, cta-band, site-footer) |
| `features/public/landing-page/count-up.ts` | Pure counter animation helper |
| `app.routes.ts`, `app.config.ts` | Shell route tree; anchor scrolling |
| `i18n/fr.json`, `i18n/en.json` | `nav.*`, `lp.*`, `authPanel.*`, renamed `schools.hero*` keys |

## Execution protocol (applies to every task)

Per PR the orchestrator (main session) checks out the PR branch, then for **each task**:

1. **Coder** (`general-purpose` subagent, runs in the PR branch checkout, tasks strictly sequential) — receives the task text verbatim plus Global Constraints; follows its steps in order (failing test → watch it fail → minimal code → watch it pass → full suite → commit); reports: files changed, failing-test output, passing-test output, commit SHA. Must not touch files outside the task's list.
2. **Gates, run in parallel as read-only subagents on `git diff <task-base>..HEAD`** (they must not edit files):
   - **Reviewer** — correctness vs the task text and spec, Angular/TS rules above, tests actually exercise the behaviour (not tautologies), naming/consistency with neighbouring tasks' Interfaces, a11y (labels, roles, focus, contrast), unnecessary scope.
   - **Security reviewer** — XSS/`innerHTML`/`bypassSecurityTrust*`, URL and query-string construction (encoding, open redirects, `returnUrl`), `target="_blank"` without `rel="noopener noreferrer"`, secrets/PII in URLs or logs, localStorage/token reads on the server, new dependency provenance (name, version, maintainers, install scripts), SSR-only data leaks, CSP-hostile patterns (inline event handlers, `eval`).
   - **Regression checker** — runs the full suite and `npm run build`; compares spec count against the Task 0 baseline (must not drop unless the task says specs were intentionally removed); confirms `dark-mode.guard.spec.ts` and `app.routes.server.spec.ts` are green; confirms the diff touches only the task's listed files; for UI tasks lists neighbouring screens that import anything changed and checks their specs ran.
3. Each gate returns findings as `Blocker | High | Medium | Low` with file:line and a concrete failure scenario. **Blocker/High must be fixed** by the coder in a follow-up commit (`fix(web): …`), then only the gates that raised them re-run (max 2 rounds; a third failure is escalated to the owner). Medium/Low are listed in the PR description, fixed only if trivial.
4. The orchestrator re-reads the final diff of the task itself before starting the next one (CLAUDE.md: review before reporting done).

Prompts the orchestrator sends (fill `<…>`):

- Coder: "You implement Task <N> of `docs/superpowers/plans/2026-10-10-landing-navbar-auth-redesign.md` on branch `<branch>`. Read the plan's Global Constraints and the spec first. Follow the task's steps exactly, TDD, one commit. Do not edit files not listed in the task. Report: files, red output, green output, SHA."
- Reviewer / Security / Regression: "Read-only. Review commit range `<base>..HEAD` for Task <N> of the plan against its task text, the spec, and Global Constraints, through the <review | security | regression> lens defined in 'Execution protocol'. Return findings with severity, file:line, failure scenario. Do not modify files."

---

# PR 1 — Design system and Dock navbar

Branch: `feat/web-design-system-dock-navbar` (from up-to-date `dev` *after* the spec/plan PR is merged).

### Task 0: Land the spec and plan, cut the branch, record the baseline

**Files:** none changed.

- [ ] **Step 1: Merge the docs PR.** On branch `docs/landing-redesign-spec` (spec + this plan): `git push -u origin docs/landing-redesign-spec`, open a PR into `dev`, wait for both checks, squash-merge (owner confirms), then `git checkout dev && git pull --ff-only origin dev && git branch -D docs/landing-redesign-spec`.
- [ ] **Step 2: Cut the branch.** `git checkout -b feat/web-design-system-dock-navbar`.
- [ ] **Step 3: Baseline.** From `apps/web`: `npm ci`, then `npm test -- --no-watch --browsers=ChromeHeadless` and `npm run build`. Record "N specs, 0 failures, build OK" in the PR description draft; the regression checker compares against N.

### Task 1: Brand tokens, Inter, button classes, motion utilities

**Files:**
- Modify: `apps/web/src/styles.css`, `apps/web/package.json`, `apps/web/package-lock.json`
- Create: `apps/web/src/app/shared/design-tokens.spec.ts`

**Interfaces:**
- Produces (CSS, consumed by every later task): Tailwind colour utilities `bg-brand`, `text-brand`, `text-brand-foreground`, `bg-brand-soft`, `text-brand-soft-foreground`, `bg-glass`, `border-glass-border`, `border-border-strong`; classes `.btn` + `.btn-primary|.btn-secondary|.btn-ghost` + `.btn-lg`; `.animate-float`, `.animate-orb`; `.reveal` with `[data-reveal='pending'|'done']`; CSS vars `--brand`, `--glass`, `--glass-border`, `--mesh-1..3`, `--shadow-glow`.

- [ ] **Step 1: Write the failing spec** `src/app/shared/design-tokens.spec.ts`

```ts
function inPage<T>(tag: string, className: string, read: (el: HTMLElement) => T): T {
  const el = document.createElement(tag);
  el.className = className;
  document.body.appendChild(el);
  try {
    return read(el);
  } finally {
    el.remove();
  }
}

function mediaRules(): string[] {
  const found: string[] = [];
  for (const sheet of Array.from(document.styleSheets)) {
    for (const rule of Array.from(sheet.cssRules)) {
      if (rule instanceof CSSMediaRule) {
        found.push(rule.conditionText);
      }
    }
  }
  return found;
}

describe('design tokens', () => {
  it('defines the brand colour on the root', () => {
    const brand = getComputedStyle(document.documentElement).getPropertyValue('--brand').trim();
    expect(brand).not.toBe('');
  });

  it('paints the primary button with the brand colour and a pill shape', () => {
    inPage('button', 'btn btn-primary', (el) => {
      const style = getComputedStyle(el);
      expect(style.backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
      expect(parseFloat(style.borderTopLeftRadius)).toBeGreaterThan(20);
      expect(parseFloat(style.height)).toBeGreaterThanOrEqual(40);
    });
  });

  it('gives the secondary button a visible outline and no fill', () => {
    inPage('button', 'btn btn-secondary', (el) => {
      const style = getComputedStyle(el);
      expect(style.borderTopWidth).toBe('1px');
      expect(style.backgroundColor).toBe('rgba(0, 0, 0, 0)');
    });
  });

  it('keeps the ghost button unfilled', () => {
    inPage('button', 'btn btn-ghost', (el) => {
      expect(getComputedStyle(el).backgroundColor).toBe('rgba(0, 0, 0, 0)');
    });
  });

  it('makes the large button taller than the default one', () => {
    const base = inPage('button', 'btn btn-primary', (el) => parseFloat(getComputedStyle(el).height));
    const large = inPage('button', 'btn btn-primary btn-lg', (el) => parseFloat(getComputedStyle(el).height));
    expect(large).toBeGreaterThan(base);
  });

  it('switches animation off for visitors who ask for reduced motion', () => {
    expect(mediaRules().some((c) => c.includes('prefers-reduced-motion'))).toBeTrue();
  });

  it('hides a pending reveal until it is done', () => {
    inPage('div', 'reveal', (el) => {
      el.setAttribute('data-reveal', 'pending');
      expect(getComputedStyle(el).opacity).toBe('0');
      el.setAttribute('data-reveal', 'done');
      expect(getComputedStyle(el).opacity).toBe('1');
    });
  });
});
```

- [ ] **Step 2: Run it, expect FAIL** — `npm test -- --no-watch --browsers=ChromeHeadless --include='**/design-tokens.spec.ts'` → failures (`--brand` empty, `.btn-primary` transparent).
- [ ] **Step 3: Add the font.** `npm install @fontsource-variable/inter`. (If the sandbox proxy blocks npm, run natively on the host; if it still fails, skip the font, keep the system stack below, and note it in the PR.)
- [ ] **Step 4: Edit `src/styles.css`.** Directly under `@import "tailwindcss";` add `@import "@fontsource-variable/inter";`. In the existing `@theme inline { … }` block append:

```css
  --color-brand: var(--brand);
  --color-brand-hover: var(--brand-hover);
  --color-brand-foreground: var(--brand-foreground);
  --color-brand-soft: var(--brand-soft);
  --color-brand-soft-foreground: var(--brand-soft-foreground);
  --color-glass: var(--glass);
  --color-glass-border: var(--glass-border);
  --color-border-strong: var(--border-strong);
  --font-sans: 'Inter Variable', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
```

  In `:root { … }` append:

```css
  --brand: oklch(0.51 0.23 277);
  --brand-hover: oklch(0.46 0.23 277);
  --brand-foreground: #ffffff;
  --brand-soft: oklch(0.95 0.03 277);
  --brand-soft-foreground: oklch(0.4 0.2 277);
  --glass: rgba(255, 255, 255, 0.72);
  --glass-border: rgba(255, 255, 255, 0.65);
  --border-strong: rgba(0, 0, 0, 0.18);
  --mesh-1: oklch(0.72 0.17 277);
  --mesh-2: oklch(0.74 0.19 310);
  --mesh-3: oklch(0.82 0.11 230);
  --shadow-glow: 0 10px 30px -10px oklch(0.51 0.23 277 / 0.6);
```

  In `.dark { … }` append:

```css
  --brand: oklch(0.58 0.22 277);
  --brand-hover: oklch(0.64 0.22 277);
  --brand-soft: oklch(0.28 0.08 277);
  --brand-soft-foreground: oklch(0.86 0.09 277);
  --glass: rgba(22, 22, 38, 0.62);
  --glass-border: rgba(255, 255, 255, 0.1);
  --border-strong: rgba(255, 255, 255, 0.22);
  --mesh-1: oklch(0.45 0.2 277);
  --mesh-2: oklch(0.42 0.2 310);
  --mesh-3: oklch(0.4 0.12 230);
  --shadow-glow: 0 10px 34px -10px oklch(0.58 0.22 277 / 0.7);
```

  Append at the end of the file:

```css
@layer components {
  .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    height: 2.5rem;
    padding: 0 1rem;
    border: 1px solid transparent;
    border-radius: 9999px;
    font-size: 0.9375rem;
    font-weight: 500;
    line-height: 1;
    letter-spacing: -0.01em;
    white-space: nowrap;
    cursor: pointer;
    transition: background-color 0.15s, border-color 0.15s, box-shadow 0.2s, transform 0.2s, color 0.15s;
  }
  .btn:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
  .btn:disabled { opacity: 0.5; cursor: not-allowed; }
  .btn-lg { height: 3rem; padding: 0 1.5rem; font-size: 1rem; }
  .btn-primary {
    background: var(--brand);
    color: var(--brand-foreground);
    box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.18), 0 1px 2px rgb(0 0 0 / 0.15);
  }
  .btn-primary:hover:not(:disabled) { background: var(--brand-hover); box-shadow: var(--shadow-glow); transform: translateY(-1px); }
  .btn-secondary { background: transparent; color: var(--foreground); border-color: var(--border-strong); }
  .btn-secondary:hover:not(:disabled) { background: var(--accent); }
  .btn-ghost { background: transparent; color: var(--muted-foreground); }
  .btn-ghost:hover:not(:disabled) { background: var(--accent); color: var(--foreground); }
}

@keyframes float-y { 0%, 100% { translate: 0 0; } 50% { translate: 0 -14px; } }
@keyframes orb-drift { 0%, 100% { translate: 0 0; scale: 1; } 50% { translate: 4% 6%; scale: 1.08; } }

.animate-float { animation: float-y 6s ease-in-out infinite; }
.animate-orb { animation: orb-drift 18s ease-in-out infinite; }

.reveal { transition: opacity 0.6s cubic-bezier(0.2, 0.7, 0.2, 1), transform 0.6s cubic-bezier(0.2, 0.7, 0.2, 1); }
.reveal[data-reveal='pending'] { opacity: 0; transform: translateY(16px); }
.reveal[data-reveal='done'] { opacity: 1; transform: none; }

@media (prefers-reduced-motion: reduce) {
  .reveal, .btn { transition: none; }
  .reveal[data-reveal='pending'] { opacity: 1; transform: none; }
  .animate-float, .animate-orb { animation: none; }
  .btn-primary:hover:not(:disabled) { transform: none; }
}
```

- [ ] **Step 5: Run the spec, expect PASS**, then the full suite and `npm run build`.
- [ ] **Step 6: Commit** — `git add src/styles.css package.json package-lock.json src/app/shared/design-tokens.spec.ts && git commit -m "feat(web): brand tokens, Inter, button classes and motion utilities"`
- [ ] **Step 7: Gates** (protocol). Security reviewer also checks the new npm package (name `@fontsource-variable/inter`, version, no install scripts).

### Task 2: Reveal directive and mesh background

**Files:**
- Create: `apps/web/src/app/shared/directives/reveal.directive.ts`, `reveal.directive.spec.ts`
- Create: `apps/web/src/app/shared/components/mesh-background/mesh-background.component.ts`, `mesh-background.component.spec.ts`
- Modify: `apps/web/src/app/shared/dark-mode.guard.spec.ts` (add case `['mesh background', MeshBackgroundComponent]` and its import)

**Interfaces:**
- Produces: `RevealDirective` (`[appReveal]`, adds class `reveal`, sets `data-reveal="pending"|"done"`); `MeshBackgroundComponent` (`<app-mesh-background />`, absolutely fills its positioned parent, `aria-hidden`, `pointer-events: none`, z-index −1 relative to a parent that creates a stacking context via `isolate`).

- [ ] **Step 1: Failing specs.** `reveal.directive.spec.ts`:

```ts
import { Component, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RevealDirective } from './reveal.directive';

@Component({ imports: [RevealDirective], template: `<div appReveal id="target">x</div>` })
class HostComponent {}

class FakeObserver {
  static last: FakeObserver | null = null;
  disconnected = false;
  constructor(public callback: (entries: { isIntersecting: boolean }[]) => void) {
    FakeObserver.last = this;
  }
  observe(): void {}
  disconnect(): void {
    this.disconnected = true;
  }
}

describe('RevealDirective', () => {
  let original: typeof IntersectionObserver;

  beforeEach(() => {
    original = window.IntersectionObserver;
    FakeObserver.last = null;
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  afterEach(() => {
    window.IntersectionObserver = original;
  });

  async function render(top: number): Promise<HTMLElement> {
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    const el = fixture.nativeElement.querySelector('#target') as HTMLElement;
    spyOn(el, 'getBoundingClientRect').and.returnValue({ top } as DOMRect);
    fixture.detectChanges();
    await fixture.whenStable();
    return el;
  }

  function useFakeObserver(): void {
    window.IntersectionObserver = FakeObserver as unknown as typeof IntersectionObserver;
  }

  it('adds the reveal class', async () => {
    useFakeObserver();
    const el = await render(5000);
    expect(el.classList.contains('reveal')).toBeTrue();
  });

  it('hides an element below the fold until it intersects, then marks it done and stops watching', async () => {
    useFakeObserver();
    const el = await render(5000);
    expect(el.getAttribute('data-reveal')).toBe('pending');

    FakeObserver.last!.callback([{ isIntersecting: true }]);

    expect(el.getAttribute('data-reveal')).toBe('done');
    expect(FakeObserver.last!.disconnected).toBeTrue();
  });

  it('keeps waiting while the entry is not intersecting', async () => {
    useFakeObserver();
    const el = await render(5000);
    FakeObserver.last!.callback([{ isIntersecting: false }]);
    expect(el.getAttribute('data-reveal')).toBe('pending');
  });

  it('never hides an element that is already on screen (no flash)', async () => {
    useFakeObserver();
    const el = await render(10);
    expect(el.getAttribute('data-reveal')).toBeNull();
  });

  it('leaves content visible when IntersectionObserver is unavailable', async () => {
    (window as unknown as { IntersectionObserver: unknown }).IntersectionObserver = undefined;
    const el = await render(5000);
    expect(el.getAttribute('data-reveal')).toBeNull();
  });

  it('leaves content visible for visitors who prefer reduced motion', async () => {
    useFakeObserver();
    spyOn(window, 'matchMedia').and.returnValue({ matches: true } as MediaQueryList);
    const el = await render(5000);
    expect(el.getAttribute('data-reveal')).toBeNull();
  });
});
```

  `mesh-background.component.spec.ts`:

```ts
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
```

- [ ] **Step 2: Run, expect FAIL** (files do not exist).
- [ ] **Step 3: Implement** `reveal.directive.ts`:

```ts
import { afterNextRender, DestroyRef, Directive, ElementRef, inject } from '@angular/core';

/**
 * Fades an element in the first time it scrolls into view.
 *
 * Content is never hidden on the server, without IntersectionObserver, under
 * `prefers-reduced-motion`, or when it is already on screen — hiding only ever
 * applies to something the visitor cannot see yet, so there is no flash and no
 * content that depends on JavaScript to appear.
 */
@Directive({ selector: '[appReveal]', host: { class: 'reveal' } })
export class RevealDirective {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    afterNextRender(() => this.observe());
  }

  private observe(): void {
    const win = this.element.ownerDocument.defaultView;
    if (!win || typeof win.IntersectionObserver === 'undefined') {
      return;
    }
    if (win.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    if (this.element.getBoundingClientRect().top < win.innerHeight) {
      return;
    }

    this.element.setAttribute('data-reveal', 'pending');
    const observer = new win.IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          this.element.setAttribute('data-reveal', 'done');
          observer.disconnect();
        }
      },
      { threshold: 0.12 },
    );
    observer.observe(this.element);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }
}
```

  `mesh-background.component.ts`:

```ts
import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Animated gradient backdrop. Pure CSS: no canvas, no images, nothing to load. */
@Component({
  selector: 'app-mesh-background',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true' },
  styles: [
    `
      :host {
        position: absolute;
        inset: 0;
        z-index: -1;
        overflow: hidden;
        pointer-events: none;
        background:
          radial-gradient(60% 50% at 15% 10%, color-mix(in oklch, var(--mesh-1) 32%, transparent), transparent 70%),
          radial-gradient(50% 45% at 90% 15%, color-mix(in oklch, var(--mesh-2) 28%, transparent), transparent 70%),
          var(--background);
      }
      .orb { position: absolute; border-radius: 9999px; filter: blur(70px); opacity: 0.55; }
      .orb-1 { width: 28rem; height: 28rem; top: -8rem; left: -6rem; background: var(--mesh-1); }
      .orb-2 { width: 24rem; height: 24rem; top: 10%; right: -6rem; background: var(--mesh-2); animation-delay: -6s; }
      .orb-3 { width: 20rem; height: 20rem; bottom: -6rem; left: 35%; background: var(--mesh-3); animation-delay: -12s; }
      @media (max-width: 47.99rem) { .orb { filter: blur(50px); } .orb-3 { display: none; } }
    `,
  ],
  template: `
    <span class="orb orb-1 animate-orb"></span>
    <span class="orb orb-2 animate-orb"></span>
    <span class="orb orb-3 animate-orb"></span>
  `,
})
export class MeshBackgroundComponent {}
```

- [ ] **Step 4: Add the mesh case to `dark-mode.guard.spec.ts`** (import + `['mesh background', MeshBackgroundComponent]` in `cases`).
- [ ] **Step 5: Run both specs + guard spec, expect PASS**; full suite; build.
- [ ] **Step 6: Commit** — `feat(web): reveal directive and mesh background`.
- [ ] **Step 7: Gates.**

### Task 3: Pure auto-hide rule

**Files:**
- Create: `apps/web/src/app/shared/components/dock-navbar/dock-visibility.ts`, `dock-visibility.spec.ts`

**Interfaces:**
- Produces: `REVEAL_ZONE_PX = 80`, `SCROLL_DELTA_PX = 8`, `interface DockState`, `nextDockVisible(state: DockState): boolean` (used by Task 4).

- [ ] **Step 1: Failing spec**

```ts
import { DockState, nextDockVisible, REVEAL_ZONE_PX, SCROLL_DELTA_PX } from './dock-visibility';

const base: DockState = {
  scrollY: 600,
  previousScrollY: 600,
  pointerY: null,
  focusWithin: false,
  menuOpen: false,
  visible: true,
};
const at = (patch: Partial<DockState>): boolean => nextDockVisible({ ...base, ...patch });

describe('nextDockVisible', () => {
  it('hides when the page scrolls down past the threshold', () => {
    expect(at({ previousScrollY: 600, scrollY: 600 + SCROLL_DELTA_PX + 1 })).toBeFalse();
  });

  it('returns when the page scrolls up past the threshold', () => {
    expect(at({ visible: false, previousScrollY: 900, scrollY: 900 - SCROLL_DELTA_PX - 1 })).toBeTrue();
  });

  it('ignores jitter smaller than the threshold in either direction', () => {
    expect(at({ visible: true, scrollY: 600 + SCROLL_DELTA_PX })).toBeTrue();
    expect(at({ visible: false, scrollY: 600 - SCROLL_DELTA_PX })).toBeFalse();
  });

  it('is always visible near the top of the page, including iOS overscroll', () => {
    expect(at({ visible: false, previousScrollY: 300, scrollY: REVEAL_ZONE_PX })).toBeTrue();
    expect(at({ visible: false, previousScrollY: 5, scrollY: -30 })).toBeTrue();
  });

  it('reappears when the pointer reaches the top edge, even while scrolling down', () => {
    expect(at({ visible: false, pointerY: REVEAL_ZONE_PX, previousScrollY: 600, scrollY: 700 })).toBeTrue();
  });

  it('does not reappear for a pointer just below the zone', () => {
    expect(at({ visible: false, pointerY: REVEAL_ZONE_PX + 1 })).toBeFalse();
  });

  it('stays visible while keyboard focus is inside, whatever the scroll', () => {
    expect(at({ focusWithin: true, previousScrollY: 600, scrollY: 900 })).toBeTrue();
  });

  it('stays visible while the mobile menu is open', () => {
    expect(at({ menuOpen: true, previousScrollY: 600, scrollY: 900 })).toBeTrue();
  });
});
```

- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** `dock-visibility.ts`:

```ts
/** Pointer this close to the viewport top (px), or a page this close to its top, always shows the bar. */
export const REVEAL_ZONE_PX = 80;
/** Scroll movement below this (px) is jitter, not intent. */
export const SCROLL_DELTA_PX = 8;

export interface DockState {
  readonly scrollY: number;
  readonly previousScrollY: number;
  /** Last known pointer position, null before any mouse movement and on touch devices. */
  readonly pointerY: number | null;
  readonly focusWithin: boolean;
  readonly menuOpen: boolean;
  /** The current answer, kept when the movement is too small to decide. */
  readonly visible: boolean;
}

/** Whether the floating bar should be shown after an input changed. Pure, so every rule is a one-line test. */
export function nextDockVisible(state: DockState): boolean {
  if (state.menuOpen || state.focusWithin) {
    return true;
  }
  if (state.scrollY <= REVEAL_ZONE_PX) {
    return true;
  }
  if (state.pointerY !== null && state.pointerY <= REVEAL_ZONE_PX) {
    return true;
  }

  const delta = state.scrollY - state.previousScrollY;
  if (delta > SCROLL_DELTA_PX) {
    return false;
  }
  if (delta < -SCROLL_DELTA_PX) {
    return true;
  }
  return state.visible;
}
```

- [ ] **Step 4: Run, expect PASS**; full suite. **Step 5: Commit** — `feat(web): pure auto-hide rule for the dock navbar`. **Step 6: Gates.**

### Task 4: Compact language switcher, restyled theme toggle, Dock navbar

**Files:**
- Modify: `apps/web/src/app/shared/components/language-switcher/language-switcher.component.ts` (+ its spec)
- Modify: `apps/web/src/app/shared/components/theme-toggle/theme-toggle.component.ts`
- Create: `apps/web/src/app/shared/components/dock-navbar/dock-navbar.component.ts`, `dock-navbar.component.spec.ts`
- Modify: `apps/web/src/app/i18n/fr.json`, `apps/web/src/app/i18n/en.json` (new top-level `nav`)
- Modify: `apps/web/src/app/shared/dark-mode.guard.spec.ts` (add `['dock navbar', DockNavbarComponent]`)

**Interfaces:**
- Consumes: `nextDockVisible`, `DockState` (Task 3); `.btn*` classes (Task 1); `LocaleService.active: Signal<Locale>`; `TokenService.isAuthenticated`, `TokenService.user`.
- Produces: `<app-dock-navbar />` (no inputs); `LanguageSwitcherComponent.compact = input(false)`.

- [ ] **Step 1: Add i18n keys** (both files, new top-level key `nav`):

  fr: `"nav": { "primary": "Navigation principale", "brandHome": "MeetStudent, retour à l'accueil", "schools": "Établissements", "howItWorks": "Comment ça marche", "reviews": "Avis", "login": "Se connecter", "register": "Créer un compte", "mySpace": "Mon espace", "openMenu": "Ouvrir le menu", "closeMenu": "Fermer le menu", "skipToContent": "Aller au contenu" }`

  en: `"nav": { "primary": "Main navigation", "brandHome": "MeetStudent, back to home", "schools": "Schools", "howItWorks": "How it works", "reviews": "Reviews", "login": "Log in", "register": "Sign up", "mySpace": "My space", "openMenu": "Open menu", "closeMenu": "Close menu", "skipToContent": "Skip to content" }`

- [ ] **Step 2: Failing specs.**
  - Append to `language-switcher.component.spec.ts` (inside the existing `describe`, reusing its `render`, `buttonFor` helpers): a test that after `fixture.componentRef.setInput('compact', true)` + `detectChanges()` the buttons read `FR` and `EN`, each keeps `lang` and gets `aria-label` equal to the full name (`Français` / `English`), and the non-compact tests are unchanged.
  - `dock-navbar.component.spec.ts`:

```ts
import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { REVEAL_ZONE_PX } from './dock-visibility';
import { DockNavbarComponent } from './dock-navbar.component';

@Component({ template: '' })
class BlankComponent {}

describe('DockNavbarComponent', () => {
  let fixture: ComponentFixture<DockNavbarComponent>;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let scrollY: number;

  const root = () => fixture.nativeElement as HTMLElement;
  const nav = () => root().querySelector('nav') as HTMLElement;
  const link = (href: string) => root().querySelector(`a[href="${href}"]`);
  const scrollTo = (y: number) => {
    scrollY = y;
    window.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();
  };

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(DockNavbarComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    authenticated = signal(false);
    scrollY = 0;
    spyOnProperty(window, 'scrollY', 'get').and.callFake(() => scrollY);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', component: BlankComponent }]),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active: signal('fr'), remember: () => undefined } },
        {
          provide: TokenService,
          useValue: {
            isAuthenticated: authenticated,
            user: signal({ firstname: 'Ada', lastname: 'Lovelace' }),
          },
        },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
  });

  it('links the brand to the home page of the current language', async () => {
    await render();
    const brand = link('/fr');
    expect(brand).toBeTruthy();
    expect(brand!.getAttribute('aria-label')).toBe('MeetStudent, retour à l\'accueil');
  });

  it('offers the public sections', async () => {
    await render();
    expect(link('/fr/schools')).toBeTruthy();
    expect(link('/fr#how-it-works')).toBeTruthy();
    expect(link('/fr#reviews')).toBeTruthy();
  });

  it('offers log in and sign up to an anonymous visitor', async () => {
    await render();
    expect(link('/fr/login')).toBeTruthy();
    expect(link('/fr/register')).toBeTruthy();
    expect(link('/fr/home')).toBeNull();
  });

  it('shows "My space" instead once the visitor is signed in', async () => {
    authenticated.set(true);
    await render();
    expect(link('/fr/home')).toBeTruthy();
    expect(link('/fr/register')).toBeNull();
  });

  it('renders the anonymous markup first even for a signed-in visitor, so hydration matches the server', () => {
    authenticated.set(true);
    fixture = TestBed.createComponent(DockNavbarComponent);
    expect((fixture.componentInstance as unknown as { signedIn(): boolean }).signedIn()).toBeFalse();
  });

  it('hides on scroll down and returns on scroll up', async () => {
    await render();
    scrollTo(600);
    scrollTo(900);
    expect(nav().getAttribute('data-hidden')).toBe('true');
    expect(getComputedStyle(nav()).transform).not.toBe('none');

    scrollTo(700);
    expect(nav().getAttribute('data-hidden')).toBe('false');
  });

  it('returns when the pointer reaches the top edge', async () => {
    await render();
    scrollTo(600);
    scrollTo(900);
    expect(nav().getAttribute('data-hidden')).toBe('true');

    document.dispatchEvent(new MouseEvent('mousemove', { clientY: REVEAL_ZONE_PX - 10 }));
    fixture.detectChanges();

    expect(nav().getAttribute('data-hidden')).toBe('false');
  });

  it('does not hide while keyboard focus is inside the bar', async () => {
    await render();
    scrollTo(600);
    (link('/fr/schools') as HTMLElement).dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    scrollTo(900);
    expect(nav().getAttribute('data-hidden')).toBe('false');
  });

  it('opens and closes the mobile menu with the button and Escape, exposing the state', async () => {
    await render();
    const button = root().querySelector('button[aria-controls="dock-menu"]') as HTMLButtonElement;
    expect(button.getAttribute('aria-expanded')).toBe('false');

    button.click();
    fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(root().querySelector('#dock-menu')).toBeTruthy();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(root().querySelector('#dock-menu')).toBeNull();
  });

  it('keeps the bar visible while the menu is open, even when the page scrolls down', async () => {
    await render();
    (root().querySelector('button[aria-controls="dock-menu"]') as HTMLButtonElement).click();
    scrollTo(600);
    scrollTo(900);
    expect(nav().getAttribute('data-hidden')).toBe('false');
  });

  it('closes the menu when the visitor navigates', async () => {
    await render();
    (root().querySelector('button[aria-controls="dock-menu"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    await TestBed.inject(Router).navigateByUrl('/fr/schools');
    fixture.detectChanges();

    expect(root().querySelector('#dock-menu')).toBeNull();
  });
});
```

- [ ] **Step 3: Run, expect FAIL.**
- [ ] **Step 4: Implement.**
  - `language-switcher.component.ts`: add `compact = input(false);`, import `input`; in the template button replace the text with `{{ compact() ? locale.toUpperCase() : t('language.' + locale) }}`, add `[attr.aria-label]="compact() ? t('language.' + locale) : null"`, and set the two class strings to `'rounded-full px-2.5 py-1 text-sm font-semibold bg-brand-soft text-brand-soft-foreground'` (active) / `'rounded-full px-2.5 py-1 text-sm text-muted-foreground hover:bg-accent hover:text-foreground cursor-pointer transition-colors'`.
  - `theme-toggle.component.ts`: replace the button's `class` with `inline-flex h-9 w-9 items-center justify-center rounded-full text-muted-foreground hover:bg-accent hover:text-foreground cursor-pointer transition-colors`.
  - `dock-navbar.component.ts`:

```ts
import { DOCUMENT } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, Menu, X } from 'lucide-angular';
import { filter } from 'rxjs';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { LanguageSwitcherComponent } from '../language-switcher/language-switcher.component';
import { ThemeToggleComponent } from '../theme-toggle/theme-toggle.component';
import { nextDockVisible } from './dock-visibility';

/**
 * The floating pill used by every public page. It hides on scroll down and comes
 * back on scroll up, when the pointer nears the top edge, while focus is inside
 * it, or while the mobile menu is open (see `nextDockVisible`).
 *
 * `(window:scroll)` and `(document:mousemove)` are only wired in the browser,
 * so nothing here runs during SSR.
 */
@Component({
  selector: 'app-dock-navbar',
  imports: [RouterLink, TranslocoDirective, LucideAngularModule, LanguageSwitcherComponent, ThemeToggleComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(window:scroll)': 'onScroll()',
    '(document:mousemove)': 'onPointer($event)',
    '(document:keydown.escape)': 'closeMenu()',
    '(focusin)': 'focusWithin.set(true)',
    '(focusout)': 'onFocusOut($event)',
  },
  styles: [
    `
      :host { display: contents; }
      .wrap { position: fixed; inset: 0 0 auto 0; z-index: 50; display: flex; flex-direction: column; align-items: center; padding: 0.75rem 1rem 0; pointer-events: none; }
      .dock {
        pointer-events: auto; display: flex; align-items: center; gap: 0.5rem; width: 100%; max-width: 64rem; height: 3.5rem;
        padding: 0 0.5rem 0 1rem; border-radius: 9999px; background: var(--glass); border: 1px solid var(--glass-border);
        backdrop-filter: blur(16px) saturate(1.6); box-shadow: 0 8px 32px -12px rgb(0 0 0 / 0.25);
        transition: transform 0.35s cubic-bezier(0.2, 0.7, 0.2, 1), opacity 0.25s;
      }
      @supports not (backdrop-filter: blur(1px)) { .dock { background: var(--card); } }
      .dock[data-hidden='true'] { transform: translateY(-160%); opacity: 0; }
      .brand { display: inline-flex; align-items: center; gap: 0.5rem; font-weight: 600; letter-spacing: -0.02em; color: var(--foreground); text-decoration: none; }
      .logo { display: inline-grid; place-items: center; width: 1.75rem; height: 1.75rem; border-radius: 0.5rem; background: var(--brand); color: var(--brand-foreground); font-size: 0.9rem; }
      .links { display: none; margin-left: 1rem; gap: 0.25rem; }
      .links a, .panel a.item { padding: 0.45rem 0.8rem; border-radius: 9999px; font-size: 0.9rem; color: var(--muted-foreground); text-decoration: none; transition: background-color 0.15s, color 0.15s; }
      .links a:hover, .panel a.item:hover { background: var(--accent); color: var(--foreground); }
      .actions { display: flex; align-items: center; gap: 0.25rem; margin-left: auto; }
      .desktop-only { display: none; }
      .avatar { display: inline-grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: 9999px; background: var(--brand-soft); color: var(--brand-soft-foreground); font-size: 0.8rem; font-weight: 600; }
      .panel { pointer-events: auto; width: 100%; max-width: 64rem; margin-top: 0.5rem; display: flex; flex-direction: column; gap: 0.25rem; padding: 0.75rem; border-radius: 1.5rem; background: var(--card); border: 1px solid var(--border-strong); box-shadow: 0 16px 48px -16px rgb(0 0 0 / 0.35); }
      .panel a.item { font-size: 1rem; padding: 0.75rem 1rem; }
      @media (min-width: 48rem) { .links { display: flex; } .desktop-only { display: inline-flex; } .menu-btn, .panel { display: none; } }
      @media (prefers-reduced-motion: reduce) { .dock { transition: none; } }
    `,
  ],
  template: `
    <header class="wrap" *transloco="let t">
      <nav class="dock" [attr.data-hidden]="!visible()" [attr.aria-label]="t('nav.primary')">
        <a class="brand" [routerLink]="['/', lang()]" [attr.aria-label]="t('nav.brandHome')">
          <span class="logo" aria-hidden="true">M</span>
          <span>MeetStudent</span>
        </a>
        <div class="links">
          <a [routerLink]="['/', lang(), 'schools']">{{ t('nav.schools') }}</a>
          <a [routerLink]="['/', lang()]" fragment="how-it-works">{{ t('nav.howItWorks') }}</a>
          <a [routerLink]="['/', lang()]" fragment="reviews">{{ t('nav.reviews') }}</a>
        </div>
        <div class="actions">
          <app-language-switcher [compact]="true" />
          <app-theme-toggle />
          @if (signedIn()) {
            <a class="btn btn-primary desktop-only" [routerLink]="['/', lang(), 'home']">{{ t('nav.mySpace') }}</a>
            <span class="avatar" aria-hidden="true">{{ initials() }}</span>
          } @else {
            <a class="btn btn-ghost desktop-only" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
            <a class="btn btn-primary desktop-only" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
          }
          <button
            type="button"
            class="btn btn-ghost menu-btn"
            aria-controls="dock-menu"
            [attr.aria-expanded]="menuOpen()"
            [attr.aria-label]="t(menuOpen() ? 'nav.closeMenu' : 'nav.openMenu')"
            (click)="toggleMenu()"
          >
            <lucide-icon [img]="menuOpen() ? X : Menu" class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </nav>
      @if (menuOpen()) {
        <div class="panel" id="dock-menu">
          <a class="item" [routerLink]="['/', lang(), 'schools']">{{ t('nav.schools') }}</a>
          <a class="item" [routerLink]="['/', lang()]" fragment="how-it-works">{{ t('nav.howItWorks') }}</a>
          <a class="item" [routerLink]="['/', lang()]" fragment="reviews">{{ t('nav.reviews') }}</a>
          @if (signedIn()) {
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'home']">{{ t('nav.mySpace') }}</a>
          } @else {
            <a class="btn btn-secondary btn-lg" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
          }
        </div>
      }
    </header>
  `,
})
export class DockNavbarComponent {
  private readonly document = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly router = inject(Router);
  private readonly token = inject(TokenService);
  private readonly locale = inject(LocaleService);

  protected readonly Menu = Menu;
  protected readonly X = X;
  protected readonly lang = this.locale.active;
  protected readonly menuOpen = signal(false);
  protected readonly focusWithin = signal(false);
  protected readonly visible = signal(true);

  /**
   * The session lives in localStorage, which the server cannot read, so the
   * server always renders the anonymous bar. Reading the token before the first
   * client render would produce different markup and break hydration; `hydrated`
   * flips after it.
   */
  private readonly hydrated = signal(false);
  protected readonly signedIn = computed(() => this.hydrated() && this.token.isAuthenticated());
  protected readonly initials = computed(() => {
    const user = this.token.user();
    return `${user?.firstname?.[0] ?? ''}${user?.lastname?.[0] ?? ''}`.toUpperCase() || '?';
  });

  private previousScrollY = 0;
  private pointerY: number | null = null;

  constructor() {
    afterNextRender(() => {
      this.previousScrollY = this.scrollY();
      this.hydrated.set(true);
    });
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
  }

  protected onScroll(): void {
    const y = this.scrollY();
    this.update(y, this.previousScrollY);
    this.previousScrollY = y;
  }

  protected onPointer(event: MouseEvent): void {
    this.pointerY = event.clientY;
    const y = this.scrollY();
    this.update(y, y);
  }

  protected onFocusOut(event: FocusEvent): void {
    if (!this.host.contains(event.relatedTarget as Node | null)) {
      this.focusWithin.set(false);
    }
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
    this.visible.set(true);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  private update(scrollY: number, previousScrollY: number): void {
    this.visible.set(
      nextDockVisible({
        scrollY,
        previousScrollY,
        pointerY: this.pointerY,
        focusWithin: this.focusWithin(),
        menuOpen: this.menuOpen(),
        visible: this.visible(),
      }),
    );
  }

  private scrollY(): number {
    return this.document.defaultView?.scrollY ?? 0;
  }
}
```

  The `[routerLink]` + `fragment` outputs `href="/fr#how-it-works"`; the test's `link('/fr#how-it-works')` relies on that.
- [ ] **Step 5: Add `['dock navbar', DockNavbarComponent]` to the guard spec** (provide the guard spec's existing `TokenService` stub, which already has `isAuthenticated` and `user`).
- [ ] **Step 6: Run the new specs + language-switcher + theme-toggle + guard specs, expect PASS**; full suite; build.
- [ ] **Step 7: Commit** — `feat(web): Dock navbar with auto-hide, compact language switch and restyled theme toggle`. **Step 8: Gates** (security reviewer: link targets built from `LocaleService.active` only, no user-controlled href; reviewer: contrast of `.links a` on glass in both themes).

### Task 5: Public shell, route tree, anchor scrolling, retire old headers

**Files:**
- Create: `apps/web/src/app/shared/layouts/public-shell/public-shell.component.ts`, `public-shell.component.spec.ts`
- Modify: `apps/web/src/app/app.routes.ts`, `apps/web/src/app/app.routes.spec.ts`, `apps/web/src/app/app.config.ts`
- Modify: `apps/web/src/app/shared/layouts/auth-layout/auth-layout.component.ts`
- Modify: `apps/web/src/app/features/public/landing-page/landing-page.component.html`, `landing-page.component.ts`, `landing-page.component.spec.ts` (the catalogue — moved in PR 2)
- Modify: `apps/web/src/app/shared/dark-mode.guard.spec.ts` (add `['public shell', PublicShellComponent]`)

**Interfaces:**
- Consumes: `<app-dock-navbar />` (Task 4); `nav.skipToContent` (Task 4).
- Produces: `PublicShellComponent` (`app-public-shell`): `<a class="skip">` + `<app-dock-navbar />` + `<main id="main"><router-outlet /></main>`; the `/:lang` route tree where `''` (landing/catalogue), `login`, `register` render inside the shell.

- [ ] **Step 1: Failing specs.**
  - `public-shell.component.spec.ts`: render inside `provideRouter([])` + transloco (fr loaded) + the same `LocaleService`/`TokenService` stubs as Task 4; assert `app-dock-navbar`, `main#main` and a router-outlet are present; assert the skip link `a[href="#main"]` exists with text "Aller au contenu" and is visually hidden until focused (`class` contains `sr-only`).
  - `app.routes.spec.ts` (add, in the existing `describe`, using its `harness`): `navigateByUrl('/fr')` → `harness.fixture.nativeElement.querySelector('app-dock-navbar')` is truthy; `/fr/login` → navbar and `app-login-form` both present; `/fr/zzz` (404) → no navbar. If the file's `LocaleService` stub lacks `negotiate`/`active` for the navbar, extend the stub; add a `TokenService` stub `{ isAuthenticated: signal(false), user: signal(null) }`.
  - `landing-page.component.spec.ts` (catalogue): add "does not render its own header: the shell owns navigation" → `expect(fixture.nativeElement.querySelector('header')).toBeNull()`; delete only the existing tests that clicked the header's login/register buttons (their behaviour is now covered by `dock-navbar.component.spec.ts`).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.**
  - `public-shell.component.ts`:

```ts
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { DockNavbarComponent } from '@shared/components/dock-navbar/dock-navbar.component';

/** Frame of every public page: skip link, floating navbar, and the page itself. */
@Component({
  selector: 'app-public-shell',
  imports: [RouterOutlet, TranslocoDirective, DockNavbarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-container *transloco="let t">
      <a
        href="#main"
        class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] btn btn-primary"
        >{{ t('nav.skipToContent') }}</a
      >
    </ng-container>
    <app-dock-navbar />
    <main id="main"><router-outlet /></main>
  `,
})
export class PublicShellComponent {}
```

  - `app.routes.ts`: inside `':lang'` children, keep `home`, `schools/:id`, `profile` as they are, and replace the current pathless auth-layout route with:

```ts
      {
        // Pathless, prefix match: the public pages share one frame (skip link + navbar).
        // Tried after the guarded routes above; the router backtracks out of it for `home` etc.
        path: '',
        loadComponent: () =>
          import('./shared/layouts/public-shell/public-shell.component').then(
            (m) => m.PublicShellComponent,
          ),
        children: [
          {
            path: '',
            pathMatch: 'full',
            title: 'pageTitle.landing',
            loadComponent: () =>
              import('./features/public/landing-page/landing-page.component').then(
                (m) => m.LandingPageComponent,
              ),
          },
          {
            path: '',
            loadComponent: () =>
              import('./shared/layouts/auth-layout/auth-layout.component').then(
                (m) => m.AuthLayoutComponent,
              ),
            children: [ /* login and register routes, unchanged */ ],
          },
        ],
      },
```
    and delete the old top-level `path: ''` landing child. Keep `**` last.
  - `app.config.ts`: `import { provideRouter, TitleStrategy, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';` and `provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }))`. (Behaviour is verified live in Task 6: `/fr#reviews` lands on the section; no stable unit assertion exists for it.)
  - `auth-layout.component.ts`: drop the switcher/theme row and their imports; the template becomes `<div class="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 dark:from-gray-950 dark:to-indigo-950 flex items-center justify-center px-4 pb-8 pt-24"><div class="w-full max-w-md"><router-outlet /></div></div>` (PR 3 replaces this layout).
  - Catalogue `landing-page.component.html`: delete the `<!-- Header -->` block (the whole `<header>…</header>`), and change the hero wrapper `py-16` to `pt-28 pb-16`. `landing-page.component.ts`: remove `LanguageSwitcherComponent`, `ThemeToggleComponent`, `LogIn`, `UserPlus` imports/fields (keep `goTo`, used by `openSchool`).
- [ ] **Step 4: Run new + touched specs, expect PASS**; full suite; build.
- [ ] **Step 5: Commit** — `feat(web): public shell with the Dock navbar around landing and auth`. **Step 6: Gates** (regression checker: every spec that rendered the old header or auth-layout switchers).

### Task 6: PR 1 verification and pull request

**Files:** none changed (unless a fix is needed).

- [ ] **Step 1: Full verification.** `npm test -- --no-watch --browsers=ChromeHeadless` (count ≥ baseline + new, 0 failures) and `npm run build` (SSR build OK).
- [ ] **Step 2: Live check.** From repo root: `cp .env.example .env` if absent, `docker compose up -d` (or run the API natively), then `cd apps/web && npm start`. In the browser verify, at 1440px and 390px, light and dark, `/fr` and `/en`: pill centred and floating; brand returns to `/fr`; FR|EN switch keeps the page; theme icon cycles; hides on scroll down, returns on scroll up and when the mouse touches the top; Tab into the hidden bar reveals it; mobile menu opens/closes with Escape and on navigation; `/fr/login` and `/fr/register` show the navbar and are still functional; log in as a seeded user → navbar shows "Mon espace" with no hydration error in the console (`NG0500`); with the page loaded via SSR (`npm run build && npm run serve:ssr:frontend`) check `view-source` shows the anonymous bar. Screenshots of each into the PR description.
- [ ] **Step 3: Tear down.** `docker compose down`.
- [ ] **Step 4: Push and open the PR.** `git push -u origin feat/web-design-system-dock-navbar`; `gh pr create --base dev` with the test counts, screenshots and the gates' Medium/Low list. Wait for both CI checks; if `dev` moved, update the branch from `dev`, re-run checks; **owner confirms, then squash-merge**; `git checkout dev && git pull --ff-only origin dev && git branch -D feat/web-design-system-dock-navbar`.

---

# PR 2 — Landing page and `/schools`

Branch: `feat/web-landing-page` from up-to-date `dev`. Re-record the baseline (Task 0 step 3 commands).

### Task 7: Move the catalogue to `/:lang/schools`

**Files:**
- Move: `apps/web/src/app/features/public/landing-page/landing-page.component.{ts,html,spec.ts}` → `apps/web/src/app/features/public/schools-page/schools-page.component.{ts,html,spec.ts}` (`git mv`)
- Modify: the moved `.ts` (class `SchoolsPageComponent`, selector `app-schools-page`), moved spec, moved `.html` (two key renames)
- Modify: `apps/web/src/app/app.routes.ts`, `apps/web/src/app/app.routes.spec.ts`, `apps/web/src/app/shared/dark-mode.guard.spec.ts`, `apps/web/src/app/i18n/fr.json`, `apps/web/src/app/i18n/en.json`

**Interfaces:**
- Produces: route `/:lang/schools` (public, SSR) → `SchoolsPageComponent`; `?q=` prefills the search box; i18n `pageTitle.schools`, `schools.heroTitle`, `schools.heroText`.
- Frees the path `features/public/landing-page/` for Task 13.

- [ ] **Step 1: Failing specs** (written in the moved spec and routes spec, after `git mv`, with names fixed):
  - Moved spec: `describe('SchoolsPageComponent')`; add: "prefills the search from ?q=": provide `{ provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ q: 'dak' }) } } }` and assert `component.searchQuery()` is `'dak'`; add "starts empty without ?q=" (empty `convertToParamMap({})`); add "keeps a hostile ?q= as plain text" with `q = '<img src=x onerror=alert(1)>'`: `searchQuery()` equals the string and `fixture.nativeElement.querySelector('img[src="x"]')` is null.
  - `app.routes.spec.ts`: `navigateByUrl('/fr/schools')` renders `app-schools-page` for an anonymous visitor (no redirect to login), and `/fr/schools/7` still redirects to login (guard intact).
  - Guard spec: rename the landing case to `['schools page', SchoolsPageComponent]`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** `git mv` the three files; rename class/selector/`templateUrl`; in `.ts` add `private readonly route = inject(ActivatedRoute);` and initialise `searchQuery = signal(this.route.snapshot.queryParamMap.get('q') ?? '');` (replace the existing `signal('')`; import `ActivatedRoute`). In `schools-page.component.html` rename `t('landing.heroTitle')` → `t('schools.heroTitle')`, `t('landing.heroText')` → `t('schools.heroText')`. `app.routes.ts`: inside the shell's `children`, before the auth-layout route add

```ts
          {
            path: 'schools',
            pathMatch: 'full',
            title: 'pageTitle.schools',
            loadComponent: () =>
              import('./features/public/schools-page/schools-page.component').then(
                (m) => m.SchoolsPageComponent,
              ),
          },
```
  and change the existing `path: ''` landing child's `loadComponent` import path **unchanged** (the new landing arrives in Task 13; until then `/fr` temporarily serves nothing — so in this task point the `''` route at `SchoolsPageComponent` as well, and Task 13 swaps it). i18n (both files): move `landing.heroTitle`/`landing.heroText` text to `schools.heroTitle`/`schools.heroText`, delete the unused `landing.login`/`landing.register`/`landing` object if empty, add `pageTitle.schools` — fr "Établissements et universités au Sénégal", en "Schools and universities in Senegal".
- [ ] **Step 4: Run, expect PASS**; full suite (i18n parity spec included); build. **Step 5: Commit** — `refactor(web): move the school catalogue to /schools and prefill its search from ?q=`. **Step 6: Gates** (security reviewer: `q` is read from the URL — confirm it only reaches `signal`/`[(ngModel)]` and never `innerHTML`).

### Task 8: Landing hero

**Files:**
- Create: `apps/web/src/app/features/public/landing-page/sections/landing-hero.component.ts`, `landing-hero.component.spec.ts`
- Modify: `apps/web/src/app/i18n/fr.json`, `en.json` (new top-level `lp`, sub-object `hero`)
- Modify: `apps/web/src/app/shared/dark-mode.guard.spec.ts`

**Interfaces:**
- Consumes: `.btn*`, `MeshBackgroundComponent`, `LocaleService`.
- Produces: `<app-landing-hero />`.

- [ ] **Step 1: i18n.** `lp.hero` — fr: `{ "eyebrow": "Plateforme d'orientation au Sénégal", "title": "Trouvez l'école qui vous ressemble", "subtitle": "Comparez établissements, formations et avis d'étudiants et d'experts, au même endroit.", "searchLabel": "Rechercher un établissement", "searchPlaceholder": "Un établissement, une ville…", "searchSubmit": "Rechercher", "ctaExplore": "Explorer les établissements", "ctaRegister": "Créer un compte gratuit" }`; en: `{ "eyebrow": "Orientation platform for Senegal", "title": "Find the school that fits you", "subtitle": "Compare schools, programmes and reviews from students and experts, all in one place.", "searchLabel": "Search for a school", "searchPlaceholder": "A school, a city…", "searchSubmit": "Search", "ctaExplore": "Explore schools", "ctaRegister": "Create a free account" }`.
- [ ] **Step 2: Failing spec** `landing-hero.component.spec.ts` (providers: zoneless, `provideRouter([])`, transloco with `fr` loaded, `LocaleService` stub `{ active: signal('fr') }`; spy `navigate` via `spyOn(TestBed.inject(Router), 'navigate')`):

```ts
const submit = (text: string) => {
  const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
  input.value = text;
  input.dispatchEvent(new Event('input'));
  fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
};

it('sends the search to the catalogue with the term as a query parameter', () => {
  submit('Dakar');
  expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], { queryParams: { q: 'Dakar' } });
});
it('trims the term', () => { submit('  Dakar  '); /* q: 'Dakar' */ });
it('opens the plain catalogue for a blank search', () => { submit('   '); /* queryParams: {} */ });
it('keeps reserved characters as data (a&b=c#d) — the router encodes them', () => { submit('a&b=c#d'); /* q: 'a&b=c#d' */ });
it('accepts non-Latin text and caps the field at 100 characters', () => {
  expect(input.getAttribute('maxlength')).toBe('100');
  submit('Université Cheikh Anta Diop — été'); /* passed verbatim */
});
it('prevents the native form submission', () => { const e = new Event('submit', { cancelable: true }); form.dispatchEvent(e); expect(e.defaultPrevented).toBeTrue(); });
it('links the two calls to action to the catalogue and to sign-up', () => { /* hrefs /fr/schools and /fr/register, secondary one has class btn-secondary */ });
it('labels the search field for screen readers', () => { /* label[for] matches input id, text 'Rechercher un établissement' */ });
it('hides the decorative stage from assistive technology and shows no school data', () => {
  const stage = fixture.nativeElement.querySelector('.stage-wrap');
  expect(stage.getAttribute('aria-hidden')).toBe('true');
  expect(stage.textContent?.trim()).toBe('');
});
it('tilts the stage with the pointer and resets on leave', () => { /* dispatch mousemove on host at the right edge: --px style > 0; mouseleave: 0 */ });
```
  Write each commented test out fully when implementing (the comment states the exact assertion).
- [ ] **Step 3: Run, expect FAIL.**
- [ ] **Step 4: Implement** `landing-hero.component.ts`:

```ts
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ArrowRight, GraduationCap, LucideAngularModule, MapPin, Search, Star } from 'lucide-angular';
import { MeshBackgroundComponent } from '@shared/components/mesh-background/mesh-background.component';
import { LocaleService } from '@services/locale.service';

@Component({
  selector: 'app-landing-hero',
  imports: [ReactiveFormsModule, RouterLink, TranslocoDirective, LucideAngularModule, MeshBackgroundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(mousemove)': 'tilt($event)', '(mouseleave)': 'resetTilt()' },
  styles: [
    `
      .stage-wrap { perspective: 1200px; }
      .stage { position: relative; height: 26rem; transform-style: preserve-3d;
        transform: rotateX(calc(var(--py, 0) * -6deg)) rotateY(calc(var(--px, 0) * 8deg)); transition: transform 0.2s ease-out; }
      .card { position: absolute; display: flex; flex-direction: column; gap: 0.6rem; padding: 1rem; border-radius: 1.25rem;
        background: var(--glass); border: 1px solid var(--glass-border); backdrop-filter: blur(14px); box-shadow: var(--shadow-glow); }
      .chip { display: inline-grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: 0.75rem; background: var(--brand-soft); color: var(--brand-soft-foreground); }
      .line { display: block; height: 0.5rem; border-radius: 9999px; background: var(--muted); }
      .c1 { width: 15rem; top: 2rem; left: 6%; transform: translateZ(60px); }
      .c2 { width: 13rem; top: 10rem; right: 2%; transform: translateZ(110px) rotate(3deg); }
      .c3 { width: 14rem; bottom: 1rem; left: 22%; transform: translateZ(30px); }
      @media (max-width: 47.99rem) { .stage { height: 18rem; } .c3 { display: none; } .c1 { left: 0; } .c2 { right: 0; top: 7rem; } }
      @media (prefers-reduced-motion: reduce) { .stage { transform: none; transition: none; } }
    `,
  ],
  template: `
    <section class="relative isolate overflow-hidden" *transloco="let t">
      <app-mesh-background />
      <div class="mx-auto grid max-w-7xl items-center gap-12 px-4 pb-20 pt-32 sm:px-6 lg:grid-cols-2 lg:px-8 lg:pb-28 lg:pt-40">
        <div>
          <p class="mb-4 inline-flex rounded-full bg-brand-soft px-3 py-1 text-sm font-medium text-brand-soft-foreground">{{ t('lp.hero.eyebrow') }}</p>
          <h1 class="text-4xl font-semibold tracking-tight text-foreground sm:text-5xl lg:text-6xl">{{ t('lp.hero.title') }}</h1>
          <p class="mt-5 max-w-xl text-lg text-muted-foreground">{{ t('lp.hero.subtitle') }}</p>

          <form class="mt-8 flex max-w-xl items-center gap-2 rounded-full border border-border-strong bg-glass p-1.5 backdrop-blur" role="search" (submit)="onSubmit($event)">
            <label for="hero-search" class="sr-only">{{ t('lp.hero.searchLabel') }}</label>
            <lucide-icon [img]="Search" class="ml-3 h-5 w-5 shrink-0 text-muted-foreground" aria-hidden="true" />
            <input
              id="hero-search"
              type="search"
              maxlength="100"
              autocomplete="off"
              [formControl]="query"
              [placeholder]="t('lp.hero.searchPlaceholder')"
              class="min-w-0 flex-1 bg-transparent px-1 py-2 text-foreground placeholder:text-muted-foreground focus:outline-none"
            />
            <button type="submit" class="btn btn-primary">{{ t('lp.hero.searchSubmit') }}</button>
          </form>

          <div class="mt-6 flex flex-wrap gap-3">
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'schools']">
              {{ t('lp.hero.ctaExplore') }}
              <lucide-icon [img]="ArrowRight" class="h-4 w-4" aria-hidden="true" />
            </a>
            <a class="btn btn-secondary btn-lg" [routerLink]="['/', lang(), 'register']">{{ t('lp.hero.ctaRegister') }}</a>
          </div>
        </div>

        <div class="stage-wrap hidden md:block" aria-hidden="true">
          <div class="stage" [style.--px]="px()" [style.--py]="py()">
            <div class="card c1 animate-float">
              <span class="chip"><lucide-icon [img]="GraduationCap" class="h-5 w-5" /></span>
              <span class="line w-3/4"></span><span class="line w-1/2"></span>
            </div>
            <div class="card c2 animate-float" style="animation-delay: -2s">
              <div class="flex gap-1 text-yellow-400">
                @for (n of [1, 2, 3, 4, 5]; track n) { <lucide-icon [img]="Star" class="h-4 w-4 fill-yellow-400" /> }
              </div>
              <span class="line w-full"></span><span class="line w-2/3"></span>
            </div>
            <div class="card c3 animate-float" style="animation-delay: -4s">
              <span class="chip"><lucide-icon [img]="MapPin" class="h-5 w-5" /></span>
              <span class="line w-2/3"></span><span class="line w-1/3"></span>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
})
export class LandingHeroComponent {
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly lang = inject(LocaleService).active;

  protected readonly Search = Search;
  protected readonly ArrowRight = ArrowRight;
  protected readonly GraduationCap = GraduationCap;
  protected readonly MapPin = MapPin;
  protected readonly Star = Star;

  protected readonly query = new FormControl('', { nonNullable: true });
  protected readonly px = signal(0);
  protected readonly py = signal(0);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    const q = this.query.value.trim();
    void this.router.navigate(['/', this.lang(), 'schools'], { queryParams: q ? { q } : {} });
  }

  protected tilt(event: MouseEvent): void {
    const rect = this.host.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
      return;
    }
    this.px.set(((event.clientX - rect.left) / rect.width - 0.5) * 2);
    this.py.set(((event.clientY - rect.top) / rect.height - 0.5) * 2);
  }

  protected resetTilt(): void {
    this.px.set(0);
    this.py.set(0);
  }
}
```

  Note the `text-yellow-400` / `fill-yellow-400` classes are on the guard spec's allow-list.
- [ ] **Step 5: Add the guard case; run specs, expect PASS**; full suite; build. **Step 6: Commit** — `feat(web): landing hero with search and 3D stage`. **Step 7: Gates.**

### Task 9: Key figures with count-up

**Files:**
- Create: `apps/web/src/app/features/public/landing-page/count-up.ts`, `count-up.spec.ts`
- Create: `apps/web/src/app/features/public/landing-page/sections/key-figures.component.ts`, `key-figures.component.spec.ts`
- Modify: `i18n/fr.json`, `en.json` (`lp.figures`), `shared/dark-mode.guard.spec.ts`

**Interfaces:**
- Produces: `countUp(target: number, durationMs: number, onTick: (value: number) => void, frames: FrameScheduler): () => void` (returns cancel); `interface FrameScheduler { now(): number; request(cb: (time: number) => void): number; cancel(id: number): void }`; `<app-key-figures />`.
- Consumes: `SchoolService.getSchools(page, size): Observable<Page<School>>`, `ProgramService.getPrograms(page, size): Observable<Page<Program>>` (both expose `totalElements`).

- [ ] **Step 1: i18n.** `lp.figures` fr `{ "title": "La plateforme en chiffres", "schools": "établissements référencés", "programs": "formations à comparer" }`, en `{ "title": "The platform in numbers", "schools": "schools listed", "programs": "programmes to compare" }`.
- [ ] **Step 2: Failing specs.**
  - `count-up.spec.ts` with a fake scheduler (`now` returns a controllable time, `request` stores the callback and returns an id, `cancel` records ids): ends exactly on `target`; emits non-decreasing values; first tick after 0ms emits 0 (never overshoots); eases (value at 50% time is > 50% of target); `target = 0` emits 0 once and stops; cancel stops further ticks; negative or non-finite target is treated as 0.
  - `key-figures.component.spec.ts` (stubs for `SchoolService.getSchools` and `ProgramService.getPrograms` returning `of({ content: [], totalElements: 12 })` / `of({ content: [], totalElements: 34 })`, `LocaleService` stub `{ active: signal('fr') }`): renders both final numbers in `.sr-only` spans (fr formatting `1 234` for 1234 → assert `'1 234'` via `toLocaleString('fr')` computed in the test); renders nothing (no `section`) when either request errors (`throwError`); renders nothing when both totals are 0; treats a missing `totalElements` as 0; requests `size = 1` (not the whole catalogue): `expect(getSchools).toHaveBeenCalledWith(0, 1)`.
- [ ] **Step 3: Run, expect FAIL.**
- [ ] **Step 4: Implement** `count-up.ts`:

```ts
export interface FrameScheduler {
  now(): number;
  request(callback: (time: number) => void): number;
  cancel(id: number): void;
}

export const browserFrames: FrameScheduler = {
  now: () => performance.now(),
  request: (cb) => requestAnimationFrame(cb),
  cancel: (id) => cancelAnimationFrame(id),
};

const easeOutCubic = (t: number): number => 1 - Math.pow(1 - t, 3);

/** Counts from 0 to `target`; returns a function that stops it. Never overshoots. */
export function countUp(
  target: number,
  durationMs: number,
  onTick: (value: number) => void,
  frames: FrameScheduler = browserFrames,
): () => void {
  const end = Number.isFinite(target) && target > 0 ? Math.round(target) : 0;
  const start = frames.now();
  let handle = 0;
  let stopped = false;

  const step = (time: number): void => {
    if (stopped) {
      return;
    }
    const progress = durationMs <= 0 ? 1 : Math.min(Math.max((time - start) / durationMs, 0), 1);
    onTick(progress >= 1 ? end : Math.round(end * easeOutCubic(progress)));
    if (progress < 1) {
      handle = frames.request(step);
    }
  };

  handle = frames.request(step);
  return () => {
    stopped = true;
    frames.cancel(handle);
  };
}
```

  `key-figures.component.ts`: standalone OnPush component; `figures = signal<Figures | null>(null)` (`interface Figures { schools: number; programs: number }`), `displayed = signal<Figures | null>(null)`; in the constructor `forkJoin({ schools: schoolService.getSchools(0, 1), programs: programService.getPrograms(0, 1) })` → map to `{ schools: page.totalElements ?? 0, programs: … }`, `catchError(() => of(null))`, treated as hidden when null or both 0, otherwise `figures.set(f); displayed.set(this.holdAtZero ? ZERO : f)`. `afterNextRender`: `canAnimate = typeof IntersectionObserver !== 'undefined' && !matchMedia('(prefers-reduced-motion: reduce)').matches && host.getBoundingClientRect().top >= innerHeight`; when `canAnimate`, `holdAtZero = true`, `displayed.set(ZERO)` if data already there, and observe the host with `IntersectionObserver({ threshold: 0.3 })`; on intersect run `countUp` for each figure (1200 ms) writing into `displayed`, `holdAtZero = false`, disconnect (also on destroy; cancel the count on destroy). Template: `@if (figures(); as f) { <section class="mx-auto max-w-5xl px-4 py-16" appReveal> <h2 class="sr-only">{{ t('lp.figures.title') }}</h2> <dl class="grid gap-6 sm:grid-cols-2"> … each: <dd><span aria-hidden="true">{{ format(displayed()?.schools ?? f.schools) }}</span><span class="sr-only">{{ format(f.schools) }}</span></dd><dt>{{ t('lp.figures.schools') }}</dt> …` in `rounded-2xl border border-border bg-card p-8` cards with `text-5xl font-semibold tracking-tight text-brand` numbers. `format(n)` = `n.toLocaleString(this.locale.active())`. Both `ProgramService` and `SchoolService` are injected with `inject()`.
- [ ] **Step 5: Add guard case** (the guard spec already stubs both services; `getPrograms` there returns `{ content: [] }`, which exercises the `?? 0` path and renders nothing — add a second case with a populated stub if the coder can do so without altering other cases). **Step 6: Run, PASS, full suite, build. Step 7: Commit** — `feat(web): animated key figures from the API`. **Step 8: Gates.**

### Task 10: How it works

**Files:**
- Create: `…/landing-page/sections/how-it-works.component.ts`, `how-it-works.component.spec.ts`
- Modify: `i18n/fr.json`, `en.json` (`lp.steps`), `shared/dark-mode.guard.spec.ts`

**Interfaces:** Produces `<app-how-it-works />`: `<section id="how-it-works" class="scroll-mt-24">` with three ordered steps.

- [ ] **Step 1: i18n** `lp.steps` — fr: `{ "title": "Comment ça marche", "subtitle": "Trois étapes pour choisir sereinement.", "search": { "title": "Cherchez", "text": "Filtrez par ville, type d'établissement ou formation pour cibler ce qui vous convient." }, "compare": { "title": "Comparez", "text": "Lisez les avis d'étudiants et d'experts, consultez les formations et les accréditations." }, "choose": { "title": "Décidez", "text": "Enregistrez vos favoris et préparez votre dossier avec vos documents au même endroit." } }`; en: `{ "title": "How it works", "subtitle": "Three steps to choose with confidence.", "search": { "title": "Search", "text": "Filter by city, type of school or programme to find what fits." }, "compare": { "title": "Compare", "text": "Read reviews from students and experts, browse programmes and accreditations." }, "choose": { "title": "Decide", "text": "Save your favourites and keep your application documents in one place." } }`.
- [ ] **Step 2: Failing spec:** renders an element with `id="how-it-works"`; three `li` in order Cherchez, Comparez, Décidez (fr) and Search, Compare, Decide (en, after `LocaleService.use('en')`); each step's number badge is `aria-hidden`; the list is an `ol`.
- [ ] **Step 3: Run, FAIL. Step 4: Implement** — `OnPush` component importing `TranslocoDirective`, `LucideAngularModule`, `RevealDirective`; icons `Search`, `Scale`, `BadgeCheck`; template `<section id="how-it-works" class="scroll-mt-24 mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8" *transloco="let t">` with centred `h2`/subtitle and `<ol class="mt-12 grid gap-6 md:grid-cols-3">` of `<li appReveal class="step ...">`; each card `rounded-2xl border border-border bg-card p-6` with a numbered pill (`1`/`2`/`3`, `aria-hidden="true"`), the icon in a `bg-brand-soft text-brand-soft-foreground` square, `h3` and text. Component `styles`: `.step { transform: perspective(900px) rotateX(3deg); transition: transform .3s ease, box-shadow .3s ease; } .step:hover { transform: perspective(900px) rotateX(0) translateY(-4px); box-shadow: var(--shadow-glow); } @media (prefers-reduced-motion: reduce) { .step, .step:hover { transform: none; transition: none; } }`.
- [ ] **Step 5: Guard case; run, PASS; full suite; build. Step 6: Commit** — `feat(web): how-it-works section`. **Step 7: Gates.**

### Task 11: Featured schools

**Files:**
- Create: `…/sections/featured-schools.component.ts`, `featured-schools.component.spec.ts`
- Modify: `i18n/fr.json`, `en.json` (`lp.featured`), `shared/dark-mode.guard.spec.ts`

**Interfaces:**
- Consumes: `SchoolService.getSchools(0, 6)`, `ErrorStateComponent` (`message` input, `retry` output), `ImageWithFallbackComponent`, `roundRating`, `pluralKey` (as the catalogue does).
- Produces: `<app-featured-schools />`.

- [ ] **Step 1: i18n** `lp.featured` fr `{ "title": "Établissements à la une", "subtitle": "Une sélection du catalogue.", "all": "Voir tous les établissements", "error": "Les établissements n'ont pas pu être chargés." }`, en `{ "title": "Featured schools", "subtitle": "A selection from the catalogue.", "all": "See all schools", "error": "Schools could not be loaded." }`.
- [ ] **Step 2: Failing spec** (`SchoolService` stub, router stub): shows up to 6 cards from the API, each an `a` with `href="/fr/schools/<id>"` (real link, not a click handler); shows name, city and rounded rating; a card with no cover/description/rating/city renders without errors (`address` present but empty); while loading shows 3 skeleton cards `aria-hidden`; on error shows `app-error-state` with the message, and clicking retry calls `getSchools` again and renders the cards; empty list shows nothing but keeps the heading hidden (no empty section); the "see all" button links to `/fr/schools`.
- [ ] **Step 3: Run, FAIL. Step 4: Implement** — `OnPush`; `status = signal<'loading'|'loaded'|'error'>('loading')`, `schools = signal<School[]>([])`; `load()` mirrors the catalogue's (`getSchools(0, 6)`, error → `status 'error'`), called from `ngOnInit`. Template: section `mx-auto max-w-7xl px-4 py-20`, header row with `h2` + subtitle + "see all" `a.btn.btn-secondary`; grid `sm:grid-cols-2 lg:grid-cols-3 gap-6`; card `<a appReveal [routerLink]="['/', lang(), 'schools', school.id]" class="group block overflow-hidden rounded-2xl border border-border bg-card transition hover:-translate-y-1 hover:shadow-lg">` with `app-image-with-fallback` cover `h-44 w-full object-cover`, `h3` `line-clamp-2`, city (`MapPin` icon) and rating (`Star` filled `fill-yellow-400 text-yellow-400` + `roundRating(school.rating)`). Skip schools without `id` (`@if (school.id !== undefined)`). Hide the whole section when `status() === 'loaded' && schools().length === 0`.
- [ ] **Step 5: Guard case (existing stub returns two schools); run, PASS; full suite; build. Step 6: Commit** — `feat(web): featured schools from the API`. **Step 7: Gates.**

### Task 12: Testimonials, final CTA band and footer

**Files:**
- Create: `…/sections/testimonials.component.ts`, `testimonials.component.spec.ts`, `cta-band.component.ts`, `cta-band.component.spec.ts`, `site-footer.component.ts`, `site-footer.component.spec.ts`
- Modify: `i18n/fr.json`, `en.json` (`lp.reviews`, `lp.cta`, `lp.footer`), `shared/dark-mode.guard.spec.ts`

**Interfaces:** Produces `<app-testimonials />` (`<section id="reviews" class="scroll-mt-24">`), `<app-cta-band />`, `<app-site-footer />`.

- [ ] **Step 1: i18n.** `lp.reviews` fr `{ "title": "Ils en parlent", "example": "Exemple", "note": "Exemples en attendant les premiers avis vérifiés.", "items": { "a": { "quote": "J'ai pu comparer trois écoles en une soirée, sans courir d'un salon à l'autre.", "who": "Étudiante en licence · Dakar" }, "b": { "quote": "Les avis d'experts m'ont aidé à choisir la bonne filière.", "who": "Élève de terminale · Thiès" }, "c": { "quote": "Tout mon dossier au même endroit, c'est un vrai gain de temps.", "who": "Étudiant en master · Saint-Louis" } } }`; en `{ "title": "What people say", "example": "Example", "note": "Examples until the first verified reviews arrive.", "items": { "a": { "quote": "I compared three schools in one evening, without running between fairs.", "who": "Undergraduate student · Dakar" }, "b": { "quote": "Expert reviews helped me pick the right track.", "who": "Final-year pupil · Thiès" }, "c": { "quote": "Having my whole file in one place saves so much time.", "who": "Master's student · Saint-Louis" } } }`. `lp.cta` fr `{ "title": "Prêt à trouver votre voie ?", "text": "Créez votre compte gratuitement et enregistrez vos établissements favoris.", "primary": "Créer un compte gratuit", "secondary": "Parcourir les établissements" }`, en `{ "title": "Ready to find your path?", "text": "Create your free account and save your favourite schools.", "primary": "Create a free account", "secondary": "Browse schools" }`. `lp.footer` fr `{ "tagline": "Trouvez l'école qui vous correspond.", "product": "Produit", "account": "Compte", "rights": "Tous droits réservés." }`, en `{ "tagline": "Find the school that suits you.", "product": "Product", "account": "Account", "rights": "All rights reserved." }`.
- [ ] **Step 2: Failing specs.** Testimonials: three `figure`s with `blockquote`/`figcaption`; **every one carries a visible "Exemple"/"Example" badge** and the section shows the note (so an unlabelled fake review cannot ship); container `id="reviews"`. CTA band: primary → `/fr/register` (`btn-primary`), secondary → `/fr/schools` (`btn-secondary`); heading present. Footer: brand link `/fr`; links to schools, login, register in the current language; shows the current year via a stubbed `Date` (`jasmine.clock().mockDate(new Date('2030-05-01'))` → "2030"); the `rights` text; no external links without `rel="noopener noreferrer"` (assert none exist).
- [ ] **Step 3: Run, FAIL. Step 4: Implement** the three OnPush components with `TranslocoDirective`, `RouterLink`, `RevealDirective`, `MeshBackgroundComponent` (CTA band: `relative isolate overflow-hidden rounded-3xl mx-4 sm:mx-6 lg:mx-auto max-w-7xl px-8 py-16 text-center border border-border` over `<app-mesh-background />`; footer: `border-t border-border bg-card`, three columns, year from `new Date().getFullYear()`). Testimonial card: `<figure appReveal class="rounded-2xl border border-border bg-card p-6"><span class="rounded-full bg-brand-soft px-2.5 py-0.5 text-xs font-medium text-brand-soft-foreground">{{ t('lp.reviews.example') }}</span><blockquote class="mt-4 text-foreground">…</blockquote><figcaption class="mt-4 text-sm text-muted-foreground">…</figcaption></figure>`; items iterated from `readonly keys = ['a', 'b', 'c'] as const`.
- [ ] **Step 5: Guard cases for the three; run, PASS; full suite; build. Step 6: Commit** — `feat(web): testimonials (labelled examples), CTA band and footer`. **Step 7: Gates.**

### Task 13: Compose the landing and make it the home of `/:lang`

**Files:**
- Create: `apps/web/src/app/features/public/landing-page/landing-page.component.ts`, `landing-page.component.spec.ts`
- Modify: `apps/web/src/app/app.routes.ts` (point `''` back to the new `LandingPageComponent`), `apps/web/src/app/app.routes.spec.ts`, `apps/web/src/app/shared/dark-mode.guard.spec.ts`, `/Users/ibrahimadiallo/IdeaProjects/meetstudent/CLAUDE.md` (routing note)

**Interfaces:** Consumes all seven section selectors. Produces `LandingPageComponent` (`app-landing-page`).

- [ ] **Step 1: Failing specs.** `landing-page.component.spec.ts` (stubs for both services): renders, in this DOM order, `app-landing-hero`, `app-key-figures`, `app-how-it-works`, `app-featured-schools`, `app-testimonials`, `app-cta-band`, `app-site-footer`; exactly one `h1` on the page; `#how-it-works` and `#reviews` exist. **API-down test:** both services `throwError` → hero, how-it-works, testimonials, CTA, footer still render, key figures absent, featured shows `app-error-state`. `app.routes.spec.ts`: `/fr` renders `app-landing-page` (not the catalogue); `/fr/schools` still renders `app-schools-page`; `/fr#reviews` navigation resolves (no NG04002 / no redirect to 404).
- [ ] **Step 2: Run, FAIL. Step 3: Implement.**

```ts
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CtaBandComponent } from './sections/cta-band.component';
import { FeaturedSchoolsComponent } from './sections/featured-schools.component';
import { HowItWorksComponent } from './sections/how-it-works.component';
import { KeyFiguresComponent } from './sections/key-figures.component';
import { LandingHeroComponent } from './sections/landing-hero.component';
import { SiteFooterComponent } from './sections/site-footer.component';
import { TestimonialsComponent } from './sections/testimonials.component';

/** The marketing home of `/:lang`. The school catalogue lives at `/:lang/schools`. */
@Component({
  selector: 'app-landing-page',
  imports: [LandingHeroComponent, KeyFiguresComponent, HowItWorksComponent, FeaturedSchoolsComponent, TestimonialsComponent, CtaBandComponent, SiteFooterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-landing-hero />
    <app-key-figures />
    <app-how-it-works />
    <app-featured-schools />
    <app-testimonials />
    <app-cta-band />
    <app-site-footer />
  `,
})
export class LandingPageComponent {}
```

  `app.routes.ts`: the `''` child imports `./features/public/landing-page/landing-page.component` → `LandingPageComponent` (Task 7 had pointed it at the catalogue). Guard spec: add `['landing page', LandingPageComponent]` (import path `../features/public/landing-page/landing-page.component`). Root `CLAUDE.md`, "Frontend structure notes": replace "Landing, login and register stay server-rendered" sentence's landing context with: the landing is the marketing page at `/:lang`; the public school catalogue is `/:lang/schools` (SSR, no guard).
- [ ] **Step 4: Run, PASS; full suite; build. Step 5: Commit** — `feat(web): marketing landing at /:lang`. **Step 6: Gates.**

### Task 14: PR 2 verification and pull request

- [ ] **Step 1:** full tests + SSR build. **Step 2: Live check** (stack up with seed data, `docker compose up -d`; `npm start`; or the SSR server): `/fr` and `/en` at 1440/390, light/dark — hero stage tilts, search `dakar` lands on `/fr/schools?q=dakar` with the box prefilled; key figures count up once when scrolled to (and show final values with JS disabled / reduced motion); `#how-it-works` and `#reviews` anchors from the navbar scroll to the sections (anchor scrolling from Task 5); featured cards link to school detail (login redirect when signed out); stop the API → figures vanish, featured shows retry, rest intact; `view-source` of the SSR output contains the hero title and key figures; console free of `NG0500`/errors; Lighthouse (mobile) performance and accessibility noted in the PR. Screenshots in the PR. **Step 3:** `docker compose down`. **Step 4:** push `feat/web-landing-page`, `gh pr create --base dev`, wait for checks, owner confirms, squash-merge, delete the local branch, `git pull --ff-only origin dev`.

---

# PR 3 — Auth screens

Branch: `feat/web-auth-split-screen` from up-to-date `dev`; re-record the baseline.

### Task 15: Field style and split-screen auth layout

**Files:**
- Modify: `apps/web/src/styles.css`, `apps/web/src/app/shared/design-tokens.spec.ts` (`.field-input`)
- Create: `apps/web/src/app/shared/layouts/auth-layout/auth-brand-panel.component.ts`, `auth-brand-panel.component.spec.ts`
- Modify: `apps/web/src/app/shared/layouts/auth-layout/auth-layout.component.ts`
- Create: `apps/web/src/app/shared/layouts/auth-layout/auth-layout.component.spec.ts`
- Modify: `i18n/fr.json`, `en.json` (`authPanel`, `nav.backHome`), `shared/dark-mode.guard.spec.ts` (add the panel; the existing `auth layout` case stays)

**Interfaces:**
- Produces: `.field-input` (token-styled text input, ≥44px tall, brand focus ring, error state via `[aria-invalid='true']`); `AuthBrandPanelComponent` (`app-auth-brand-panel`); split `AuthLayoutComponent` with a "← back to home" link.

- [ ] **Step 1: i18n.** `nav.backHome` fr "Retour à l'accueil", en "Back to home". `authPanel` fr `{ "title": "Votre avenir commence ici", "text": "Comparez, enregistrez et préparez votre dossier sur une seule plateforme.", "example": "Exemple", "quote": "J'ai pu comparer trois écoles en une soirée.", "who": "Étudiante en licence · Dakar" }`, en `{ "title": "Your future starts here", "text": "Compare, save and prepare your file on a single platform.", "example": "Example", "quote": "I compared three schools in one evening.", "who": "Undergraduate student · Dakar" }`.
- [ ] **Step 2: Failing specs.**
  - `design-tokens.spec.ts`: `.field-input` computed height ≥ 44px, `border-top-width` `1px`, border radius ≥ 10px; with `aria-invalid="true"` the border colour differs from the default.
  - `auth-brand-panel.component.spec.ts`: shows title and text, the quote carries the "Exemple" badge, the decorative cards are `aria-hidden`.
  - `auth-layout.component.spec.ts` (router stub with a child route, transloco `fr`): `a[href="/fr"]` with text "Retour à l'accueil" exists; `app-auth-brand-panel` exists and its wrapper has the `hidden` and `lg:flex` classes (hidden below `lg`); a `router-outlet` is present; the form column is the second grid child (panel left, form right).
- [ ] **Step 3: Run, FAIL. Step 4: Implement.** `styles.css` inside `@layer components`:

```css
  .field-input {
    width: 100%;
    height: 2.875rem;
    padding: 0 1rem;
    border: 1px solid var(--border-strong);
    border-radius: 0.75rem;
    background: var(--card);
    color: var(--foreground);
    font-size: 0.9375rem;
    transition: border-color 0.15s, box-shadow 0.15s;
  }
  .field-input::placeholder { color: var(--muted-foreground); }
  .field-input:focus { outline: none; border-color: var(--brand); box-shadow: 0 0 0 3px color-mix(in oklch, var(--brand) 25%, transparent); }
  .field-input[aria-invalid='true'] { border-color: var(--destructive); }
```

  `auth-brand-panel.component.ts`: OnPush; `relative isolate overflow-hidden` flex column, `<app-mesh-background />`, brand lockup (`MeetStudent` linking nowhere — the layout owns the back link), `h2` title, text, three decorative glass cards (same abstract style as the hero, `aria-hidden`, `animate-float`), and a bottom glass card containing the labelled example quote (`Exemple` badge). `auth-layout.component.ts` template:

```html
<div class="grid min-h-screen lg:grid-cols-2" *transloco="let t">
  <div class="hidden lg:flex"><app-auth-brand-panel class="flex-1" /></div>
  <div class="flex flex-col justify-center px-4 pb-10 pt-24 sm:px-8 lg:px-16">
    <div class="mx-auto w-full max-w-md">
      <a class="mb-6 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground" [routerLink]="['/', lang()]">
        <lucide-icon [img]="ArrowLeft" class="h-4 w-4" aria-hidden="true" /> {{ t('nav.backHome') }}
      </a>
      <router-outlet />
    </div>
  </div>
</div>
```

  with imports `RouterOutlet`, `RouterLink`, `TranslocoDirective`, `LucideAngularModule`, `AuthBrandPanelComponent`, and `lang = inject(LocaleService).active`.
- [ ] **Step 5: Run, PASS; full suite (login/register specs still green: the forms are unchanged here); build. Step 6: Commit** — `feat(web): split-screen auth layout with brand panel and back link`. **Step 7: Gates.**

### Task 16: Restyle the login form

**Files:**
- Modify: `apps/web/src/app/features/auth/login-form/login-form.component.html`, `login-form.component.spec.ts`

**Interfaces:** Consumes `.field-input`, `.btn*`.

- [ ] **Step 1: Failing spec additions** (in the existing spec, reusing its setup): both inputs have class `field-input`; the submit button has classes `btn btn-primary btn-lg` and `w-full`; an invalid touched email sets `aria-invalid="true"` on the email input; the card no longer centres a lone 64px icon (assert no element with class `w-16`); behaviour tests (submit calls the auth service, shows the error, goes to `returnUrl`) are untouched and stay green.
- [ ] **Step 2: Run, FAIL. Step 3: Implement** in `login-form.component.html`: replace the card wrapper `bg-card rounded-2xl shadow-xl p-8` with a plain `<div>` (the layout provides the frame); replace the icon-circle header with `<h1 class="text-3xl font-semibold tracking-tight text-foreground">` + subtitle left-aligned; every input class string (the long `w-full pl-11 pr-4 py-3 border border-gray-300 …` one) becomes `field-input pl-11`, adding `[attr.aria-invalid]="loginForm.get('email')?.touched && loginForm.get('email')?.invalid ? 'true' : null"` (and the same for `password`); the submit button class becomes `btn btn-primary btn-lg w-full`; the two text buttons (`forgotPassword`, `signUp`) become `text-brand font-medium hover:underline`; the remember-me checkbox gets `accent-brand`. Keep all `formControlName`, ids, handlers and i18n keys.
- [ ] **Step 4: Run, PASS; full suite; build. Step 5: Commit** — `feat(web): restyle the login form`. **Step 6: Gates.**

### Task 17: Restyle the register form

**Files:**
- Modify: `apps/web/src/app/features/auth/register-form/register-form.component.html`, `register-form.component.spec.ts`

- [ ] **Step 1: Failing spec additions:** every `input[formControlName]` and `select[formControlName]` rendered on step 1 and step 2 has class `field-input`; both submit buttons (`Étape suivante`, final submit) have `btn btn-primary btn-lg`; the progress bar segments use `bg-brand` for reached steps and `bg-muted` otherwise (assert the class for step 1 → first segment `bg-brand`, second `bg-muted`; after advancing, both `bg-brand`); a visible step caption "1/2" and "2/2" (`aria-current="step"` on the active segment); the existing field-error `data-testid`s and validation behaviour are unchanged (existing specs stay green).
- [ ] **Step 2: Run, FAIL. Step 3: Implement** with a mechanical replacement in `register-form.component.html`: `grep -c` the long input class string first and record the count, replace each with `field-input pl-11` (or `field-input` where the original has no left icon padding), add `aria-invalid` the same way `fieldError(...)` already decides (reuse its truthiness), turn `bg-indigo-600`/`hover:bg-indigo-700` submit buttons into `btn btn-primary btn-lg` (keep `flex-1` where present), progress segments `bg-indigo-600` → `bg-brand`, and the footer link button to `text-brand font-medium hover:underline`. Remove the card wrapper as in Task 16. No script changes.
- [ ] **Step 4: Run, PASS; full suite; build. Step 5: Commit** — `feat(web): restyle the register form`. **Step 6: Gates.**

### Task 18: PR 3 verification, docs and pull request

- [ ] **Step 1:** full tests + SSR build. **Step 2: Live check** (stack up): `/fr/login` and `/fr/register` at 1440/390, light/dark, fr/en — brand panel left on ≥1024px and gone below; navbar present and the back link returns to `/fr`; log in with a seeded account and with a wrong password (error banner); register both steps (student and expert) end to end and land on login with the success notice; `returnUrl` flow from a guarded page still returns there; tab order logical, focus rings visible; no console errors. Screenshots into the PR. **Step 3:** `docker compose down`.
- [ ] **Step 4: Docs.** In root `CLAUDE.md` and `apps/web/docs/backend-api-integration.md` (only if it mentions the landing/catalogue routes) record the final route map (landing `/:lang`, catalogue `/:lang/schools`, auth inside the public shell). **Step 5:** push `feat/web-auth-split-screen`, `gh pr create --base dev`, wait for checks, owner confirms, squash-merge, delete the local branch.
- [ ] **Step 6: Promotion** is a separate, owner-approved `dev` → `main` fast-forward (`git push origin dev:main`), followed by the post-promotion live test of CLAUDE.md (`docker compose up --build`, exercise API and front, `docker compose down`).

---

## Self-review

- **Spec coverage:** tokens/typography/buttons/GlassCard-equivalent/reveal/reduced-motion (Tasks 1–2) · Dock navbar rules, mobile menu, SSR-safety, brand-to-home (Tasks 3–4) · shell + navbar on landing/catalogue/auth (Task 5) · catalogue to `/schools`, SSR retained (Task 7) · hero with search + 3D stage + parallax (Task 8) · key figures hidden on failure (Task 9) · how it works (Task 10) · featured schools with `error-state` (Task 11) · labelled testimonials, CTA, footer (Task 12) · split auth with back link and 2-step register (Tasks 15–17) · three PRs, squash, live checks (Tasks 6, 14, 18). **Deviation to flag to the owner:** the navbar is mounted on the public pages only; the signed-in screens (`home`, `profile`, `schools/:id`) keep their own headers (wishlist cart, profile menu) so working code is not touched — the navbar already renders "My space" for a signed-in visitor on public pages. Migrating those headers is a separate follow-up.
- **Placeholder scan:** specs in Tasks 8–12 and 15–17 are given as explicit assertions per test; where a test body is summarised in a comment (Task 8), the comment states the exact expected call/value — the coder writes it out in full as part of the red step.
- **Type consistency:** `nextDockVisible`/`DockState` (Task 3) match their use in Task 4; `LanguageSwitcherComponent.compact` (Task 4) is used as `[compact]="true"`; `FrameScheduler`/`countUp` (Task 9) used only in Task 9; section selectors in Task 13 match their `selector`s in Tasks 8–12; i18n namespaces `nav`, `lp.*`, `authPanel`, `schools.hero*` are each introduced before use.
- **Review Focus coverage:** items 1→Task 4, 2→Task 8, 3→Tasks 9, 11, 13, 4→Tasks 3–4, 5→Tasks 5, 7, 13.
