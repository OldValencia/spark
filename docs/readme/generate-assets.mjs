import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = dirname(fileURLToPath(import.meta.url));
const colors = { background: '#191a1e', panel: '#25262a', ink: '#f2eadc', muted: '#b3b1b9', warm: '#dfb584' };
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
const text = (x, y, label, size = 16, fill = colors.ink, extra = '') =>
  `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" ${extra}>${escape(label)}</text>`;

function gear(x) {
  const points = Array.from({ length: 24 }, (_, index) => {
    const angle = index * Math.PI / 12;
    const radius = index % 4 === 1 || index % 4 === 2 ? 5.6 : 4.2;
    return `${(Math.cos(angle) * radius).toFixed(3)},${(Math.sin(angle) * radius).toFixed(3)}`;
  }).join(' ');
  return `<g transform="translate(${x} 25)"><polygon points="${points}" fill="#f7e7ca"/>
    <circle r="1.9" fill="#45434a"/></g>`;
}

function mascot(x, y, scale = 1, expression = 'normal') {
  const eyes = expression === 'gears' ? gear(14) + gear(26) :
    `<g fill="#f7e7ca" transform="translate(${expression === 'curious' ? 1 : -1} 0)">
      <ellipse cx="14" cy="25" rx="2.6" ry="${expression === 'curious' ? 5 : 4.5}"/>
      <ellipse cx="26" cy="25" rx="2.6" ry="${expression === 'curious' ? 5 : 4.5}"/></g>`;
  return `<g transform="translate(${x} ${y}) scale(${scale})">
    <path d="M16 12C16 9 18 8 20 5C22 8 24 9 24 12Z" fill="${colors.warm}"/>
    <rect x="4" y="11" width="32" height="28" rx="10" fill="#45434a" stroke="#b19470" stroke-opacity=".8"/>
    ${eyes}</g>`;
}

