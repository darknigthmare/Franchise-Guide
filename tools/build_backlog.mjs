import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';

// Run with the primary runtime from an OS temporary directory whose node_modules
// resolves @oai/artifact-tool. The source of truth is docs/backlog.json.
const repo = path.resolve(process.argv[2] || process.env.FG_REPO || process.cwd());
const outputDir = path.resolve(process.argv[3] || 'outputs/franchiseguide-backlog');
await fs.mkdir(outputDir, { recursive: true });
const backlog = JSON.parse(await fs.readFile(path.join(repo, 'docs/backlog.json'), 'utf8'));
const items = backlog.items;
const ids = new Set(items.map(item => item.id));
assert.equal(ids.size, items.length, 'Duplicate task IDs');
for (const item of items) {
  assert.ok(backlog.statusDefinitions[item.status], `Unknown status ${item.status}`);
  assert.ok(backlog.priorityDefinitions[item.priority], `Unknown priority ${item.priority}`);
  for (const dep of item.dependencies.split(',').map(s => s.trim()).filter(Boolean)) {
    assert.ok(ids.has(dep), `${item.id}: missing dependency ${dep}`);
  }
  for (const source of item.evidence.split(';').map(s => s.trim()).filter(Boolean)) {
    await fs.access(path.join(repo, source));
  }
}

const wb = Workbook.create();
const summary = wb.worksheets.add('Synthèse');
const detail = wb.worksheets.add('Travaux');
const palette = { ink: '#253247', header: '#4F3A73', light: '#F5F3F8', line: '#D9D5E2', amber: '#FFF3D6', muted: '#657080' };
const statuses = Object.keys(backlog.statusDefinitions);
const priorities = Object.keys(backlog.priorityDefinitions);
const domains = [...new Set(items.map(item => item.domain))];
const first = 8, last = first + items.length - 1, formulaEnd = 1000;
const c = (col) => `'Travaux'!$${col}$${first}:$${col}$${formulaEnd}`;
const reportDate = new Date(`${backlog.assessedAt}T12:00:00Z`);
const version = backlog.targetVersion || backlog.baselineVersion;

for (const sheet of [summary, detail]) {
  sheet.showGridLines = false;
  sheet.tabColor = sheet === summary ? palette.header : '#9E91B3';
}

const headers = ['ID', 'Priorité', 'Statut', 'Domaine', 'Plateforme', 'Chantier', 'État constaté', 'Travail à réaliser', 'Critères de recette', 'Dépendances', 'Validation de cette passe', 'Suivi', 'Preuves dans le dépôt'];
const widths = [12, 10, 18, 23, 17, 43, 62, 64, 64, 28, 58, 38, 66];
detail.getRange(`A1:M${last}`).format = { font: { name: 'Arial', size: 10, color: palette.ink }, verticalAlignment: 'center', rowHeight: 24 };
for (let i = 0; i < widths.length; i++) detail.getRangeByIndexes(0, i, last, 1).format.columnWidth = widths[i];
detail.getRange('A2').values = [['FranchiseGuide — travaux restants']];
detail.getRange('A2').format.font = { name: 'Arial', size: 16, bold: true, color: palette.header };
detail.getRange('A3').values = [['Situation au']];
detail.getRange('B3').values = [[reportDate]];
detail.getRange('B3').setNumberFormat('dd/mm/yyyy');
detail.getRange('D3').values = [[`Version ${version}`]];
detail.getRange('A4').values = [['Statut et Suivi sont modifiables. Filtrer par priorité, domaine ou plateforme pour préparer la prochaine passe.']];
detail.getRange('A5').values = [['« À valider » signifie que le code existe et qu’une preuve manque. Les rapports des anciennes versions restent historiques.']];
detail.getRange('A4:A5').format.font = { name: 'Arial', size: 10, italic: true, color: palette.muted };
detail.getRange('A6:M6').format.borders = { bottom: { style: 'thin', color: palette.line } };
detail.getRange('A7:M7').values = [headers];
const values = items.map(item => [item.id, item.priority, item.status, item.domain, item.platform, item.title, item.current, item.steps, item.acceptance, item.dependencies || 'Aucune', item.validation, item.followup || '', item.evidence]);
detail.getRange(`A${first}:M${last}`).values = values;
const table = detail.tables.add(`A7:M${last}`, true, 'TravauxFranchiseGuide');
table.showFilterButton = true;
table.style = 'TableStyleLight1';
detail.getRange('A7:M7').format = { fill: palette.header, font: { name: 'Arial', size: 10, bold: true, color: '#FFFFFF' }, horizontalAlignment: 'center', verticalAlignment: 'center', wrapText: true, rowHeight: 34 };
detail.getRange(`A${first}:M${last}`).format.wrapText = true;
detail.getRange(`A${first}:M${last}`).format.verticalAlignment = 'top';
detail.getRange(`A${first}:E${last}`).format.verticalAlignment = 'center';
detail.getRange(`A${first}:C${last}`).format.horizontalAlignment = 'center';
detail.getRange(`C${first}:C${last}`).format.fill = palette.amber;
detail.getRange(`L${first}:L${last}`).format.fill = '#FFF9E9';
detail.getRange(`C${first}:C${formulaEnd}`).dataValidation = { rule: { type: 'list', values: statuses } };
detail.getRange(`B${first}:B${formulaEnd}`).dataValidation = { rule: { type: 'list', values: priorities } };
detail.getRange(`D${first}:D${formulaEnd}`).dataValidation = { rule: { type: 'list', values: domains } };
const statusColors = { 'À développer': '#F0F1F4', 'Partiel': '#ECE6F5', 'À valider': '#FFF0CE', 'Terminé': '#E8F0EB' };
for (const [status, fill] of Object.entries(statusColors)) {
  detail.getRange(`C${first}:C${formulaEnd}`).conditionalFormats.add('cellIs', { operator: 'equal', formula: `"${status}"`, format: { fill, font: { color: palette.ink } } });
}
detail.getRange(`B${first}:B${formulaEnd}`).conditionalFormats.add('cellIs', { operator: 'equal', formula: '"P0"', format: { fill: '#FCE3E2', font: { color: '#9B2420', bold: true } } });
detail.getRange(`B${first}:B${formulaEnd}`).conditionalFormats.add('cellIs', { operator: 'equal', formula: '"P1"', format: { fill: '#FFF0D4', font: { color: '#7A4E00' } } });
function wrappedLines(value, width) {
  let lines = 1, used = 0;
  for (const word of String(value || '').split(/\s+/)) {
    if (used && used + word.length + 1 > width) { lines++; used = 0; }
    const total = used + (used ? 1 : 0) + word.length;
    lines += Math.floor(Math.max(0, total - 1) / width);
    used = total % width;
  }
  return lines;
}
values.forEach((row, i) => {
  const lines = Math.max(...row.map((value, col) => wrappedLines(value, Math.floor(widths[col] * .91))));
  detail.getRange(`A${first+i}:M${first+i}`).format.rowHeight = Math.max(48, lines * 13 + 10);
});
detail.freezePanes.freezeRows(7);
detail.freezePanes.freezeColumns(3);

