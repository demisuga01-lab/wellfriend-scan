import { cp, mkdir } from "node:fs/promises";
await mkdir("dist/web/public", { recursive: true });
await cp("web/public", "dist/web/public", { recursive: true });
