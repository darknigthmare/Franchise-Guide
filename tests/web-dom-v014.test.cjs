'use strict';

/*
 * Full-application integration in jsdom, not a browser test.
 *
 * Run: node --test tests/web-dom-v014.test.cjs
 * Dependencies: jsdom 26.1.0 and fake-indexeddb 6.2.2 (devDependencies).
 *
 * The complete production index.html and every script in its declared order are
 * executed. The real app.css is attached, but layout, CSS rendering, images, CSP,
 * cookies, service workers, installability and OAuth are NOT validated here.
 * Fetch is a closed fixture router: unlisted requests fail, and it never calls
 * Node's fetch. localStorage/sessionStorage are jsdom's memory storage;
 * IndexedDB is fake-indexeddb. Dialogs, scrolling, downloads and storage quota
 * estimation have explicit test shims. No real account or network origin is used.
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { Blob, File } = require('node:buffer');
const { TextDecoder, TextEncoder } = require('node:util');
const { JSDOM, VirtualConsole } = require('jsdom');
const { IDBFactory, IDBKeyRange } = require('fake-indexeddb');

const ROOT = path.resolve(__dirname, '..');
const WEB = path.join(ROOT, 'web');
const HTML = fs.readFileSync(path.join(WEB, 'index.html'), 'utf8');
const CSS = fs.readFileSync(path.join(WEB, 'app.css'), 'utf8');
const SCRIPTS = [...HTML.matchAll(/<script\s+src="([^"]+)"/g)].map(m => m[1]);
const KEY = 'franchiseguide-web-v8';
const ULID = '01JQA000000000000000000000';
const cases = [];
const json = value => JSON.parse(JSON.stringify(value));

function dependencyVersion(name) {
  let directory = path.dirname(require.resolve(name));
  for (;;) {
    const manifest = path.join(directory, 'package.json');
    if (fs.existsSync(manifest)) { const pkg = JSON.parse(fs.readFileSync(manifest, 'utf8')); if (pkg.name === name) return pkg.version; }
    const parent = path.dirname(directory);
    if (parent === directory) throw Error('Dependency manifest not found: ' + name);
    directory = parent;
  }
}

function baseState(overrides = {}) {
  return {
    schema: 8,
    settings: { autoBackupEnabled: false, catalogAutoRefresh: false, traktAutoSync: false, detailJingleEnabled: false },
    media: [], episodes: [], suggestions: [], customFranchises: [],
    lists: [{ id: 'later', name: 'À voir / lire / jouer', sortMode: 'title', ascending: true }],
    gaming: { version: 1, accounts: [], sets: [] },
    ...overrides
  };
}

function retroState() {
  return baseState({
    media: [
      { id: 'retro-game', kind: 'game', title: 'Jeu rétro fictif QA', added: true, source: 'manual', listIds: ['later'], watched: false, userNote: 'Garder la note du jeu', platforms: ['Console QA'] },
      { id: 'film', kind: 'movie', title: 'Film fictif QA', added: true, source: 'manual', listIds: [], watched: true, plays: 1, lastWatchedAt: 1700000000000, watchHistory: [1700000000000] }
    ],
    gaming: {
      version: 1,
      accounts: [{ id: 'retro-account', provider: 'retro', externalId: ULID, username: 'FixtureUser', label: 'Profil QA fictif', autoSync: false, autoImport: false }],
      sets: [{
        id: 'retro-set', mediaId: 'retro-game', provider: 'retro', platform: 'Console QA', accountId: 'retro-account', externalGameId: '123',
        title: 'Succès du jeu fictif QA', edition: 'Édition de test', source: 'retro-api', completeCatalog: true, lastSyncAt: 1700000000000,
        groups: [{ id: 'base', name: 'Jeu de base', kind: 'base' }],
        achievements: [
          { id: 'normal', groupId: 'base', title: 'Premier objectif', description: 'Accomplir le premier objectif fictif.', earned: true, earnedAt: 1700000000000, hardcoreEarned: false, points: 5 },
          { id: 'hardcore', groupId: 'base', title: 'Objectif Hardcore', description: 'Accomplir le défi fictif.', earned: true, earnedAt: 1700000000000, hardcoreEarned: true, hardcoreEarnedAt: 1700000000000, points: 10 },
          { id: 'unknown', groupId: 'base', title: 'État non fourni', description: 'Données fictives incomplètes.', earned: null, hardcoreEarned: null, points: 5 },
          { id: 'secret', groupId: 'base', title: 'Secret QA caché', description: 'Ne doit pas apparaître avant révélation.', hidden: true, earned: false, hardcoreEarned: false, points: 5 }
        ]
      }]
    }
  });
}

function response(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', ...headers } });
}

function catalogueFixture(url) {
  const u = new URL(url), p = u.pathname;
  if (u.hostname === 'api.themoviedb.org') {
    if (p === '/3/search/tv') return response({ page: 1, total_pages: 1, results: [{ id: 111, name: 'Série fictive QA', first_air_date: '2025-01-01', poster_path: '/qa-series.png' }] });
    if (p === '/3/search/movie') return response({ page: 1, total_pages: 1, results: [{ id: 222, title: 'Film fictif catalogue QA', release_date: '2025-01-01', poster_path: '/qa-movie.png' }] });
    if (p === '/3/tv/111') return response({ id: 111, name: 'Série fictive QA', status: 'Returning Series', overview: 'Description de test.', first_air_date: '2025-01-01', poster_path: '/qa-series.png', genres: [{ name: 'Science-fiction' }], number_of_seasons: 1, number_of_episodes: 2, seasons: [{ season_number: 1, episode_count: 2 }], aggregate_credits: { cast: [{ id: 5, name: 'Personne fictive', profile_path: '/qa-cast.png', roles: [{ character: 'Rôle fictif' }] }] }, external_ids: {}, videos: { results: [] }, episode_run_time: [45] });
    if (p === '/3/tv/111/season/1') return response({ id: 10, name: 'Saison 1', poster_path: '/qa-season.png', episodes: [1, 2].map(n => ({ id: 100 + n, episode_number: n, name: `Épisode fictif ${n}`, air_date: `2025-01-0${n}`, runtime: 45, still_path: '/qa-still.png', overview: 'Résumé fictif.', vote_average: 0, vote_count: 0 })), 'watch/providers': { results: {} } });
    if (p === '/3/movie/222') return response({ id: 222, title: 'Film fictif catalogue QA', release_date: '2025-01-01', runtime: 100, overview: 'Description de test.', genres: [{ name: 'Drame' }], poster_path: '/qa-movie.png', credits: { cast: [] }, videos: { results: [] } });
    if (p.endsWith('/watch/providers')) return response({ results: {} });
  }
  if (u.hostname === 'api.rawg.io') {
    if (p === '/api/games') return response({ results: [{ id: 333, name: 'Jeu fictif catalogue QA', released: '2025-01-01', background_image: 'https://fixtures.invalid/game.png' }], next: null });
    if (p === '/api/games/333') return response({ id: 333, name: 'Jeu fictif catalogue QA', description_raw: 'Description de test.', platforms: [{ platform: { name: 'PC' } }], genres: [{ name: 'Aventure' }], background_image: 'https://fixtures.invalid/game.png' });
  }
  if (u.hostname === 'openlibrary.org') {
    if (p === '/search.json') return response({ numFound: 1, docs: [{ key: '/works/OL1W', title: 'Livre fictif QA', author_name: ['Auteur fictif'], cover_i: 1, number_of_pages_median: 240 }] });
    if (p === '/works/OL1W.json') return response({ key: '/works/OL1W', title: 'Livre fictif QA', description: 'Description de test.', subjects: ['Roman'], covers: [1] });
  }
  return null;
}

async function createApp(t, options = {}) {
  const errors = [], calls = [], unmatched = [], downloads = [], blobUrls = new Map();
  const cacheRows = new Map([
    ['franchiseguide-images-fixture', ['poster-one', 'poster-two']],
    ['franchiseguide-web-fixture', ['index.html']],
    ['unrelated-cache', ['other']]
  ]);
  const vc = new VirtualConsole();
  vc.on('jsdomError', err => errors.push(err.message));
  const dom = new JSDOM(HTML, {
    url: 'https://franchiseguide.test/', runScripts: 'outside-only', pretendToBeVisual: true,
    virtualConsole: vc,
    beforeParse(w) {
      w.Blob = Blob; w.File = File; w.TextDecoder = TextDecoder; w.TextEncoder = TextEncoder;
      w.Response = Response; w.Request = Request; w.Headers = Headers;
      w.AbortController = AbortController; w.AbortSignal = AbortSignal;
      w.structuredClone = structuredClone;
      w.indexedDB = options.indexedDB || new IDBFactory(); w.IDBKeyRange = IDBKeyRange;
      w.scrollTo = () => {};
      w.confirm = () => true;
      w.prompt = () => null;
      w.Audio = class { play() { return Promise.resolve(); } pause() {} };
      w.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
      w.HTMLDialogElement.prototype.close = function () {
        if (!this.open) return;
        this.removeAttribute('open');
        this.dispatchEvent(new w.Event('close'));
      };
      // jsdom has no rendered innerText. This shim supplies plain DOM text only.
      Object.defineProperty(w.HTMLElement.prototype, 'innerText', { configurable: true, get() { return this.textContent; }, set(value) { this.textContent = value; } });
      Object.defineProperty(w.navigator, 'storage', { configurable: true, value: { estimate: async () => ({ quota: 1000000, usage: 1000 }), persisted: async () => false, persist: async () => false } });
      w.caches = { keys: async () => [...cacheRows.keys()], open: async name => ({ keys: async () => (cacheRows.get(name) || []).map(url => ({ url })) }), delete: async name => cacheRows.delete(name) };
      w.URL.createObjectURL = blob => { const url = `blob:fixture-${blobUrls.size + 1}`; blobUrls.set(url, blob); return url; };
      w.URL.revokeObjectURL = url => blobUrls.delete(url);
      w.HTMLAnchorElement.prototype.click = function () {
        if (!this.download) throw Error('This DOM harness must not navigate external links.');
        downloads.push({ name: this.download, blob: blobUrls.get(this.href) });
      };
      w.addEventListener('error', e => errors.push(e.error?.stack || e.message));
      w.addEventListener('unhandledrejection', e => errors.push(String(e.reason?.stack || e.reason)));
      w.fetch = async (input, init = {}) => {
        const u = new URL(String(input), w.location.href), method = init.method || 'GET';
        calls.push({ url: u.href, path: u.pathname, method, headers: init.headers });
        if (init.signal?.aborted) throw init.signal.reason || Error('Aborted fixture');
        if (u.origin === w.location.origin) {
          if (u.pathname === '/api/services/config' && method === 'GET') {
            assert.equal(new Headers(init.headers).get('X-FranchiseGuide-Services'), '1');
            return response({ error: 'No publisher gateway in this standalone Web fixture.' }, 404);
          }
          if (u.pathname === '/api/gaming/status') return response({ app: 'FranchiseGuide Gaming Bridge', steam: false, steamSignIn: false });
          if (u.pathname === '/api/gaming/retro/status') return response({ provider: 'retro', configured: false });
          if (u.pathname === '/api/gaming/auth/steam/session') return response({ authenticated: false });
          if (u.pathname === '/api/gaming/auth/steam/begin') return response({ error: 'Configure FG_STEAM_API_KEY dans le serveur local.' }, 503);
        }
        if (options.catalogue) { const fixture = catalogueFixture(u.href); if (fixture) return fixture; }
        if (method === 'GET' && u.hostname === 'api.tvmaze.com' && u.pathname === '/search/shows') return response([]);
        if (method === 'GET' && u.hostname === 'itunes.apple.com' && u.pathname === '/search') return response({ resultCount: 0, results: [] });
        if (method === 'GET' && u.hostname === 'www.freetogame.com' && u.pathname === '/api/games') return response([]);
        unmatched.push(`${method} ${u.href}`);
        throw Error('Unexpected fixture request: ' + unmatched.at(-1));
      };
      w.localStorage.setItem(KEY, options.rawStorage === undefined ? JSON.stringify(options.state || baseState()) : options.rawStorage);
      for (const [key, value] of Object.entries(options.storage || {})) w.localStorage.setItem(key, String(value));
    }
  });
  const w = dom.window, context = dom.getInternalVMContext();
  t.after(() => dom.window.close());
  const style = w.document.createElement('style'); style.dataset.qaSource = 'app.css'; style.textContent = CSS; w.document.head.append(style);
  for (const script of SCRIPTS) vm.runInContext(fs.readFileSync(path.join(WEB, script), 'utf8'), context, { filename: script });
  const app = {
    w, dom, errors, calls, unmatched, downloads, cacheRows,
    evaluate(code) { return vm.runInContext(code, context, { filename: 'web-dom-fixture.js' }); },
    data(code) { return json(this.evaluate(code)); },
    el(selector) { const element = w.document.querySelector(selector); assert.ok(element, `Missing production DOM element: ${selector}`); return element; },
    all(selector) { return [...w.document.querySelectorAll(selector)]; },
    text(selector = '#content') { return this.el(selector).textContent; },
    click(selector) { this.el(selector).click(); },
    input(selector, value) { const e = this.el(selector); e.value = value; e.dispatchEvent(new w.Event('input', { bubbles: true })); },
    change(selector, value) { const e = this.el(selector); if (e.type === 'checkbox') e.checked = value; else e.value = value; e.dispatchEvent(new w.Event('change', { bubbles: true })); },
    async submit() { const f = this.el('#gamingForm'); await f.onsubmit({ preventDefault() {}, target: f }); },
    async imported(value) {
      const e = this.el('#importFg'), file = new File([JSON.stringify(value)], 'fixture-backup.json', { type: 'application/json' });
      const arrayBuffer = file.arrayBuffer.bind(file);
      file.arrayBuffer = async () => this.w.Uint8Array.from(new Uint8Array(await arrayBuffer())).buffer;
      Object.defineProperty(e, 'files', { configurable: true, value: [file] });
      await e.onchange({ target: e });
    },
    async confirmImportReview() {
      assert.equal(this.el('#commitImportReview').disabled, true, 'Review requires an explicit acknowledgement');
      this.change('#importReviewAcknowledge', true);
      await this.el('#importReviewForm').onsubmit({ preventDefault() {} });
      assert.equal(this.w.document.querySelector('#importReviewDialog'), null, 'Confirmed review closes after the durable commit');
    },
    async downloaded() { assert.ok(downloads.length, 'Expected a real download handler to create an export'); return JSON.parse(await downloads.at(-1).blob.text()); },
    async waitFor(predicate, label = 'DOM state') {
      const end = Date.now() + 2500;
      while (!predicate()) { if (Date.now() >= end) throw Error('Timed out waiting for ' + label); await new Promise(resolve => setTimeout(resolve, 5)); }
    },
    async more(route) {
      this.click('#nav [data-screen="more"]'); this.click(`[data-quick="${route}"]`);
      if (route === 'maintenance') await this.waitFor(() => this.w.document.querySelector('#storageFacts .facts'), 'storage facts');
    },
    assertClean() { assert.deepEqual(errors, [], 'No uncaught app/DOM errors'); assert.deepEqual(unmatched, [], 'No unlisted or real network request'); }
  };
  await app.waitFor(() => app.evaluate('recoveryOnly !== null || retroBridge.checked > 0'), 'startup completion');
  app.assertClean();
  return app;
}

function integration(name, body) {
  test(name, { timeout: 20000 }, async t => {
    const started = Date.now();
    try { await body(t); cases.push({ name, status: 'passed', durationMs: Date.now() - started }); }
    catch (error) { cases.push({ name, status: 'failed', durationMs: Date.now() - started, error: error.stack || error.message }); throw error; }
  });
}

integration('Complete production document boots all scripts and persists one library/queue envelope', async t => {
  const app = await createApp(t);
  assert.ok(SCRIPTS.includes('retro-connect.js'));
  assert.equal(SCRIPTS.at(-1), 'startup.js');
  assert.equal(app.all('script[src]').length, SCRIPTS.length);
  assert.equal(app.el('style[data-qa-source]').textContent, CSS);
  assert.equal(app.text('#title'), 'Séries');
  assert.equal(app.el('#search').getAttribute('aria-label'), 'Rechercher / ajouter un titre');
  assert.equal(app.el('#toast').getAttribute('aria-live'), 'polite');
  const persisted = JSON.parse(app.w.localStorage.getItem(KEY));
  assert.equal(persisted.schema, 11);
  assert.ok(persisted._revision > 0);
  assert.deepEqual(persisted._syncState.queue, []);
  assert.equal(app.w.navigator.serviceWorker, undefined, 'No service worker is simulated or validated');
  app.assertClean();
});

integration('Four category plus buttons, rail navigation and More routes are wired', async t => {
  const app = await createApp(t);
  for (const [screen, kind] of [['shows', 'show'], ['movies', 'movie'], ['games', 'game'], ['books', 'book']]) {
    app.click(`#nav [data-screen="${screen}"]`);
    assert.equal(app.el(`#nav [data-screen="${screen}"]`).getAttribute('aria-current'), 'page');
    app.click(`[data-add-kind="${kind}"]`);
    assert.equal(app.evaluate('kindFilter'), kind);
    assert.equal(app.el('#searchDialog').open, true);
    assert.equal(app.el('#kinds .selected').dataset.kind, kind);
    app.click('#searchDialog .close');
    assert.equal(app.el('#searchDialog').open, false);
  }
  for (const screen of ['franchises', 'calendar', 'more']) {
    app.click(`[data-rail="${screen}"]`); assert.equal(app.evaluate('screen'), screen);
    assert.ok(app.el('#content').children.length);
  }
  for (const [route, marker] of [['setup', '#setupSettings'], ['privacy', '#privacyExport'], ['gaming', '#retroAssociate'], ['stats', '#exportStats'], ['history', '#historyKind'], ['maintenance', '#maintExport'], ['integrity', '#integrityRepair'], ['trakt-library', '#openTraktSettings']]) {
    await app.more(route); app.el(marker); app.click('#back');
  }
  app.assertClean();
});

integration('Search without keys uses public catalogues and Trakt does not claim a login', async t => {
  const app = await createApp(t);
  for (const [screen, kind, provider] of [['shows', 'show', 'api.tvmaze.com'], ['movies', 'movie', 'itunes.apple.com'], ['games', 'game', 'www.freetogame.com']]) {
    app.click(`#nav [data-screen="${screen}"]`); app.click(`[data-add-kind="${kind}"]`);
    app.el('#q').value = 'Recherche QA'; await app.evaluate('searchResults()');
    assert.ok(app.calls.some(c => new URL(c.url).hostname === provider), 'The public catalogue is queried without an API key');
    assert.match(app.text('#results'), /Aucun|aucun/i);
    assert.equal(app.data('remoteResults').length, 0);
    app.click('#searchDialog .close');
  }
  app.click('#settings');
  await app.el('#traktConnect').onclick();
  assert.match(app.text('#toast'), /Client ID|Client Secret|renseign|configur|activé.*éditeur/i);
  assert.equal(app.evaluate('traktConnected()'), false);
  assert.equal(app.calls.some(c => new URL(c.url).hostname.includes('trakt')), false);
  assert.equal(app.calls.some(c => /themoviedb|rawg/.test(c.url)), false);
  app.assertClean();
});

integration('Steam form keeps sign-in unconfirmed when the fixture server has no API key', async t => {
  const app = await createApp(t); await app.more('gaming');
  app.click('#steamSignIn');
  assert.match(app.text('#gamingDialog'), /steamcommunity\.com/);
  assert.equal(app.all('#gamingForm input[type="password"]').length, 0);
  await app.submit();
  assert.equal(app.el('#gamingDialog').open, true);
  assert.match(app.text('#gamingFormError'), /FG_STEAM_API_KEY/);
  assert.equal(app.el('#gamingSubmit').disabled, false);
  assert.equal(app.w.sessionStorage.getItem('franchiseguide-steam-login-intent-v1'), null);
  assert.equal(app.evaluate('steamConnection.authenticated'), false);
  assert.equal(app.data('gamingState().accounts').length, 0);
  assert.deepEqual(app.calls.filter(c => c.method === 'POST').map(c => c.path), ['/api/gaming/auth/steam/begin']);
  app.assertClean();
});

integration('Retro profile form exposes public association and missing server configuration honestly', async t => {
  const app = await createApp(t); app.click('#nav [data-screen="games"]'); app.click('#retroQuickConnect');
  assert.match(app.text('#gamingDialog'), /ne constitue pas une connexion/);
  assert.match(app.text('#retroConnectCard'), /n’authentifie pas/);
  assert.equal(app.all('#gamingForm input[type="password"]').length, 0);
  app.input('#gamingForm [name="user"]', 'FixtureUser');
  await app.submit();
  assert.match(app.text('#gamingFormError'), /FG_RETRO_API_KEY/);
  assert.equal(app.el('#gamingSubmit').disabled, false);
  assert.equal(app.el('#gamingDialog').open, true);
  assert.equal(app.data('gamingState().accounts').length, 0);
  assert.equal(app.calls.some(c => /retro\/(profile|library|achievements)/.test(c.path)), false);
  app.assertClean();
});

integration('Real catalogue search and add handlers retain all four media categories', async t => {
  const app = await createApp(t, { catalogue: true, storage: { 'franchiseguide-services-v1': JSON.stringify({ tmdbKey: 'fixture-tmdb', rawgKey: 'fixture-rawg' }) } });
  for (const [screen, kind] of [['shows', 'show'], ['movies', 'movie'], ['games', 'game'], ['books', 'book']]) {
    app.click(`#nav [data-screen="${screen}"]`); app.click(`[data-add-kind="${kind}"]`);
    app.el('#q').value = 'Fictif QA'; await app.evaluate('searchResults()');
    const before = app.data('state.media').length;
    app.click('#results [data-act="add"]');
    await app.waitFor(() => app.evaluate('state.media.length') === before + 1, 'complete catalogue add ' + kind);
    assert.equal(app.el('#searchDialog').open, false);
    assert.equal(app.evaluate('overlay.type'), 'item');
    assert.equal(app.evaluate('state.media[state.media.length - 1].kind'), kind);
  }
  assert.deepEqual(app.data('state.media.map(m=>m.kind)'), ['show', 'movie', 'game', 'book']);
  assert.equal(app.evaluate('state.episodes.length'), 2);
  assert.equal(app.evaluate('state.media[0].seasons.length'), 1);
  assert.equal(app.evaluate('state.episodes[0].catalogRating'), 0);
  app.evaluate("overlay={type:'item',id:state.media[0].id,src:'media',title:state.media[0].title};render();");
  assert.equal(app.el('.detail-poster img').getAttribute('src'), 'https://image.tmdb.org/t/p/w500/qa-series.png');
  assert.ok(app.all('img[src*="qa-cast"]').length > 0);
  app.click('[data-ep="watch"]');
  assert.equal(app.evaluate('state.episodes.filter(e=>e.watched).length'), 1);
  assert.equal(JSON.parse(app.w.localStorage.getItem(KEY)).episodes.filter(e => e.watched).length, 1);
  app.assertClean();
});

integration('Manual game, personal list and note/checklist form persist without platform unlocks', async t => {
  const app = await createApp(t); app.click('#nav [data-screen="games"]'); app.click('#newManualGame');
  app.input('#gamingForm [name="title"]', 'Jeu manuel fictif QA');
  app.input('#gamingForm [name="platform"]', 'PC'); await app.submit();
  app.click('#openGameTrophies'); app.click('#newAchievementSet'); await app.submit();
  app.click('#addManualAchievement');
  app.input('#gamingForm [name="title"]', 'Objectif personnel fictif');
  await app.submit();
  app.click('[data-ach-note]');
  app.change('#gamingForm [name="done"]', true);
  app.input('#gamingForm [name="note"]', 'Plan personnel <sans HTML>');
  await app.submit();
  const achievement = app.data('gamingState().sets[0].achievements[0]');
  assert.equal(achievement.localDone, true); assert.equal(achievement.earned, null); assert.equal(achievement.hardcoreEarned, null);
  assert.equal(achievement.note, 'Plan personnel <sans HTML>');
  assert.match(app.text('.achievement-note'), /<sans HTML>/);
  assert.equal(app.all('.achievement-note sans').length, 0);
  assert.match(app.text('.local-check'), /distinct du compte/);
  assert.equal(app.evaluate('state.media[0].watched'), false);
  assert.deepEqual(app.data('syncState.queue'), []);
  assert.equal(app.calls.some(c => c.method !== 'GET'), false);
  app.assertClean();
});

integration('Retro Hardcore filters, secret reveal, pin and note controls use distinct persisted states', async t => {
  const app = await createApp(t, { state: retroState() }); await app.more('gaming');
  app.click('[data-gaming-game="retro-game"]');
  app.el('#linkRetroGame');
  assert.match(app.text('#achievementSetBody'), /Mode normal : 2 \/ 4 · Hardcore : 1 \/ 4/);
  assert.equal(app.all('.achievement-card').length, 4);
  assert.match(app.text('[data-achievement="secret"]'), /Succès secret/);
  assert.doesNotMatch(app.text('[data-achievement="secret"]'), /Secret QA caché/);
  for (const [filter, expected] of [['hardcore-earned', ['hardcore']], ['hardcore-remaining', ['normal', 'unknown', 'secret']], ['hardcore-unknown', ['unknown']]]) {
    app.change('#achievementState', filter);
    assert.deepEqual(app.all('.achievement-card').map(e => e.dataset.achievement), expected);
  }
  app.change('#achievementState', 'all');
  app.input('#achievementQuery', 'Secret QA caché'); assert.equal(app.all('.achievement-card').length, 0);
  app.input('#achievementQuery', ''); app.click('[data-ach-reveal="secret"]');
  assert.match(app.text('[data-achievement="secret"]'), /Secret QA caché/);
  app.input('#achievementQuery', 'Secret QA caché'); assert.equal(app.all('.achievement-card').length, 1);
  app.input('#achievementQuery', ''); app.click('[data-ach-pin="unknown"]');
  app.change('#achievementState', 'pinned'); assert.deepEqual(app.all('.achievement-card').map(e => e.dataset.achievement), ['unknown']);
  app.click('[data-ach-note="unknown"]');
  app.change('#gamingForm [name="done"]', true); app.input('#gamingForm [name="note"]', 'Mon plan Hardcore reste personnel');
  await app.submit();
  const row = app.data('gamingState().sets[0].achievements.find(a=>a.id==="unknown")');
  assert.equal(row.earned, null); assert.equal(row.hardcoreEarned, null); assert.equal(row.localDone, true); assert.equal(row.pinned, true);
  assert.equal(JSON.parse(app.w.localStorage.getItem(KEY)).gaming.sets[0].achievements.find(a => a.id === 'unknown').note, row.note);
  assert.equal(app.evaluate('state.media[0].watched'), false);
  app.assertClean();
});

integration('Portable UI export and file import retain tracking and suspend queued Trakt/gaming automation', async t => {
  const app = await createApp(t, { state: retroState(), storage: { 'franchiseguide-services-v1': JSON.stringify({ tmdbKey: 'fixture-secret', traktSecret: 'fixture-trakt-secret' }), 'franchiseguide-trakt-auth-v1': JSON.stringify({ accessToken: 'fixture-bearer', username: 'fixture-user' }) } });
  app.evaluate("gamingState().accounts[0].autoImport=true;gamingState().accounts[0].autoSync=true;save(false);");
  app.click('#nav [data-screen="more"]'); app.click('#export');
  const portable = await app.downloaded();
  assert.equal(portable.schema, 11);
  assert.equal(portable.gaming.accounts[0].externalId, ULID);
  assert.equal(portable.gaming.sets[0].achievements[1].hardcoreEarned, true);
  assert.equal(portable.media[1].watchHistory.length, 1);
  assert.equal(JSON.stringify(portable).includes('fixture-secret'), false);
  assert.equal(JSON.stringify(portable).includes('fixture-trakt-secret'), false);
  assert.equal(JSON.stringify(portable).includes('fixture-bearer'), false);
  app.evaluate("syncState.queue=[{id:'queued-before-import',title:'Film fictif QA',field:'history',at:'2025-01-01T00:00:00Z'}];persistEnvelope();");
  await app.imported(portable);
  assert.equal(app.evaluate('syncState.queue[0].held'), undefined, 'Preparing the preview has no side effect');
  await app.confirmImportReview();
  assert.match(app.text('#toast'), /Import vérifié enregistré/);
  assert.equal(app.evaluate('gamingState().accounts[0].autoImport'), false);
  assert.equal(app.evaluate('gamingState().accounts[0].autoSync'), false);
  assert.equal(app.evaluate('syncState.queue[0].held'), 'Remplacement par import vérifié');
  const saved = JSON.parse(app.w.localStorage.getItem(KEY));
  assert.equal(saved._syncState.queue[0].id, 'queued-before-import');
  assert.equal(saved._syncState.queue[0].held, 'Remplacement par import vérifié');
  assert.equal(saved.gaming.sets[0].achievements[1].hardcoreEarnedAt, 1700000000000);
  assert.equal(app.calls.some(c => c.method !== 'GET'), false);
  app.assertClean();
});

integration('Snapshot restore button restores the saved library and holds pre-existing remote actions', async t => {
  const app = await createApp(t, { state: retroState() });
  app.click('#nav [data-screen="more"]'); app.click('#backupNow');
  assert.equal(app.evaluate('backups().length'), 1);
  app.evaluate("state.media[0].title='Changement après copie';syncState.queue=[{id:'held-after-restore',title:'Film fictif QA',field:'history',at:'2025-01-01T00:00:00Z'}];save(false);render();");
  app.click('[data-backup="0"]');
  assert.equal(app.evaluate('state.media[0].title'), 'Changement après copie', 'Preview does not restore data before confirmation');
  await app.confirmImportReview();
  assert.equal(app.evaluate('state.media[0].title'), 'Jeu rétro fictif QA');
  assert.equal(app.evaluate('syncState.queue[0].held'), 'Restauration locale vérifiée');
  assert.equal(app.evaluate('backups().length'), 2);
  assert.equal(JSON.parse(app.w.localStorage.getItem(KEY))._syncState.queue[0].held, 'Restauration locale vérifiée');
  assert.equal(app.calls.some(c => c.method !== 'GET'), false);
  app.assertClean();
});

integration('Maintenance UI creates, exports, restores and deletes simulated IndexedDB copies', async t => {
  const app = await createApp(t, { state: retroState() }); await app.more('maintenance');
  assert.match(app.text('#storageFacts'), /Contrôleur non actif/);
  await app.el('#maintDurable').onclick();
  assert.equal((await app.evaluate('FGDurable.list()')).length, 1);
  app.click('[data-durable-export]');
  const exported = await app.downloaded();
  assert.equal(exported.media[0].title, 'Jeu rétro fictif QA');
  app.evaluate("state.media[0].title='Titre modifié après copie durable';save(false);");
  await app.el('[data-durable-restore]').onclick();
  assert.equal(app.evaluate('state.media[0].title'), 'Titre modifié après copie durable', 'Durable restore also requires confirmation');
  await app.confirmImportReview();
  await app.more('maintenance');
  await app.waitFor(() => app.w.document.querySelector('[data-durable-delete]'), 'durable controls after restore');
  assert.equal(app.evaluate('state.media[0].title'), 'Jeu rétro fictif QA');
  await app.el('[data-durable-delete]').onclick();
  assert.equal((await app.evaluate('FGDurable.list()')).length, 0);
  assert.match(app.text('#durableSnapshots'), /Aucune copie durable/);
  app.assertClean();
});

integration('Maintenance reports denied persistence and clears only the fixture image cache', async t => {
  const app = await createApp(t, { state: retroState() }); await app.more('maintenance');
  const before = app.w.localStorage.getItem(KEY);
  await app.el('#persistStorage').onclick();
  assert.match(app.text('#maintStatus'), /non accordée/);
  await app.el('#clearPosters').onclick();
  await app.waitFor(() => app.w.document.querySelector('#storageFacts .facts'), 'cache facts after deletion');
  assert.equal(app.cacheRows.has('franchiseguide-images-fixture'), false);
  assert.equal(app.cacheRows.has('franchiseguide-web-fixture'), true);
  assert.equal(app.cacheRows.has('unrelated-cache'), true);
  assert.equal(app.w.localStorage.getItem(KEY), before);
  app.assertClean();
});

integration('Integrity UI repairs safe list references and retains tracked orphan episodes', async t => {
  const app = await createApp(t, { state: baseState({ media: [{ id: 'film', kind: 'movie', title: 'Film intégrité fictif', added: true, source: 'manual', listIds: ['missing', 'later', 'later'] }] }) });
  // Fault injection after a valid boot: the importer correctly refuses orphans.
  // This exercises the in-session repair UI, not acceptance of an invalid backup.
  app.evaluate("state.episodes=[{id:'untracked',showId:'missing-show',season:1,num:1,watched:false,plays:0},{id:'tracked',showId:'missing-show',season:1,num:2,watched:true,plays:1,lastWatchedAt:1700000000000}];savedState=clone(state);");
  await app.more('integrity'); assert.equal(app.el('#integrityRepair').disabled, false);
  app.click('#integrityExport');
  const before = await app.downloaded(); assert.ok(before.counts.repairable > 0);
  app.click('#integrityRepair');
  assert.deepEqual(app.data('state.media[0].listIds'), ['later']);
  assert.deepEqual(app.data('state.episodes.map(e=>e.id)'), ['tracked']);
  assert.equal(app.evaluate('state.episodes[0].watched'), true);
  assert.equal(app.evaluate('backups().length'), 1);
  assert.equal(app.el('#integrityRepair').disabled, true);
  app.assertClean();
});

integration('Quota failure retains the previous envelope and exposes a durable draft export', async t => {
  const app = await createApp(t, { state: retroState() });
  const before = app.w.localStorage.getItem(KEY), originalSet = app.w.Storage.prototype.setItem;
  app.w.Storage.prototype.setItem = function (key, value) {
    if (key === KEY) throw new app.w.DOMException('Fixture quota exhausted', 'QuotaExceededError');
    return originalSet.call(this, key, value);
  };
  app.evaluate("state.media[0].title='Copie de travail non enregistrée';try{save(false);}catch{}");
  await app.waitFor(() => app.evaluate('storageDraft !== null'), 'storage draft');
  assert.equal(app.w.localStorage.getItem(KEY), before);
  assert.equal(app.evaluate('state.media[0].title'), 'Jeu rétro fictif QA');
  app.click('#exportDraft');
  assert.equal((await app.downloaded()).media[0].title, 'Copie de travail non enregistrée');
  const rows = await app.evaluate('FGDurable.list()');
  assert.equal(rows.length, 1);
  assert.equal(rows[0].state.media[0].title, 'Copie de travail non enregistrée');
  app.assertClean();
});

integration('Future-schema startup preserves exact raw bytes and blocks edits behind recovery UI', async t => {
  const raw = '  {"schema":999,"media":[],"privateFixture":"keep exact bytes"}\n';
  const app = await createApp(t, { rawStorage: raw });
  assert.equal(app.w.localStorage.getItem(KEY), raw);
  assert.equal(app.evaluate('storageWritable'), false);
  assert.ok(app.evaluate('recoveryOnly'));
  app.click('#exportOriginal');
  assert.equal(await app.downloads.at(-1).blob.text(), raw);
  app.click('#nav [data-screen="games"]'); app.click('#newManualGame');
  app.input('#gamingForm [name="title"]', 'Ne pas enregistrer'); await app.submit();
  assert.match(app.text('#gamingFormError'), /récup|prise en charge/i);
  assert.equal(app.w.localStorage.getItem(KEY), raw);
  assert.equal(app.evaluate('state.media.length'), 0);
  assert.deepEqual(app.calls.map(c => [c.method, c.path]), [['GET', '/api/services/config']], 'Only the capability probe triggered by the user navigation is allowed');
  app.assertClean();
});

integration('Privacy reports partial erasure when IndexedDB fails and offers a reload to resume', async t => {
  const app = await createApp(t, { state: retroState(), storage: { 'unrelated-setting': 'keep-me' } });
  await app.evaluate("FGDurable.put(toPortableBackup(state),'Copie avant effacement')");
  app.evaluate("FGDurable.clear=async()=>{throw Error('Transaction IndexedDB refusée par la fixture');};");
  await app.more('privacy');app.input('#privacyConfirm','EFFACER');await app.el('#privacyErase').onclick();
  assert.match(app.text('#privacyStatus'), /Effacement incomplet/);
  assert.match(app.text('#privacyStatus'), /déjà été effacés/);
  assert.match(app.text('#privacyStatus'), /IndexedDB.*échoué/);
  assert.doesNotMatch(app.text('#privacyStatus'), /Données locales effacées\./);
  assert.equal(app.w.localStorage.getItem(KEY),null);
  assert.equal(app.w.localStorage.getItem('unrelated-setting'),'keep-me');
  assert.equal((await app.evaluate('FGDurable.list()')).length,1);
  assert.equal(app.cacheRows.has('franchiseguide-images-fixture'),false);
  assert.equal(app.cacheRows.has('unrelated-cache'),true);
  assert.equal(app.evaluate('storageWritable'),false);
  assert.equal(app.el('#privacyErase').disabled,true);
  assert.match(app.text('#privacyEraseReload'),/Recharger.*reprendre/);
  assert.equal(typeof app.el('#privacyEraseReload').onclick,'function');
  assert.equal(app.calls.some(c=>c.method!=='GET'),false);
  app.assertClean();
});

integration('Privacy waits for IndexedDB deletion before confirming complete local erasure', async t => {
  const app = await createApp(t, { state: retroState(), storage: { 'unrelated-setting': 'keep-me' } });
  await app.evaluate("FGDurable.put(toPortableBackup(state),'Copie avant effacement')");
  app.evaluate("window.qaClearStarted=false;window.qaClearGate=new Promise(resolve=>{window.qaAllowClear=resolve;});window.qaOriginalClear=FGDurable.clear;FGDurable.clear=async()=>{window.qaClearStarted=true;await window.qaClearGate;await window.qaOriginalClear();};");
  await app.more('privacy');app.input('#privacyConfirm','EFFACER');const erasing=app.el('#privacyErase').onclick();
  await app.waitFor(()=>app.w.qaClearStarted,'pending IndexedDB erase');
  assert.match(app.text('#privacyStatus'),/Effacement en cours/);assert.equal(app.el('#privacyErase').disabled,true);
  assert.equal((await app.evaluate('FGDurable.list()')).length,1);
  app.w.qaAllowClear();await erasing;
  assert.match(app.text('#privacyStatus'),/^Données locales effacées\./);
  assert.equal((await app.evaluate('FGDurable.list()')).length,0);
  assert.equal(app.w.localStorage.getItem(KEY),null);
  assert.equal(app.w.localStorage.getItem('unrelated-setting'),'keep-me');
  assert.equal(app.cacheRows.has('unrelated-cache'),true);
  assert.equal(app.w.document.querySelector('#privacyEraseReload'),null);
  assert.equal(app.calls.some(c=>c.method!=='GET'),false);app.assertClean();
});

integration('A fresh app context can finish deletion of an IndexedDB copy left by partial erasure', async t => {
  const first = await createApp(t, { state: retroState() });
  await first.evaluate("FGDurable.put(toPortableBackup(state),'Copie restante')");
  first.evaluate("FGDurable.clear=async()=>{throw Error('Échec contrôlé');};");
  await first.more('privacy');first.input('#privacyConfirm','EFFACER');await first.el('#privacyErase').onclick();
  assert.equal((await first.evaluate('FGDurable.list()')).length,1);
  const resumed = await createApp(t, { state:baseState(), indexedDB:first.w.indexedDB });
  assert.equal((await resumed.evaluate('FGDurable.list()')).length,1);
  await resumed.more('privacy');resumed.input('#privacyConfirm','EFFACER');await resumed.el('#privacyErase').onclick();
  assert.match(resumed.text('#privacyStatus'),/^Données locales effacées\./);
  assert.equal((await resumed.evaluate('FGDurable.list()')).length,0);
  first.assertClean();resumed.assertClean();
});

test.after(() => {
  const directory = path.join(__dirname, 'results', 'v014');
  fs.mkdirSync(directory, { recursive: true });
  fs.writeFileSync(path.join(directory, 'web-dom-results.json'), JSON.stringify({
    mode: 'Full production document/scripts in jsdom; fixture-only fetch; simulated IndexedDB and in-memory DOM storage; no real browser, account, network, image load, layout, CSP, cookie, OAuth or service-worker validation.',
    dependencies: { jsdom: dependencyVersion('jsdom'), fakeIndexedDB: dependencyVersion('fake-indexeddb') },
    scripts: SCRIPTS,
    passed: cases.filter(x => x.status === 'passed').length,
    failed: cases.filter(x => x.status === 'failed').length,
    cases
  }, null, 2) + '\n');
});
