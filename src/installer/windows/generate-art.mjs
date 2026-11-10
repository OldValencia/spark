// Draws the Windows installer bitmaps. WiX shows its own black text on top of them, so the
// areas behind the text stay white; the Spark look lives in the left panel and the banner corner.
//
// Requires ImageMagick (`magick`) to turn the SVG sources into 24-bit BMP files.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { colors } from '../../../docs/readme/mascot.mjs';

const directory = dirname(fileURLToPath(import.meta.url));
const icon = readFileSync(new URL('../../main/resources/app-icons/icon.svg', import.meta.url), 'utf8');
const shapes = icon.slice(icon.indexOf('</desc>') + '</desc>'.length, icon.lastIndexOf('</svg>')).trim();

function sparky(x, y, size) {
  return `<g transform="translate(${x} ${y}) scale(${size / 16})">${shapes}</g>`;
}

function bitmap(name, width, height, body) {
  const svgFile = join(directory, `${name}.svg`);
  const source = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <g font-family="Segoe UI, system-ui, sans-serif">${body}</g>
</svg>\n`;

  writeFileSync(svgFile, source);
  execFileSync('magick', ['-density', '96', svgFile, '-alpha', 'off', '-type', 'TrueColor',
    `BMP3:${join(directory, `${name}.bmp`)}`]);
}

// Top of the inner pages. The page title and description occupy the left part.
bitmap('banner', 493, 58, `
  <rect width="493" height="58" fill="#ffffff"/>
  ${sparky(441, 4, 50)}
`);

// Welcome and finish pages. WiX draws the text from x = 180 onwards.
bitmap('dialog', 493, 312, `
  <rect width="493" height="312" fill="#ffffff"/>
  <rect width="164" height="312" fill="${colors.background}"/>
  <ellipse cx="82" cy="171" rx="36" ry="5" fill="#000000" opacity=".35"/>
  ${sparky(30, 62, 104)}
  <text x="82" y="214" font-size="26" font-weight="600" fill="${colors.ink}" text-anchor="middle"
    letter-spacing="-0.5">Spark</text>
  <text x="82" y="238" font-size="12" fill="${colors.muted}" text-anchor="middle">Your sites, one</text>
  <text x="82" y="255" font-size="12" fill="${colors.muted}" text-anchor="middle">shortcut away.</text>
`);

console.log('Generated banner.bmp and dialog.bmp in src/installer/windows/.');
