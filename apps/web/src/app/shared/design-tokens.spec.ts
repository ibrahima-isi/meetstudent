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
      // The reveal transition would report a mid-flight opacity; read the settled value.
      el.style.transition = 'none';
      el.setAttribute('data-reveal', 'done');
      expect(getComputedStyle(el).opacity).toBe('1');
    });
  });
});
