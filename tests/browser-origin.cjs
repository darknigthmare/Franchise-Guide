'use strict';
// Real origin, localStorage, IndexedDB, Web Locks and service worker. Fictional data only.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { once } = require('node:events');
const { chromium } = require('playwright');
for (const key of Object.keys(process.env)) if (key.startsWith('FG_')) delete process.env[key];
const { start } = require('../server/start.cjs');
const out = path.resolve(__dirname, 'results/current/browser-origin');
fs.mkdirSync(out, { recursive: true });
const report = { version: require('../package.json').version, checks: [], realProviderAccounts: false, errors: [] };
function check(name, condition) {
  assert.ok(condition, name); report.checks.push(name); console.log('PASS ' + name);
}
(async () => {
  const server = start({ port: 0 });
  let browser;
  try {
    await once(server, 'listening');
    const origin = 'http://127.0.0.1:' + server.address().port;
    browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_EXECUTABLE ? { executablePath: process.env.CHROMIUM_EXECUTABLE } : {}) });
    report.browser = browser.version();
    const context = await browser.newContext({ viewport: { width: 1360, height: 900 }, locale: 'fr-FR', timezoneId: 'Europe/Paris' });
    const blockedRemote = [];
    await context.route('**/*', route => {
      const url = new URL(route.request().url());
      if (url.origin === origin || ['data:', 'blob:'].includes(url.protocol)) return route.continue();
      blockedRemote.push(url.origin); return route.abort('blockedbyclient');
    });
    function observe(page) { page.on('pageerror', error => report.errors.push(error.message)); }
    let page = await context.newPage(); observe(page);
    const response = await page.goto(origin, { waitUntil: 'networkidle' });
    await page.waitForFunction(() => storageWritable && storageRevision > 0);
    check('Application loads on its actual HTTP origin', response.status() === 200 && (await page.title()).includes('FranchiseGuide'));
    const policy = response.headers()['content-security-policy'] || await page.locator('meta[http-equiv="Content-Security-Policy"]').getAttribute('content');
    check('Production content security policy remains enabled', policy.includes("script-src 'self'"));
    for (const section of ['shows', 'movies', 'games', 'books']) {
      await page.locator('[data-screen="' + section + '"]:visible, [data-rail="' + section + '"]:visible').first().click();
      await page.locator('[data-add-kind]').first().click();
      check('Contextual add dialog: ' + section, await page.locator('#searchDialog').isVisible());
      await page.locator('#searchDialog .close').click();
    }
    await page.locator('#settings').click();
    await page.locator('#catalogAuto').uncheck();
    await page.locator('#traktAuto').uncheck();
    await page.locator('#jingle').uncheck();
    await page.locator('#saveSettings').click();
    await page.waitForFunction(() => !document.querySelector('#settingsDialog').open);
    const payload = await page.evaluate(() => {
      const sample = clone(seed);
      sample.media = ['show', 'movie', 'game', 'book'].map((kind, i) => ({
        id: 'browser-fixture-' + kind, kind, title: 'Recette navigateur ' + kind,
        source: 'manual', added: true, listIds: [], watchHistory: [], watched: false,
        plays: 0, favorite: false, genres: [], credits: [], ratings: [],
        userNote: 'Note conservée ' + i, catalogComplete: true
      }));
      return toPortableBackup(sample);
    });
    await page.locator('#importFg').setInputFiles({ name: 'browser-fixture.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(payload)) });
    await page.locator('#importReviewDialog').waitFor({ state: 'visible' });
    check('Import preview changes no stored library', await page.evaluate(() => state.media.length === 0));
    check('Import requires explicit confirmation', await page.locator('#commitImportReview').isDisabled());
    await page.locator('#importReviewAcknowledge').check();
    await page.locator('#commitImportReview').click();
    await page.waitForFunction(() => state.media.length === 4 && !document.querySelector('#importReviewDialog')?.open);
    check('Confirmed import persists all four categories', await page.evaluate(() => JSON.parse(localStorage.getItem(KEY)).media.length === 4));
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForFunction(() => storageWritable && state.media.length === 4);
    check('Reload retains private notes and category identities', await page.evaluate(() => state.media.every((m, i) => m.userNote === 'Note conservée ' + i) && new Set(state.media.map(m => m.kind)).size === 4));
    await page.evaluate(async () => { await FGDurable.put(toPortableBackup(state), 'Recette navigateur'); });
    check('Real IndexedDB recovery contains the saved library', await page.evaluate(async () => (await FGDurable.latest()).state.media.length === 4));
    const second = await context.newPage(); observe(second);
    await second.goto(origin, { waitUntil: 'networkidle' });
    await second.locator('#storageBanner').waitFor({ state: 'visible' });
    check('Second real tab opens in read-only mode', await second.evaluate(() => !storageWritable));
    const before = await page.evaluate(() => localStorage.getItem(KEY));
    const blocked = await second.evaluate(() => {
      state.media[0].userNote = 'Must not replace saved note';
      try { save(false); return false; } catch (error) { return error.code === 'READ_ONLY'; }
    });
    check('Second tab cannot overwrite the first tab', blocked && await second.evaluate(() => localStorage.getItem(KEY)) === before);
    await page.close(); page = second;
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForFunction(() => storageWritable);
    check('Closing the writer permits takeover after reload', await page.evaluate(() => state.media[0].userNote === 'Note conservée 0'));
    await page.locator('#settings').click();
    await page.locator('#dark').check();
    await page.locator('#saveSettings').click();
    check('New writer can save a preference', await page.evaluate(() => JSON.parse(localStorage.getItem(KEY)).settings.dark === true));
    await page.evaluate(async () => { await navigator.serviceWorker.ready; });
    await page.waitForFunction(() => navigator.serviceWorker.controller !== null);
    check('Service worker controls the actual origin', await page.evaluate(() => !!navigator.serviceWorker.controller));
    const offlineProbe = '/__offline_probe__?nonce=' + Date.now();
    const onlineProbeStatus = await page.evaluate(async url => (await fetch(url, { cache: 'no-store' })).status, offlineProbe);
    await context.setOffline(true);
    const offlineNavigation = await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => storageWritable && state.media.length === 4);
    // Chromium 151 Headless Shell can report online after a service-worker reload
    // while its transport remains offline. This uncached path returns HTTP 404
    // online; offline, the same request must reject before any HTTP response.
    report.offline = await page.evaluate(async url => {
      const stored = JSON.parse(localStorage.getItem(KEY));
      const networkBlocked = await fetch(url + '&attempt=offline', { cache: 'no-store' }).then(() => false, () => true);
      return {
        navigatorOnline: navigator.onLine, networkBlocked,
        theme: state.settings.dark, storedTheme: stored.settings.dark,
        media: state.media.length, storedMedia: stored.media.length,
        revision: storageRevision, storedRevision: stored._revision
      };
    }, offlineProbe);
    report.offline.onlineProbeStatus = onlineProbeStatus;
    report.offline.navigationFromServiceWorker = offlineNavigation.fromServiceWorker();
    console.log('Offline reload evidence: ' + JSON.stringify(report.offline));
    check('Offline reload keeps the full library and theme',
      onlineProbeStatus === 404 && report.offline.networkBlocked && report.offline.navigationFromServiceWorker &&
      report.offline.theme === true && report.offline.storedTheme === true &&
      report.offline.media === 4 && report.offline.storedMedia === 4);
    check('Recovery snapshot persists across offline reload', await page.evaluate(async () => (await FGDurable.latest()).state.media.length === 4));
    const privateCache = await page.evaluate(async () => {
      const urls = [];
      for (const name of await caches.keys()) for (const request of await (await caches.open(name)).keys()) urls.push(request.url);
      return urls.some(url => new URL(url).pathname.startsWith('/api/'));
    });
    check('Private local API responses are absent from service worker caches', !privateCache);
    await context.setOffline(false);
    await page.locator('[data-screen="shows"]:visible, [data-rail="shows"]:visible').first().click();
    await page.screenshot({ path: path.join(out, 'desktop.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: path.join(out, 'mobile.png'), fullPage: true });
    check('Mobile layout has no document horizontal overflow', await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
    check('Browser emits no uncaught JavaScript errors', report.errors.length === 0);
    check('No external provider requests are required by this recipe', blockedRemote.length === 0);
    report.passed = true; console.log(report.checks.length + ' real-origin browser checks passed.');
  } catch (error) {
    report.passed = false; report.failure = error.stack || String(error);
    console.error(error); process.exitCode = 1;
  } finally {
    if (browser) await browser.close();
    server.closeAllConnections(); await new Promise(resolve => server.close(resolve));
    fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2) + '\n');
  }
})();
