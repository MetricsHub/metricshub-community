import fs from "fs";
import { execSync } from "child_process";

const iso = new Date().toISOString();

const info = { buildTime: iso };
fs.writeFileSync("./src/build-info.json", JSON.stringify(info, null, 2));
console.log("Build info:", info);
