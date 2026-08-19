import { readFile } from "node:fs/promises";

const contents = await readFile("third_party/dependency-register.toml", "utf8");
const entries = contents.split("[[dependency]]").slice(1);
const required = ["name", "version", "license", "source_url", "purpose", "risk_level", "used_by", "scope"];
const blocked = /(GPL|AGPL|LGPL|non-commercial|research-only|unknown)/i;

if (!entries.length) throw new Error("dependency register has no entries");
for (const entry of entries) {
  for (const field of required) {
    if (!new RegExp(`^${field}\\s*=`, "m").test(entry)) throw new Error(`dependency entry is missing ${field}`);
  }
  const license = entry.match(/^license\s*=\s*"([^"]+)"/m)?.[1] ?? "";
  if (blocked.test(license)) throw new Error(`blocked or unclear dependency license: ${license}`);
}
console.log(`Dependency register validation passed for ${entries.length} entries`);
