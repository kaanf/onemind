// Redocly plugin for AGENTS.md rule 5: a money-named property or component (or the items or map
// values of one) is the Money object, int64 minor units or a decimal string (sub-minor prices,
// README#data-formats), never a JSON number. Names are matched by whole words from money-words.json, exactly like backend
// architecture/ProductRules. Proven by money-as-number.yaml (CI, api job).
import { readFileSync } from 'node:fs';

const { moneyWords, notMoneySuffixes } = JSON.parse(readFileSync(new URL('./money-words.json', import.meta.url), 'utf8'));
const MONEY = new Set(moneyWords);
const NOT_MONEY = new Set(notMoneySuffixes);

const tokens = (name) =>
  name
    .split(/[_$-]/)
    .flatMap((part) => part.split(/(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])/))
    .filter((word) => word.length > 0)
    .map((word) => word.toLowerCase());

// A word or its plural (s/es): `tips`, `losses`, `tipRates`.
const inWords = (set, word) =>
  set.has(word) || (word.endsWith('s') && set.has(word.slice(0, -1))) || (word.endsWith('es') && set.has(word.slice(0, -2)));

export const isMoneyName = (name) => {
  const words = tokens(name);
  return words.length > 0 && !inWords(NOT_MONEY, words[words.length - 1]) && words.some((word) => inWords(MONEY, word));
};

function MoneyMinorUnits() {
  // `from` is the file `raw` sits in, so a relative $ref inside another file resolves against that file.
  // `seen` holds the schemas on the current path, so a self-referencing array or map stops.
  const check = (raw, from, ctx, location, seen = new Set()) => {
    const resolved = ctx.resolve(raw, from);
    const schema = resolved.node;
    if (!schema || typeof schema !== 'object' || seen.has(schema)) return;
    seen.add(schema);
    const source = resolved.location?.source.absoluteRef ?? from;
    if (schema.type === 'number') {
      ctx.report({ message: 'Money is the Money object, int64 minor units or a decimal string, never a JSON number (AGENTS.md rule 5).', location });
    } else if (schema.type === 'integer' && schema.format !== 'int64') {
      ctx.report({ message: 'Money in minor units is int64 (AGENTS.md rule 5).', location });
    } else if (schema.type === 'array' && schema.items) {
      check(schema.items, source, ctx, location.child(['items']), seen);
    } else if (schema.additionalProperties && typeof schema.additionalProperties === 'object') {
      check(schema.additionalProperties, source, ctx, location.child(['additionalProperties']), seen);
    }
  };
  const checkMoneyNamed = (schemas, ctx) => {
    for (const [key, schema] of Object.entries(schemas)) {
      if (isMoneyName(key)) check(schema, ctx.location.source.absoluteRef, ctx, ctx.location.child([key]));
    }
  };
  return {
    // Money-named properties, and money-named components (`Price`), which may be used under any
    // property name or directly as a body.
    SchemaProperties: { enter: checkMoneyNamed },
    NamedSchemas: { enter: checkMoneyNamed },
  };
}

export default function moneyPlugin() {
  return { id: 'onemind', rules: { oas3: { 'money-minor-units': MoneyMinorUnits } } };
}
