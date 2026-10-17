/**
 * Guard for the dark/light sweep (same rule as apps/web): a raw palette class that
 * only works on one background (`bg-white`, `text-slate-900`, `border-slate-300`...)
 * must be replaced by a semantic token (`bg-card`, `text-foreground`,
 * `border-border`...) or paired with a `dark:` class of the same kind and variant.
 * Templates are inline in the .ts files, so the sources are scanned as text; each
 * quoted string is one class group.
 */
const PALETTE =
  /^(bg|text|border|ring|divide|placeholder|from|to|via|fill|stroke|outline|file)-(white|black|(?:gray|slate|zinc|neutral|stone|red|green|blue|indigo|purple|yellow|amber|emerald|sky)-\d+)(\/\d+)?$/;

/** Colours that read on both backgrounds. */
const BOTH_MODES = new Set<string>([]);

/** Sources served by the Karma assets entry in angular.json (test target only). */
const FILES = [
  'shared/components/alert/alert.ts',
  'shared/components/image-field/image-field.ts',
  'features/auth/login-page/login-page.ts',
  'features/moderation/moderation-page.ts',
  'features/not-found/not-found-page.ts',
  'features/programs/accreditation-links.ts',
  'features/programs/course-form.ts',
  'features/programs/program-courses.ts',
  'features/programs/program-form.ts',
  'features/programs/programs-page.ts',
  'features/schools/school-form.ts',
  'features/schools/schools-page.ts',
  'features/settings/settings-page.ts',
  'features/shell/shell.ts',
  'features/tags/accreditation-form.ts',
  'features/tags/accreditations-section.ts',
  'features/tags/tags-page.ts',
  'features/tags/tags-section.ts',
];

function lightOnlyClasses(source: string): string[] {
  const offenders = new Set<string>();
  for (const group of source.split(/["'`]/)) {
    const classes = group.split(/\s+/).filter(Boolean);
    for (const cls of classes) {
      const parts = cls.split(':');
      const utility = parts.pop()!;
      if (cls.startsWith('dark:') || BOTH_MODES.has(cls) || !PALETTE.test(utility)) continue;
      const variants = parts.length ? parts.join(':') + ':' : '';
      const kind = utility.split('-')[0];
      if (!classes.some((other) => other.startsWith(`dark:${variants}${kind}-`))) offenders.add(cls);
    }
  }
  return [...offenders].sort();
}

describe('back office dark mode: no light-only palette classes', () => {
  for (const file of FILES) {
    it(`${file} has none`, async () => {
      const response = await fetch(`/app-sources/${file}`);
      expect(response.ok).withContext(file).toBeTrue();
      expect(lightOnlyClasses(await response.text())).toEqual([]);
    });
  }

  it('detects an unpaired palette class and accepts a paired one', () => {
    expect(lightOnlyClasses(`class="bg-white text-slate-900"`)).toEqual(['bg-white', 'text-slate-900']);
    expect(lightOnlyClasses(`class="bg-red-50 dark:bg-red-950 hover:bg-red-100"`)).toEqual(['hover:bg-red-100']);
  });
});
