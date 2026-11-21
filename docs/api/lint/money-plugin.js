// Redocly plugin for AGENTS.md rule 5: a money-named property (or the items of a money-named array)
// is the Money object, int64 minor units or a decimal string (sub-minor prices, README#data-formats),
// never a JSON number. Names are matched by whole words from money-words.json, exactly like backend
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
  const check = (raw, ctx, location) => {
    const schema = ctx.resolve(raw).node;
    if (!schema || typeof schema !== 'object') return;
    if (schema.type === 'number') {
      ctx.report({ message: 'Money is the Money object, int64 minor units or a decimal string, never a JSON number (AGENTS.md rule 5).', location });
    } else if (schema.type === 'integer' && schema.format !== 'int64') {
      ctx.report({ message: 'Money in minor units is int64 (AGENTS.md rule 5).', location });
    } else if (schema.type === 'array' && schema.items) {
      check(schema.items, ctx, location.child(['items']));
    }
  };
  return {
    SchemaProperties: {
      enter(properties, ctx) {
        for (const [key, schema] of Object.entries(properties)) {
          if (isMoneyName(key)) check(schema, ctx, ctx.location.child([key]));
        }
      },
    },
  };
}

export default function moneyPlugin() {
  return { id: 'onemind', rules: { oas3: { 'money-minor-units': MoneyMinorUnits } } };
}