function svg(name, width, height, title, description, body) {
  const source = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-labelledby="title desc">
  <title id="title">${escape(title)}</title><desc id="desc">${escape(description)}</desc>
  <g font-family="Segoe UI, system-ui, sans-serif">${body}</g>
</svg>\n`;
  writeFileSync(join(directory, name), source);
}

mkdirSync(directory, { recursive: true });
svg('hero.svg', 1200, 300, 'Spark — your favorite websites, one shortcut away',
  'The Spark mascot beside the app name on a quiet graphite background.', `
  <rect width="1200" height="300" rx="24" fill="${colors.background}"/>
  <path d="M64 240H832" stroke="#34343c"/>
  ${text(64, 76, 'A SMALL WINDOW FOR YOUR REGULAR TABS', 13, colors.warm, 'letter-spacing="2"')}
  ${text(60, 164, 'Spark', 88, colors.ink, 'font-weight="600" letter-spacing="-4"')}
  ${text(64, 206, 'Your favorite websites, one shortcut away.', 24, colors.muted)}
  <ellipse cx="1010" cy="250" rx="62" ry="7" fill="#000000" opacity=".18"/>
  ${mascot(930, 62, 4)}
  ${text(64, 270, 'Windows desktop app · your websites, your accounts', 14, colors.muted)}
  `);

const grid = `<g fill="none" stroke="#c3c3cc" stroke-width="1.7">
  <rect x="537" y="243" width="7" height="7" rx="1.5"/><rect x="550" y="243" width="7" height="7" rx="1.5"/>
  <rect x="537" y="256" width="7" height="7" rx="1.5"/><rect x="550" y="256" width="7" height="7" rx="1.5"/></g>`;
const arrow = path => `<path d="${path}" fill="none" stroke="#b19470" stroke-width="1.5" stroke-linecap="round" marker-end="url(#arrow)"/>`;
svg('tour.svg', 1200, 440, 'A guide to the Spark header',
  'The mascot points out site tabs, the All sites grid, the drag handle, window size, options with settings and extensions, and hide controls. This is an illustration of the shell.', `
  <defs><marker id="arrow" markerWidth="7" markerHeight="7" refX="5" refY="3.5" orient="auto"><path d="M0 0L6 3.5L0 7" fill="none" stroke="#b19470" stroke-width="1.2"/></marker></defs>
  <rect width="1200" height="440" rx="24" fill="${colors.background}"/>
  ${mascot(56, 20, 2.2, 'curious')}
  ${text(174, 70, "I'll show you around.", 30, colors.ink, 'font-weight="600"')}
  ${text(174, 100, 'The website gets the big space. This little strip is Spark.', 17, colors.muted)}
  ${text(468, 153, 'Your sites', 19)}
  ${text(468, 176, 'Find, add, edit, reorder.', 14, colors.muted)}
  ${arrow('M546 184V211')}
  ${text(944, 153, 'Back to your work', 19)}
  ${text(944, 176, 'Hide the window. Keep the page.', 14, colors.muted)}
  ${arrow('M1116 184V211')}
  <rect x="44" y="220" width="1112" height="64" rx="14" fill="${colors.panel}" stroke="#42434b"/>
  ${mascot(62, 230)}
  <rect x="124" y="235" width="144" height="34" rx="8" fill="#343a39" stroke="#647d75"/>
  <circle cx="143" cy="252" r="4" fill="#a7c9bd"/>
  ${text(156, 257, 'ChatGPT', 15)}
  <circle cx="291" cy="252" r="4" fill="#d99a7c"/>
  ${text(304, 257, 'Claude', 15)}
  <circle cx="407" cy="252" r="4" fill="#c3c3cc"/>
  ${text(420, 257, 'Notes', 15)}
  ${grid}
  <path d="M770 252H796" stroke="#6c6d76" stroke-width="4" stroke-linecap="round"/>
  <g fill="none" stroke="#c3c3cc" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
    <rect x="970" y="235" width="34" height="34" rx="8" stroke="#494a53"/>
    <path d="M980 249V244H985M994 255V260H989M980 244L985 249M994 260L989 255"/>
    <path d="M1108 255L1115 248L1122 255"/>
  </g>
  <g fill="#c3c3cc"><circle cx="1049" cy="252" r="1.6"/><circle cx="1057" cy="252" r="1.6"/><circle cx="1065" cy="252" r="1.6"/></g>
  ${arrow('M226 332V294')}
  ${text(88, 363, 'Switch sites', 20)}
  ${text(88, 387, 'Your draft stays on its page.', 14, colors.muted)}
  ${arrow('M782 332V294')}
  ${text(647, 363, 'Move or dock', 20)}
  ${text(647, 387, 'Drag here. Double-click to dock.', 14, colors.muted)}
  ${arrow('M942 332L981 294')}
  ${text(902, 363, 'Window size', 18)}
  ${text(902, 387, 'Size, pin, dock.', 14, colors.muted)}
  ${arrow('M1080 332L1060 294')}
  ${text(1052, 363, 'Options', 18)}
  ${text(1052, 387, 'Settings, extensions.', 14, colors.muted)}
  `);

for (const [file, title, first, second, expression] of [
  ['sites', 'Pick your regulars.', 'AI, notes, a translator, a wiki.', 'The grid has room for your own links.', 'curious'],
  ['window', 'Make some room.', 'Dock it, float it, pin it.', 'Keep the size that works for you.', 'normal'],
  ['settings', 'A few small preferences.', 'My gears mean settings.', 'Yours are under the three dots.', 'gears'],
  ['extensions', 'Bring your extensions.', 'Paste a Chrome Web Store link.', 'They can tell which site is open.', 'curious'],
]) {
  svg(`guide-${file}.svg`, 600, 160, title, `${first} ${second}`, `
    <rect width="600" height="160" rx="18" fill="${colors.background}"/>
    ${mascot(22, 23, 2.5, expression)}
    ${text(146, 58, title, 24, colors.ink, 'font-weight="600"')}
    ${text(146, 91, first, 16, colors.muted)}
    ${text(146, 116, second, 16, colors.muted)}
  `);
}

console.log('Generated six self-contained SVG illustrations in docs/readme/.');
