'use strict';
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const canonical = path.join(root, 'web');
for (const target of ['web-preview', 'desktop/resources/web']) {
  const destination = path.join(root, target);
  fs.rmSync(destination, { recursive: true, force: true });
  fs.cpSync(canonical, destination, { recursive: true });
  console.log('Moteur Web synchronisé : ' + target);
}
