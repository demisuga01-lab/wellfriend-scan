import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer } from "node:http";
import { extname, join, normalize } from "node:path";
const root = normalize(process.cwd() + "/dist");
const types = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".json": "application/json", ".wasm": "application/wasm" };
createServer(async (request, response) => { const pathname = request.url === "/" ? "web/public/index.html" : (request.url?.split("?")[0] ?? "").replace(/^[/\\]+/, ""); const file = normalize(join(root, pathname)); if (!file.startsWith(root)) { response.writeHead(403).end(); return; } try { if (!(await stat(file)).isFile()) throw new Error("not a file"); response.writeHead(200, { "content-type": types[extname(file)] ?? "application/octet-stream" }); createReadStream(file).pipe(response); } catch { response.writeHead(404).end("Not found"); } }).listen(4173, () => console.log("Wellfriend web scanner: http://localhost:4173"));