summary.getRange('A1:J37').format = { font: { name: 'Arial', size: 10, color: palette.ink }, verticalAlignment: 'center', rowHeight: 23 };
const sw = [3, 33, 12, 12, 12, 3, 35, 14, 14, 3];
sw.forEach((width, i) => { summary.getRangeByIndexes(0, i, 37, 1).format.columnWidth = width; });
summary.getRange('B2').values = [['FranchiseGuide']];
summary.getRange('B2').format.font = { name: 'Arial', size: 16, bold: true, color: palette.header };
summary.getRange('B3').values = [[`Travaux restants — version ${version}`]];
summary.getRange('B3').format.font = { name: 'Arial', size: 14, color: palette.ink };
summary.getRange('B4').values = [['Situation au']];
summary.getRange('C4').values = [[reportDate]];
summary.getRange('C4').setNumberFormat('dd/mm/yyyy');
summary.getRange('B5:I5').format.borders = { bottom: { style: 'thin', color: palette.line } };
summary.getRange('B6:B9').values = [['Actions suivies'], ['Travaux ouverts'], ['Terminés dans cette passe'], ['Statut « À valider »']];
summary.getRange('C6:C9').formulas = [[`=COUNTA(${c('A')})`], [`=C6-C8`], [`=COUNTIFS(${c('C')},"Terminé")`], [`=COUNTIFS(${c('C')},"À valider")`]];
summary.getRange('C6:C9').setNumberFormat('#,##0');
summary.getRange('B7:C7').format = { fill: palette.light, font: { name: 'Arial', size: 10, bold: true, color: palette.ink } };
summary.getRange('G6:H6').values = [['Priorité', 'Ouverts']];
summary.getRange('G7:G9').values = priorities.map(p => [p]);
summary.getRange('H7:H9').formulas = priorities.map(p => [`=COUNTIFS(${c('B')},"${p}",${c('C')},"<>Terminé")`]);
summary.getRange('B12:E12').values = [['Domaine', 'Ouverts', 'Terminés', 'Total']];
domains.forEach((domain, i) => {
  const row = 13 + i;
  summary.getRange(`B${row}`).values = [[domain]];
  summary.getRange(`C${row}:E${row}`).formulas = [[`=COUNTIFS(${c('D')},B${row},${c('C')},"<>Terminé")`, `=COUNTIFS(${c('D')},B${row},${c('C')},"Terminé")`, `=SUM(C${row}:D${row})`]];
});
summary.getRange('G12:H12').values = [['État', 'Travaux']];
summary.getRange('G13:G16').values = statuses.map(status => [status]);
summary.getRange('H13:H16').formulas = statuses.map(status => [`=COUNTIFS(${c('C')},G${13+statuses.indexOf(status)})`]);
summary.getRange('G19').values = [['Ordre de travail']];
summary.getRange('G19').format.font = { name: 'Arial', size: 10, bold: true, color: palette.header };
summary.getRange('G20').values = [['P0 : données et blocages d’exécution']];
summary.getRange('G21').values = [['P1 : fonctions et distribution essentielles']];
summary.getRange('G22').values = [['P2 : extensions et améliorations']];
summary.getRange('G24').values = [['Le détail et les preuves sont dans Travaux.']];
summary.getRange('G25').values = [['Les compteurs suivent les statuts modifiés.']];
summary.getRange('G24:G25').format.font = { name: 'Arial', size: 10, italic: true, color: palette.muted };
for (const addr of ['B12:E12', 'G6:H6', 'G12:H12']) {
  summary.getRange(addr).format = { fill: palette.header, font: { name: 'Arial', size: 10, bold: true, color: '#FFFFFF' }, horizontalAlignment: 'center', rowHeight: 28 };
}
summary.getRange('C13:E25').setNumberFormat('#,##0');
summary.getRange('H7:H16').setNumberFormat('#,##0');
summary.getRange('B28').values = [['Priorités de sortie']];
summary.getRange('B28').format.font = { name: 'Arial', size: 14, bold: true, color: palette.header };
summary.getRange('B29').values = [['1. Qualifier Android sur appareil, les comptes réels et les parcours sur les plateformes cibles.']];
summary.getRange('B30').values = [['2. Fermer les risques de perte de données et de doublons de synchronisation.']];
summary.getRange('B31').values = [['3. Finaliser sauvegarde externe, distribution et synchronisation générale.']];
summary.getRange('B34').values = [['Ce registre suit des actions. Il ne mesure pas un pourcentage global d’achèvement du produit.']];
summary.getRange('B35').values = [['Base : archive Drive V0.35 récupérable. Les résultats historiques restent distincts de cette passe.']];
summary.getRange('B34:B35').format.font = { name: 'Arial', size: 10, italic: true, color: palette.muted };

