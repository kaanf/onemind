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

const isMoneyWord = (word) =>
  MONEY.has(word) || (word.endsWith('s') && MONEY.has(word.slice(0, -1))) || (word.endsWith('es') && MONEY.has(word.slice(0, -2)));

export const isMoneyName = (name) => {
  const words = tokens(name);
  return words.length > 0 && !NOT_MONEY.has(words[words.length - 1]) && words.some(isMoneyWord);
};

function MoneyMinorUnits() {
  // `from` is the file `raw` sits in, so a relative $ref inside another file resolves against that file.
  const check = (raw, from, ctx, location) => {
    const resolved = ctx.resolve(raw, from);
    const schema = resolved.node;
    if (!schema || typeof schema !== 'object') return;
    const source = resolved.location?.source.absoluteRef ?? from;
    if (schema.type === 'number') {
      ctx.report({ message: 'Money is the Money object, int64 minor units or a decimal string, never a JSON number (AGENTS.md rule 5).', location });
    } else if (schema.type === 'integer' && schema.format !== 'int64') {
      ctx.report({ message: 'Money in minor units is int64 (AGENTS.md rule 5).', location });
    } else if (schema.type === 'array' && schema.items) {
      check(schema.items, source, ctx, location.child(['items']));
    } else if (schema.additionalProperties && typeof schema.additionalProperties === 'object') {
      check(schema.additionalProperties, source, ctx, location.child(['additionalProperties']));
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
