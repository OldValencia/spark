import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { colors, mascot } from './mascot.mjs';

const directory = dirname(fileURLToPath(import.meta.url));
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
const text = (x, y, label, size = 16, fill = colors.ink, extra = '') =>
  `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" ${extra}>${escape(label)}</text>`;

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
  ${text(468, 176, 'Find, add, reorder. Ctrl + Shift + L.', 14, colors.muted)}
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
  ${text(88, 387, 'Your draft stays. Right-click for its menu.', 14, colors.muted)}
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

// Small line icons on a 24-unit grid, shown on a badge beside the mascot.
const icons = {
  play: '<path d="M9 7L17 12L9 17Z"/>',
  grid: '<rect x="5" y="5" width="5.5" height="5.5" rx="1"/><rect x="13.5" y="5" width="5.5" height="5.5" rx="1"/><rect x="5" y="13.5" width="5.5" height="5.5" rx="1"/><rect x="13.5" y="13.5" width="5.5" height="5.5" rx="1"/>',
  menu: '<path d="M6 7H18M6 12H18M6 17H13"/>',
  resize: '<path d="M5 10V5H10M19 14V19H14M5 5L10 10M19 19L14 14"/>',
  back: '<path d="M7 7L17 17M17 7L7 17"/>',
  speaker: '<path d="M5 10V14H8L12 17V7L8 10Z"/><path d="M15 9.5A3.5 3.5 0 0 1 15 14.5M17.5 7A7 7 0 0 1 17.5 17"/>',
  sliders: '<path d="M5 8H13M17 8H19M5 16H7M11 16H19"/><circle cx="15" cy="8" r="2"/><circle cx="9" cy="16" r="2"/>',
  puzzle: '<path d="M6 9H9.5A2 2 0 1 1 13.5 9H17V12.5A2 2 0 1 1 17 16.5V19H6Z"/>',
  download: '<path d="M12 5V15M8 11L12 15L16 11M6 19H18"/>',
  bookmark: '<path d="M7 5H17V19L12 15.5L7 19Z"/>',
  terminal: '<rect x="4" y="5" width="16" height="14" rx="2"/><path d="M8 10L10.5 12.5L8 15M12.5 15H16"/>',
  chat: '<path d="M5 6H19V16H11L7 19V16H5Z"/>',
};

function badge(icon) {
  return `<circle cx="116" cy="122" r="17" fill="${colors.panel}" stroke="#b19470" stroke-opacity=".8"/>
    <g transform="translate(105.2 111.2) scale(.9)" fill="none" stroke="${colors.warm}" stroke-width="1.9"
      stroke-linecap="round" stroke-linejoin="round">${icons[icon]}</g>`;
}

// One card opens each part of the README: the mascot's pose, one line of its own, and a short hint.
const cards = [
  ['watch', 'Grab a seat.', 'A minute of Spark at work.', 'No sound, and nobody is signed in.', 'curious', 'play'],
  ['sites', 'Pick your regulars.', 'AI, notes, a translator, a wiki.', 'Ctrl + Shift + L finds one by name.', 'curious', 'grid'],
  ['tab-menu', 'Every tab has a menu.', 'Right-click a tab, or press the menu key.', 'Even for a site that is not on screen.', 'curious', 'menu'],
  ['window', 'Make some room.', 'Dock it, float it, pin it.', 'Each site can keep a size of its own.', 'normal', 'resize'],
  ['off-site', "Wandered off? I'll get you back.", 'A bar shows which website you are on.', '✕ goes back, + keeps the page as a site.', 'curious', 'back'],
  ['small-things', 'I notice the small stuff.', 'A tab making noise, a file you carry in,', 'a site that changed while you were away.', 'curious', 'speaker'],
  ['settings', 'A few small preferences.', 'My gears mean settings.', 'Yours are under the three dots.', 'gears', 'sliders'],
  ['extensions', 'Bring your extensions.', 'Paste a Chrome Web Store link.', 'They can tell which site is open.', 'curious', 'puzzle'],
  ['start', "Let's get you set up.", 'Install, pick a site, record a shortcut.', 'A spare mouse button makes a fine home.', 'normal', 'download'],
  ['return', "I'll keep your place.", 'Hide me, and the page waits as you left it:', 'draft, scroll and all, while Spark runs.', 'sleepy', 'bookmark'],
  ['build', 'Rather build it yourself?', 'JDK 25 and the Gradle wrapper are enough.', 'The native tests drive a real WebView2.', 'gears', 'terminal'],
  ['hello', 'Something off? Tell me.', 'Open an issue with the steps and a screenshot.', 'Small wishes are welcome, too.', 'sad', 'chat'],
];

for (const [file, title, first, second, expression, icon] of cards) {
  svg(`guide-${file}.svg`, 600, 160, title, `${first} ${second}`, `
    <rect width="600" height="160" rx="18" fill="${colors.background}"/>
    ${mascot(22, 23, 2.5, expression)}
    ${badge(icon)}
    ${text(150, 58, title, 24, colors.ink, 'font-weight="600"')}
    ${text(150, 91, first, 16, colors.muted)}
    ${text(150, 116, second, 16, colors.muted)}
  `);
}

console.log(`Generated ${cards.length + 2} self-contained SVG illustrations in docs/readme/.`);
