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

function mediaRules(): CSSMediaRule[] {
  const found: CSSMediaRule[] = [];
  for (const sheet of Array.from(document.styleSheets)) {
    for (const rule of Array.from(sheet.cssRules)) {
      if (rule instanceof CSSMediaRule) {
        found.push(rule);
      }
    }
  }
  return found;
}

function inTheme<T>(dark: boolean, read: () => T): T {
  const root = document.documentElement;
  const had = root.classList.contains('dark');
  root.classList.toggle('dark', dark);
  try {
    return read();
  } finally {
    root.classList.toggle('dark', had);
  }
}

function rootVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function lightness(oklch: string): number {
  const match = /oklch\(\s*([\d.]+)/.exec(oklch);
  expect(match).not.toBeNull();
  return parseFloat(match![1]);
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
    const css = mediaRules()
      .filter((r) => r.conditionText.includes('prefers-reduced-motion'))
      .map((r) => r.cssText)
      .join('\n');
    for (const selector of ['.reveal', '.btn', '.animate-float', '.animate-orb']) {
      expect(css).toContain(selector);
    }
  });

  it('uses a different brand colour in the dark theme', () => {
    const light = inTheme(false, () => rootVar('--brand'));
    const dark = inTheme(true, () => rootVar('--brand'));
    expect(dark).not.toBe(light);
  });

  it('never lightens the primary button on hover in the dark theme', () => {
    inTheme(true, () => {
      expect(lightness(rootVar('--brand-hover'))).toBeLessThanOrEqual(lightness(rootVar('--brand')));
    });
  });

  it('hides a pending reveal until it is done', () => {
    inPage('div', 'reveal', (el) => {
      el.setAttribute('data-reveal', 'pending');
      expect(getComputedStyle(el).opacity).toBe('0');
      expect(getComputedStyle(el).transitionDuration).not.toBe('0s');
      // The reveal transition would report a mid-flight opacity; read the settled value.
      el.style.transition = 'none';
      el.setAttribute('data-reveal', 'done');
      expect(getComputedStyle(el).opacity).toBe('1');
    });
  });

  it('keeps anchor targets clear of the floating navbar', () => {
    const padding = getComputedStyle(document.documentElement).scrollPaddingTop;
    expect(padding).not.toBe('auto');
    expect(parseFloat(padding)).toBeGreaterThanOrEqual(80);
  });
});

describe('design tokens: field input', () => {
  it('is a tall, rounded, bordered text field', () => {
    inPage('input', 'field-input', (el) => {
      const style = getComputedStyle(el);
      expect(parseFloat(style.height)).toBeGreaterThanOrEqual(44);
      expect(style.borderTopWidth).toBe('1px');
      expect(parseFloat(style.borderTopLeftRadius)).toBeGreaterThanOrEqual(10);
    });
  });

  it('changes its border colour when marked invalid', () => {
    const normal = inPage('input', 'field-input', (el) => getComputedStyle(el).borderTopColor);
    const invalid = inPage('input', 'field-input', (el) => {
      el.setAttribute('aria-invalid', 'true');
      return getComputedStyle(el).borderTopColor;
    });
    expect(invalid).not.toBe(normal);
  });
});
