/**
 * `.field-input` (src/styles.css, loaded by the Karma test target) is shared by inputs
 * and textareas. A fixed input height must not crush a textarea: `rows` has to keep
 * driving its height and the text needs vertical padding.
 */
describe('.field-input on a textarea', () => {
  const created: HTMLElement[] = [];

  function mount<K extends 'input' | 'textarea'>(tag: K, rows?: number): HTMLElementTagNameMap[K] {
    const element = document.createElement(tag);
    element.className = 'field-input';
    if (rows !== undefined) element.setAttribute('rows', String(rows));
    document.body.appendChild(element);
    created.push(element);
    return element;
  }

  afterEach(() => created.splice(0).forEach((element) => element.remove()));

  it('keeps the fixed height on an input (sanity check that the stylesheet is loaded)', () => {
    expect(mount('input').getBoundingClientRect().height).toBeCloseTo(46, 0);
  });

  it('lets rows set the height, so a multi-line textarea is taller than an input', () => {
    const input = mount('input').getBoundingClientRect().height;
    const twoRows = mount('textarea', 2).getBoundingClientRect().height;
    const nineRows = mount('textarea', 9).getBoundingClientRect().height;

    expect(twoRows).toBeGreaterThan(input);
    expect(nineRows).toBeGreaterThan(twoRows);
  });

  it('pads the text vertically', () => {
    const style = getComputedStyle(mount('textarea', 2));
    expect(parseFloat(style.paddingTop)).toBeGreaterThan(0);
    expect(parseFloat(style.paddingBottom)).toBeGreaterThan(0);
  });
});
