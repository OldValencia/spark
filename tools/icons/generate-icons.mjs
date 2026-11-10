// The SVG is the source of truth for every packaged Spark icon.
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const directory = join(root, 'src/main/resources/app-icons');
const output = join(root, 'build/icon-assets');
const source = readFileSync(join(directory, 'icon.svg'), 'utf8');
const shapes = source.slice(source.indexOf('</desc>') + '</desc>'.length, source.lastIndexOf('</svg>')).trim();
mkdirSync(output, { recursive: true });

writeFileSync(join(directory, 'icon_with_background.svg'), `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512" role="img" aria-labelledby="title desc">
  <title id="title">Spark / Sparky on graphite</title>
  <desc id="desc">The curious Sparky icon on a rounded graphite tile.</desc>
  <rect width="512" height="512" rx="120" fill="#191a1e"/>
  <g transform="translate(40 40) scale(27)">
    ${shapes}
  </g>
</svg>
`);

const sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256, 512, 1024];
for (const size of sizes) {
  execFileSync('magick', ['-density', '192', '-background', 'none', join(directory, 'icon.svg'),
    '-resize', `${size}x${size}`, '-depth', '8', `PNG32:${join(output, `${size}.png`)}`]);
}
writeFileSync(join(directory, 'icon.png'), readFileSync(join(output, '512.png')));

// The tray draws whichever frame matches its size and the display scale, so it never shrinks the large PNG.
const traySizes = [16, 20, 24, 32, 40, 48, 64];
mkdirSync(join(directory, 'tray'), { recursive: true });
for (const size of traySizes) {
  writeFileSync(join(directory, 'tray', `icon-${size}.png`), readFileSync(join(output, `${size}.png`)));
}

const icoSizes = sizes.filter(size => size <= 256);
execFileSync('magick', [...icoSizes.map(size => join(output, `${size}.png`)), join(directory, 'icon.ico')]);

// Modern ICNS entries contain complete PNGs, so no platform-specific icon tools are needed.
const icnsEntries = [
  ['icp4', 16], ['icp5', 32], ['icp6', 64], ['ic07', 128],
  ['ic08', 256], ['ic09', 512], ['ic10', 1024],
  ['ic11', 32], ['ic12', 64], ['ic13', 256], ['ic14', 512]
];
const chunks = icnsEntries.map(([type, size]) => {
  const png = readFileSync(join(output, `${size}.png`));
  const header = Buffer.alloc(8);
  header.write(type, 0, 4, 'ascii');
  header.writeUInt32BE(png.length + 8, 4);
  return Buffer.concat([header, png]);
});
const header = Buffer.alloc(8);
header.write('icns', 0, 4, 'ascii');
header.writeUInt32BE(8 + chunks.reduce((total, chunk) => total + chunk.length, 0), 4);
writeFileSync(join(directory, 'icon.icns'), Buffer.concat([header, ...chunks]));

console.log('Generated background SVG, PNG, ICO and ICNS from icon.svg.');
