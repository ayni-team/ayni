// Downloads the OpenAPI document of a running backend into docs/api/openapi.json.
// The backend must be up (docker compose up); AYNI_API_URL points elsewhere than localhost:8080.
import { writeFile } from 'node:fs/promises';

const base = process.env.AYNI_API_URL ?? 'http://localhost:8080';
const target = new URL('../../docs/api/openapi.json', import.meta.url);

const response = await fetch(`${base}/v3/api-docs`);
if (!response.ok) {
  console.error(`${base}/v3/api-docs answered ${response.status}`);
  process.exit(1);
}
const document = await response.json();
await writeFile(target, `${JSON.stringify(document, null, 2)}\n`);
console.log(`Saved ${Object.keys(document.paths).length} paths to docs/api/openapi.json`);
