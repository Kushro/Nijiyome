import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Two guards over every file git knows about or would add (tracked + untracked, minus ignored):
//  1. No crash-reporting or telemetry SDKs, and no host of the origin project's old backend.
//  2. Identity: the previous name of the project appears only where it is deliberate (provenance, the legacy
//     names the extension host keeps for existing .kai bundles, and the migration tooling).
// Patterns are assembled from parts so this file does not match itself.
const OLD = 'tata' + 'kai';
const FORBIDDEN = new RegExp([OLD + '\\.me', OLD + 'api', 'sen' + 'try', 'data' + 'dog', 'analy' + 'tics'].join('|'), 'i');
const OLD_NAME = new RegExp(OLD, 'i');

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const MAX_BYTES = 3 * 1024 * 1024;

// Vendored third-party code is never rewritten, so it is not scanned. Listed one by one on purpose:
// a new directory that happens to be called third_party is scanned like everything else.
const VENDORED = [
  'native/temple-lsfg/third_party/',
  'core/framegen/src/main/cpp/third_party/',
  'tokens/vendor/',
];

// Whole files where the old name is expected: they talk about where the project comes from.
const OLD_NAME_FILES = [
  /^docs\//,
  /^scripts\/migracion\//,
  /^README\.md$/,
  /^THIRD_PARTY_NOTICES\.md$/,
  /^contracts\/extension-host\/globals\.v1\.md$/,
];

// Code files where only the legacy extension names are allowed, line by line.
const LEGACY_LINE = new RegExp(`__${OLD}_[a-z_*]+__|${OLD}Id|${OLD}_\\$\\{name\\}`);
const LEGACY_LINE_FILES = new Set([
  'core/extensions-quickjs/src/commonMain/resources/nijiyome/host-prelude.js',
  'core/extensions-quickjs/src/commonMain/kotlin/app/nijiyome/extensions/QuickJsEngine.kt',
  'core/extensions-quickjs/src/commonTest/kotlin/app/nijiyome/extensions/LegacyGlobalsTest.kt',
  'core/extensions-quickjs/src/desktopTest/kotlin/app/nijiyome/extensions/QuickJsLegacyGlobalsTest.kt',
  'core/extensions/src/main/kotlin/app/nijiyome/extensions/ExtensionHost.kt',
  'contracts/extension-host/routes.v1.json',
]);

function candidateFiles() {
  const out = execFileSync('git', ['ls-files', '-co', '--exclude-standard', '-z'], {
    cwd: repo, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024,
  });
  const files = [];
  for (const rel of out.split('\0')) {
    if (!rel || VENDORED.some((prefix) => rel.startsWith(prefix))) continue;
    const full = join(repo, rel);
    let st;
    try { st = statSync(full); } catch { continue; } // deleted in the working tree
    if (!st.isFile()) continue;
    let lines = null; // null = binary or too large: only its path is checked
    if (st.size <= MAX_BYTES) {
      const buf = readFileSync(full);
      if (!buf.includes(0)) lines = buf.toString('utf8').split('\n');
    }
    files.push({ rel, lines });
  }
  return files;
}

const FILES = candidateFiles();

test('the scan covers the repository', () => {
  assert.ok(FILES.length > 500, `guard scanned suspiciously few files (${FILES.length})`);
});

test('no crash-reporting, telemetry or old-backend references', () => {
  const hits = [];
  for (const { rel, lines } of FILES) {
    if (rel === 'contracts/test/guard.test.mjs') continue;
    if (FORBIDDEN.test(rel)) hits.push(`${rel}: path`);
    (lines ?? []).forEach((line, i) => {
      if (FORBIDDEN.test(line)) hits.push(`${rel}:${i + 1}: ${line.trim().slice(0, 120)}`);
    });
  }
  assert.deepEqual(hits, []);
});

test('the previous project name appears only where it is allowed', () => {
  const hits = [];
  for (const { rel, lines } of FILES) {
    if (OLD_NAME.test(rel)) hits.push(`${rel}: path`);
    if (OLD_NAME_FILES.some((re) => re.test(rel))) continue;
    const legacyOnly = LEGACY_LINE_FILES.has(rel);
    (lines ?? []).forEach((line, i) => {
      if (!OLD_NAME.test(line)) return;
      if (legacyOnly && OLD_NAME.test(line.replace(new RegExp(LEGACY_LINE.source, 'g'), '')) === false) return;
      hits.push(`${rel}:${i + 1}: ${line.trim().slice(0, 120)}`);
    });
  }
  assert.deepEqual(hits, []);
});

test('the patterns really match (self-test)', () => {
  for (const sample of [`https://api.${OLD}.me/x`, `${OLD}API`.toUpperCase(), 'Sen' + 'try.init', 'DATA' + 'DOG', 'google-analy' + 'tics']) {
    assert.match(sample, FORBIDDEN);
  }
  assert.doesNotMatch('nijiyome-link:v1', FORBIDDEN);
  assert.match(`__${OLD}_fetch__`, OLD_NAME);
  assert.match(`g.__${OLD}_fetch__ = g.__nijiyome_fetch__;`, LEGACY_LINE);
  // a legacy-line file may not carry the old name anywhere else on the line
  const stray = `navigator.userAgent = '${OLD}'; g.__${OLD}_fetch__ = 1;`;
  assert.ok(OLD_NAME.test(stray.replace(new RegExp(LEGACY_LINE.source, 'g'), '')));
});
