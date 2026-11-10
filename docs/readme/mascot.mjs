// The mascot drawn as SVG, shared by the README illustrations and the installer artwork.
// Its geometry follows MascotFace and MascotGears in the JavaFX shell.

export const colors = { background: '#191a1e', panel: '#25262a', ink: '#f2eadc', muted: '#b3b1b9', warm: '#dfb584' };

const glow = '#f7e7ca';

// Eye height as a share of open eyes, matching MascotFace's SAD_EYES and SLEEPY_EYES.
const eyeOpenness = { normal: 1, curious: 5 / 4.5, sad: 0.65, sleepy: 0.12 };

function gear(x) {
  const points = Array.from({ length: 24 }, (_, index) => {
    const angle = index * Math.PI / 12;
    const radius = index % 4 === 1 || index % 4 === 2 ? 5.6 : 4.2;
    return `${(Math.cos(angle) * radius).toFixed(3)},${(Math.sin(angle) * radius).toFixed(3)}`;
  }).join(' ');
  return `<g transform="translate(${x} 25)"><polygon points="${points}" fill="${glow}"/>
    <circle r="1.9" fill="#45434a"/></g>`;
}

function eyes(expression) {
  if (expression === 'gears') {
    return gear(14) + gear(26);
  }
  const ry = +(4.5 * eyeOpenness[expression]).toFixed(2);
  const shift = expression === 'curious' ? 1 : -1;
  return `<g fill="${glow}" transform="translate(${shift} 0)">
      <ellipse cx="14" cy="25" rx="2.6" ry="${ry}"/>
      <ellipse cx="26" cy="25" rx="2.6" ry="${ry}"/></g>`;
}

// The frown and the little "z" of MascotFace.
function marks(expression) {
  const stroke = color => `fill="none" stroke="${color}" stroke-width="1.4" stroke-linecap="round"`;
  if (expression === 'sad') {
    return `<path d="M16.5 34Q20 31.2 23.5 34" ${stroke(glow)}/>`;
  }
  if (expression === 'sleepy') {
    return `<path d="M31 5H35L31 10H35" ${stroke(colors.warm)}/>`;
  }
  return '';
}

/** Expressions: normal, curious, gears, sad (frown) and sleepy (doze). */
export function mascot(x, y, scale = 1, expression = 'normal') {
  return `<g transform="translate(${x} ${y}) scale(${scale})">
    <path d="M16 12C16 9 18 8 20 5C22 8 24 9 24 12Z" fill="${colors.warm}"/>
    <rect x="4" y="11" width="32" height="28" rx="10" fill="#45434a" stroke="#b19470" stroke-opacity=".8"/>
    ${marks(expression)}${eyes(expression)}</g>`;
}
