import { existsSync, rmSync } from "node:fs";
import { resolve } from "node:path";

const nextDir = resolve(process.cwd(), ".next");

function removeWithRetry(target, attempts = 4) {
  let lastError = null;

  for (let index = 0; index < attempts; index += 1) {
    try {
      rmSync(target, { recursive: true, force: true });
      return;
    } catch (error) {
      lastError = error;
      if (error?.code !== "ENOTEMPTY" && error?.code !== "EBUSY") {
        throw error;
      }
    }
  }

  if (lastError) {
    throw lastError;
  }
}

if (existsSync(nextDir)) {
  removeWithRetry(nextDir);
  console.log(`Removed ${nextDir}`);
} else {
  console.log(`No .next cache found at ${nextDir}`);
}
