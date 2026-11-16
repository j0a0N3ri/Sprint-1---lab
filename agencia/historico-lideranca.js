const fs = require('node:fs');
const path = require('node:path');

const pasta = process.argv[2] || path.join(__dirname, 'data');
const tipos = new Set([
  'ELEICAO_INICIADA', 'ELEICAO_RECEBIDA', 'TORNOU_LIDER',
  'LIDER_ANUNCIADO', 'LIDER_NAO_RESPONDEU',
]);
const eventos = fs.existsSync(pasta)
  ? fs.readdirSync(pasta)
    .filter(arquivo => arquivo.endsWith('.jsonl'))
    .flatMap(arquivo => fs.readFileSync(path.join(pasta, arquivo), 'utf8')
      .split(/\r?\n/).filter(linha => linha.trim()).map(linha => JSON.parse(linha)))
    .filter(evento => tipos.has(evento.tipo))
    .sort((a, b) => a.horaParede.localeCompare(b.horaParede))
  : [];

console.log('=== Histórico de eventos de eleição ===');
for (const evento of eventos) {
  console.log(`${evento.horaParede} [${evento.agencia}] ${evento.tipo} ${JSON.stringify(evento.detalhes)}`);
}

console.log('\n=== Declarações de liderança em ordem de hora local ===');
for (const evento of eventos.filter(evento => evento.tipo === 'TORNOU_LIDER')) {
  console.log(`${evento.horaParede} → Agência ${evento.detalhes.idAgencia}`);
}
