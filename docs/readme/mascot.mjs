// The mascot drawn as SVG, shared by the README illustrations and the installer artwork.
// Its geometry follows MascotFace and MascotGears in the JavaFX shell.

export const colors = { background: '#191a1e', panel: '#25262a', ink: '#f2eadc', muted: '#b3b1b9', warm: '#dfb584' };

function gear(x) {
  const points = Array.from({ length: 24 }, (_, index) => {
    const angle = index * Math.PI / 12;
    const radius = index % 4 === 1 || index % 4 === 2 ? 5.6 : 4.2;
    return `${(Math.cos(angle) * radius).toFixed(3)},${(Math.sin(angle) * radius).toFixed(3)}`;
  }).join(' ');
  return `<g transform="translate(${x} 25)"><polygon points="${points}" fill="#f7e7ca"/>
    <circle r="1.9" fill="#45434a"/></g>`;
}

export function mascot(x, y, scale = 1, expression = 'normal') {
  const eyes = expression === 'gears' ? gear(14) + gear(26) :
    `<g fill="#f7e7ca" transform="translate(${expression === 'curious' ? 1 : -1} 0)">
      <ellipse cx="14" cy="25" rx="2.6" ry="${expression === 'curious' ? 5 : 4.5}"/>
      <ellipse cx="26" cy="25" rx="2.6" ry="${expression === 'curious' ? 5 : 4.5}"/></g>`;
  return `<g transform="translate(${x} ${y}) scale(${scale})">
    <path d="M16 12C16 9 18 8 20 5C22 8 24 9 24 12Z" fill="${colors.warm}"/>
    <rect x="4" y="11" width="32" height="28" rx="10" fill="#45434a" stroke="#b19470" stroke-opacity=".8"/>
    ${eyes}</g>`;
}