// Assert that summary formulas react to actual status edits, then restore input.
wb.recalculate();
const total = items.length, done = items.filter(item => item.status === 'Terminé').length;
const baseMetrics = summary.getRange('C6:C9').values;
assert.equal(baseMetrics[0][0], total);
assert.equal(baseMetrics[1][0], total - done);
assert.equal(baseMetrics[2][0], done);
const mutable = items.findIndex(item => item.status !== 'Terminé');
if (mutable >= 0) {
  const target = detail.getRange(`C${first+mutable}`), original = items[mutable].status;
  target.values = [['Terminé']];
  wb.recalculate();
  assert.equal(summary.getRange('C7').values[0][0], total - done - 1);
  assert.equal(summary.getRange('C8').values[0][0], done + 1);
  target.values = [[original]];
}
wb.recalculate();
const formulaCheck = await wb.inspect({ kind: 'match', searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!', options: { useRegex: true, maxResults: 50 }, summary: 'final formula error scan', maxChars: 2500 });
const summaryCheck = await wb.inspect({ kind: 'table', range: 'Synthèse!B6:C9', include: 'values,formulas', tableMaxRows: 4, tableMaxCols: 2, maxChars: 2000 });
await fs.writeFile(path.join(outputDir, 'verification.json'), JSON.stringify({ total, done, open: total-done, statusMutationVerified: true, sourcePathsVerified: true, summary: summaryCheck.ndjson, errors: formulaCheck.ndjson }, null, 2));
console.log(JSON.stringify({ total, done, open: total-done, statusMutationVerified: true, errors: formulaCheck.ndjson }));
for (const [name, sheetName, range] of [['synthese', 'Synthèse', 'A1:J37'], ['travaux-identification', 'Travaux', 'A2:F13'], ['travaux-details', 'Travaux', 'F7:I12'], ['travaux-preuves', 'Travaux', 'J7:M12']]) {
  const preview = await wb.render({ sheetName, range, scale: 1.3, format: 'png' });
  await fs.writeFile(path.join(outputDir, `${name}.png`), new Uint8Array(await preview.arrayBuffer()));
}
const output = await SpreadsheetFile.exportXlsx(wb);
await output.save(path.join(outputDir, 'FranchiseGuide_Reste_a_faire.xlsx'));
console.log(`Workbook exported: ${path.join(outputDir, 'FranchiseGuide_Reste_a_faire.xlsx')}`);
