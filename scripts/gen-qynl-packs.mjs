#!/usr/bin/env node
/**
 * Build both Qyn-L Clean BedWars packs.
 *
 * The vanilla jar is used only as a compatibility fallback. The BedWars
 * overlays below are generated from shapes/colors in this file, not copied
 * from another resource pack.
 *
 * Usage:
 *   bun scripts/gen-qynl-packs.mjs
 *   bun scripts/gen-qynl-packs.mjs path/to/1.8.9.jar path/to/1.21.1.jar
 */
import { deflateSync, inflateRawSync, inflateSync } from "node:zlib";
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { homedir } from "node:os";

const root = process.cwd();
const versions = [
  { id: "1.8.9", format: 1, jar: process.argv[2], out: join(root, "texturepacks", "QynL-Clean-BedWars-1.8.9") },
  { id: "1.21.1", format: 34, jar: process.argv[3], out: join(root, "texturepacks", "QynL-Clean-BedWars-1.21.1") },
];

function crc32(buf) {
  let c = 0xffffffff;
  for (const byte of buf) {
    c ^= byte;
    for (let i = 0; i < 8; i++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const t = Buffer.from(type, "latin1"), len = Buffer.alloc(4), crc = Buffer.alloc(4);
  len.writeUInt32BE(data.length); crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}
function encodePng(w, h, data) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(w, 0); header.writeUInt32BE(h, 4); header[8] = 8; header[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    Buffer.from(data.buffer, data.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  }
  return Buffer.concat([Buffer.from("\x89PNG\r\n\x1a\n", "latin1"), chunk("IHDR", header), chunk("IDAT", deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
function decodePng(buf) {
  if (buf.toString("latin1", 0, 8) !== "\x89PNG\r\n\x1a\n") throw new Error("not PNG");
  let p = 8, w = 0, h = 0, depth = 0, type = 0, palette = null, alpha = null;
  const parts = [];
  while (p < buf.length) {
    const n = buf.readUInt32BE(p), kind = buf.toString("latin1", p + 4, p + 8), d = buf.slice(p + 8, p + 8 + n);
    if (kind === "IHDR") { w = d.readUInt32BE(0); h = d.readUInt32BE(4); depth = d[8]; type = d[9]; }
    if (kind === "IDAT") parts.push(d);
    if (kind === "PLTE") palette = d;
    if (kind === "tRNS") alpha = d;
    p += n + 12;
    if (kind === "IEND") break;
  }
  if (![1, 2, 4, 8].includes(depth) || ![0, 2, 3, 4, 6].includes(type)) throw new Error(`unsupported PNG ${depth}/${type}`);
  const bpp = type === 0 || type === 3 ? 1 : type === 2 ? 3 : type === 4 ? 2 : 4;
  const stride = Math.ceil(w * (type === 0 || type === 3 ? depth : 8) * bpp / 8);
  const raw = Buffer.from(inflateSync(Buffer.concat(parts))), out = new Uint8Array(w * h * 4);
  let q = 0;
  for (let y = 0; y < h; y++) {
    const filter = raw[q++];
    for (let x = 0; x < stride; x++) {
      const i = q, left = x >= bpp ? raw[i - bpp] : 0, up = y ? raw[i - stride] : 0, ul = y && x >= bpp ? raw[i - stride - bpp] : 0;
      let v = raw[i];
      if (filter === 1) v = (v + left) & 255;
      else if (filter === 2) v = (v + up) & 255;
      else if (filter === 3) v = (v + ((left + up) >> 1)) & 255;
      else if (filter === 4) { const a = Math.abs(up - ul), b = Math.abs(left - ul), c = Math.abs(left + up - 2 * ul); v = (v + (a <= b && a <= c ? left : b <= c ? up : ul)) & 255; }
      raw[i] = v; q++;
    }
    const row = q - stride;
    for (let x = 0; x < w; x++) {
      const i = row + (type === 0 || type === 3 ? Math.floor(x * depth / 8) : x * bpp), o = (y * w + x) * 4;
      const packed = type === 0 || type === 3 ? (depth === 8 ? raw[i] : (raw[i] >> (8 - depth - ((x * depth) & 7))) & ((1 << depth) - 1)) : raw[i];
      if (type === 6) out.set(raw.slice(i, i + 4), o);
      else if (type === 2) { out[o] = raw[i]; out[o + 1] = raw[i + 1]; out[o + 2] = raw[i + 2]; out[o + 3] = 255; }
      else if (type === 4) { out[o] = raw[i]; out[o + 1] = raw[i]; out[o + 2] = raw[i]; out[o + 3] = raw[i + 1]; }
      else if (type === 0) out[o] = out[o + 1] = out[o + 2] = packed, out[o + 3] = 255;
      else { const k = packed * 3; out[o] = palette[k]; out[o + 1] = palette[k + 1]; out[o + 2] = palette[k + 2]; out[o + 3] = alpha?.[packed] ?? 255; }
    }
  }
  return { w, h, data: out };
}
function zipEntries(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) throw new Error("ZIP EOCD missing");
  const count = buf.readUInt16LE(eocd + 10), entries = new Map(); let off = buf.readUInt32LE(eocd + 16);
  for (let i = 0; i < count; i++) {
    const method = buf.readUInt16LE(off + 10), size = buf.readUInt32LE(off + 20), nl = buf.readUInt16LE(off + 28), el = buf.readUInt16LE(off + 30), cl = buf.readUInt16LE(off + 32), local = buf.readUInt32LE(off + 42);
    const name = buf.toString("utf8", off + 46, off + 46 + nl), start = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28), data = buf.slice(start, start + size);
    entries.set(name, method === 8 ? inflateRawSync(data) : data); off += 46 + nl + el + cl;
  }
  return entries;
}
async function vanillaJar(version, explicit) {
  const candidates = [explicit, join(root, ".cache", `${version}.jar`), join(homedir(), ".minecraft", "versions", version, `${version}.jar`)].filter(Boolean);
  for (const p of candidates) if (existsSync(p)) return p;
  const manifest = await fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").then((r) => r.json());
  const item = manifest.versions.find((v) => v.id === version); if (!item) throw new Error(`Minecraft ${version} missing from manifest`);
  const meta = await fetch(item.url).then((r) => r.json()), data = Buffer.from(await (await fetch(meta.downloads.client.url)).arrayBuffer());
  const p = join(root, ".cache", `${version}.jar`); mkdirSync(dirname(p), { recursive: true }); writeFileSync(p, data); return p;
}
function im(w = 16, h = 16) { return { w, h, data: new Uint8Array(w * h * 4) }; }
function px(a, x, y, c) { if (x < 0 || y < 0 || x >= a.w || y >= a.h) return; const i = (y * a.w + x) * 4; a.data[i] = c[0]; a.data[i + 1] = c[1]; a.data[i + 2] = c[2]; a.data[i + 3] = c[3] ?? 255; }
function fill(a, x, y, w, h, c) { for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) px(a, xx, yy, c); }
function shade(c, n) { return [Math.min(255, c[0] * n | 0), Math.min(255, c[1] * n | 0), Math.min(255, c[2] * n | 0), c[3] ?? 255]; }
function avg(a) { let r = 0, g = 0, b = 0, n = 0; for (let i = 0; i < a.data.length; i += 4) if (a.data[i + 3] > 180) r += a.data[i], g += a.data[i + 1], b += a.data[i + 2], n++; return n ? [r / n | 0, g / n | 0, b / n | 0] : [128, 128, 128]; }
function copy(a) { return { w: a.w, h: a.h, data: a.data.slice() }; }
function resizeCanvas(a, w, h) { const b = im(w, h); for (let y = 0; y < Math.min(h, a.h); y++) for (let x = 0; x < Math.min(w, a.w); x++) px(b, x, y, [a.data[(y * a.w + x) * 4], a.data[(y * a.w + x) * 4 + 1], a.data[(y * a.w + x) * 4 + 2], a.data[(y * a.w + x) * 4 + 3]]); return b; }
function flat(a, c) { const b = im(a.w, a.h); fill(b, 0, 0, b.w, b.h, c); fill(b, 0, b.h - 1, b.w, 1, shade(c, .82)); return b; }
function weave(a, c) { const b = flat(a, c); for (let y = 0; y < b.h; y++) for (let x = 0; x < b.w; x++) if ((x + y * 3) % 9 === 0) px(b, x, y, shade(c, .9)); for (let x = 1; x < b.w; x += 5) px(b, x, 1, shade(c, 1.08)); return b; }
function glass(a) { const c = avg(a), b = im(a.w, a.h); fill(b, 0, 0, b.w, b.h, [c[0], c[1], c[2], 18]); for (let x = 0; x < b.w; x++) px(b, x, 0, [225, 245, 255, 235]), px(b, x, b.h - 1, [170, 215, 235, 210]); for (let y = 0; y < b.h; y++) px(b, 0, y, [225, 245, 255, 235]), px(b, b.w - 1, y, [170, 215, 235, 210]); return b; }
function bed(a, red = [205, 55, 60]) { const b = flat(a, red); fill(b, 0, 0, b.w, Math.max(2, b.h >> 3), [242, 242, 242]); fill(b, 0, 0, Math.max(2, b.w >> 3), b.h, [242, 242, 242]); return b; }
function tnt(a, top = false) { const b = flat(a, [200, 48, 52]); if (top) fill(b, 4, 4, Math.max(2, b.w - 8), Math.max(2, b.h - 8), [243, 243, 243]); else { const y = Math.max(1, b.h >> 2); fill(b, 0, y, b.w, Math.max(2, b.h >> 4), [243, 243, 243]); } return b; }
function modernStone(a, name) { const c = avg(a), b = flat(a, c); if (!/smooth|polished/.test(name)) for (let x = 2; x < b.w; x += 5) fill(b, x, 0, 1, b.h, shade(c, .88)); return b; }
function renderBlock(name, a) {
  if (/(wool|concrete_powder|concrete)$/.test(name) || name.endsWith("_wool")) return weave(a, avg(a));
  if (/(stained_glass|glass_pane|^glass$)/.test(name)) return glass(a);
  if (/(terracotta|hardened_clay)/.test(name) && !name.includes("glazed")) return flat(a, avg(a));
  if (/^bed_|_bed_/.test(name)) return bed(a);
  if (/^tnt_(side|bottom)$/.test(name)) return tnt(a);
  if (name === "tnt_top") return tnt(a, true);
  if (/(^stone$|stone$|deepslate|blackstone|cobblestone|andesite|diorite|granite)/.test(name)) return modernStone(a, name);
  if (/^(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|crimson|warped).*planks/.test(name)) return modernStone(a, name);
  if (/(^diamond_block$|^emerald_block$|^gold_block$|^iron_block$|^raw_.*_block$)/.test(name)) return flat(a, avg(a));
  return null;
}
function renderItem(name, a) {
  const tool = /(sword|pickaxe|axe|shovel|hoe)$/.test(name);
  if (tool) {
    const b = im(a.w, a.h);
    const material = name.includes("netherite") ? [72, 76, 88] : name.includes("diamond") ? [74, 237, 217] : name.includes("golden") ? [255, 222, 75] : name.includes("iron") ? [220, 226, 235] : name.includes("stone") ? [150, 155, 164] : [166, 112, 63];
    const handle = name.startsWith("wooden") ? [142, 91, 48] : [116, 78, 43];
    for (let y = 0; y < a.h; y++) for (let x = 0; x < a.w; x++) {
      const i = (y * a.w + x) * 4, alpha = a.data[i + 3];
      if (!alpha) continue;
      const isHandle = y > a.h * .57 && x > a.w * .22;
      const c = isHandle ? handle : material;
      px(b, x, y, [c[0], c[1], c[2], alpha]);
    }
    for (let y = 0; y < b.h; y++) for (let x = 0; x < b.w; x++) {
      const i = (y * b.w + x) * 4;
      if (!b.data[i + 3]) continue;
      const edge = [[x - 1, y], [x + 1, y], [x, y - 1], [x, y + 1]].some(([nx, ny]) => nx < 0 || ny < 0 || nx >= b.w || ny >= b.h || !b.data[(ny * b.w + nx) * 4 + 3]);
      if (edge) px(b, x, y, [cDark(material)[0], cDark(material)[1], cDark(material)[2], b.data[i + 3]]);
    }
    return b;
  }
  if (/^bow|^crossbow/.test(name)) {
    const b = copy(a), wood = [126, 79, 38];
    for (let y = 2; y < b.h - 2; y++) px(b, 3 + Math.abs(y - b.h / 2) / 3 | 0, y, [...wood, 255]);
    return b;
  }
  return null;
}
function cDark(c) { return shade(c, .52); }
function grayUi(a) { const b = copy(a); for (let i = 0; i < b.data.length; i += 4) { const l = (b.data[i] * 3 + b.data[i + 1] * 6 + b.data[i + 2]) / 10; b.data[i] = l * .56 | 0; b.data[i + 1] = l * .6 | 0; b.data[i + 2] = l * .68 | 0; } return b; }
function write(out, rel, a) { const p = join(out, rel); mkdirSync(dirname(p), { recursive: true }); writeFileSync(p, encodePng(a.w, a.h, a.data)); }
function readPng(entries, name) { const b = entries.get(name); return b ? decodePng(b) : null; }
function description(v) { return `Qyn-L Clean BedWars — original modern Bare-Bones-style textures for Minecraft ${v.id}`; }
function packIcon() {
  const b = im(128, 128); fill(b, 0, 0, 128, 128, [13, 16, 22]); fill(b, 7, 7, 114, 114, [21, 27, 36]);
  const cyan = [74, 237, 217];
  for (let y = 0; y < 128; y++) for (let x = 0; x < 128; x++) { const d = Math.hypot(x - 59, y - 58); if (d > 27 && d < 34) px(b, x, y, cyan); }
  fill(b, 63, 68, 28, 8, cyan); fill(b, 57, 76, 10, 9, cyan); fill(b, 49, 84, 12, 7, cyan); fill(b, 101, 24, 6, 6, [60, 82, 200]);
  return b;
}

const TEAM_NAMES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"];
function teamColor(name, fallback) {
  const colors = { white: [238, 241, 246], orange: [238, 126, 42], magenta: [207, 77, 184], light_blue: [84, 178, 229], yellow: [242, 207, 55], lime: [117, 205, 62], pink: [235, 112, 157], gray: [83, 91, 105], light_gray: [177, 185, 197], cyan: [48, 192, 190], purple: [137, 82, 211], blue: [61, 99, 218], brown: [143, 91, 57], green: [61, 170, 94], red: [215, 62, 71], black: [35, 39, 49] };
  return colors[name] || fallback;
}
function pvpWool(a, color) {
  const b = flat(a, color), dark = shade(color, .78), light = shade(color, 1.12);
  for (let y = 0; y < b.h; y++) for (let x = 0; x < b.w; x++) {
    if ((x + y * 2) % 7 === 0) px(b, x, y, dark);
    if ((x - y + b.w * 2) % 11 === 0) px(b, x, y, light);
  }
  fill(b, 0, 0, b.w, 1, light); fill(b, 0, b.h - 1, b.w, 1, dark);
  return b;
}
function pvpTerracotta(a, color) {
  const b = flat(a, color), dark = shade(color, .78), light = shade(color, 1.08);
  fill(b, 0, 0, b.w, 1, light); fill(b, 0, b.h - 2, b.w, 2, dark);
  for (let x = 3; x < b.w; x += 6) px(b, x, 3, light);
  for (let y = 5; y < b.h; y += 6) px(b, 2, y, dark);
  return b;
}
function swordMaterial(name) {
  if (name.includes("netherite")) return [73, 77, 91];
  if (name.includes("diamond")) return [74, 237, 217];
  if (name.includes("gold")) return [255, 218, 63];
  if (name.includes("iron")) return [218, 226, 238];
  if (name.includes("stone")) return [151, 158, 170];
  return [179, 119, 65];
}
function shortSword(a, material) {
  const b = im(a.w, a.h), sx = a.w / 16, sy = a.h / 16, edge = shade(material, .42), highlight = shade(material, 1.18), handle = [125, 78, 42];
  const put = (x, y, c, w = 1, h = 1) => fill(b, Math.round(x * sx), Math.round(y * sy), Math.max(1, Math.round(w * sx)), Math.max(1, Math.round(h * sy)), c);
  // Compact 9-pixel blade: readable in fights without occupying the whole screen.
  for (let i = 0; i < 6; i++) { put(4 + i, 3 + i, edge, 2, 2); put(5 + i, 3 + i, material, 1, 1); }
  put(8, 9, edge, 5, 1); put(8, 10, material, 2, 1);
  put(7, 11, handle, 2, 3); put(6, 13, edge, 4, 1); put(7, 12, [205, 155, 94], 1, 1);
  put(4, 3, highlight, 1, 1);
  return b;
}
function skySun(a) {
  const b = im(a.w, a.h), cx = a.w / 2, cy = a.h / 2, r = Math.min(a.w, a.h) * .36;
  for (let y = 0; y < a.h; y++) for (let x = 0; x < a.w; x++) { const d = Math.hypot(x - cx, y - cy); if (d <= r) px(b, x, y, d > r - 1 ? [255, 235, 132, 210] : [255, 220, 92, 255]); }
  return b;
}
function skyMoon(a) {
  const b = im(a.w, a.h), slots = 8, r = Math.min(a.w / slots, a.h) * .34;
  for (let slot = 0; slot < slots; slot++) { const cx = (slot + .5) * a.w / slots, cy = a.h / 2; for (let y = 0; y < a.h; y++) for (let x = Math.floor(slot * a.w / slots); x < Math.ceil((slot + 1) * a.w / slots); x++) { const d = Math.hypot(x - cx, y - cy); if (d <= r) { let c = [231, 239, 249, 255]; if (slot % 4 === 1 && x > cx) c = [23, 31, 48, 255]; if (slot % 4 === 2 && x < cx) c = [23, 31, 48, 255]; if (slot % 4 === 3 && x < cx + r * .55) c = [23, 31, 48, 255]; px(b, x, y, c); } } }
  return b;
}
function skyClouds(a) {
  const b = im(a.w, a.h), c = [226, 239, 250, 42];
  for (let y = Math.floor(a.h * .18); y < a.h; y += Math.max(8, Math.floor(a.h / 8))) { fill(b, 0, y, a.w, Math.max(2, Math.floor(a.h / 28)), c); if (y + 4 < a.h) fill(b, Math.floor(a.w * .18), y + 3, Math.floor(a.w * .3), Math.max(2, Math.floor(a.h / 32)), [238, 247, 255, 30]); }
  return b;
}
function rewrite(out, rel, transform) {
  const path = join(out, rel); if (!existsSync(path)) return false;
  const source = decodePng(readFileSync(path)); write(out, rel, transform(source)); return true;
}
function applyPackStyle(out, id) {
  const blockDir = id === "1.8.9" ? "blocks" : "block", itemDir = id === "1.8.9" ? "items" : "item";
  for (const name of TEAM_NAMES) {
    const legacyName = name === "light_gray" ? "silver" : name;
    rewrite(out, `assets/minecraft/textures/${blockDir}/${id === "1.8.9" ? `wool_colored_${legacyName}` : `${name}_wool`}.png`, (a) => pvpWool(a, teamColor(name, avg(a))));
    rewrite(out, `assets/minecraft/textures/${blockDir}/${id === "1.8.9" ? `hardened_clay_stained_${legacyName}` : `${name}_terracotta`}.png`, (a) => pvpTerracotta(a, teamColor(name, avg(a))));
  }
  const swords = id === "1.8.9" ? ["wood_sword", "stone_sword", "iron_sword", "gold_sword", "diamond_sword"] : ["wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword"];
  for (const name of swords) rewrite(out, `assets/minecraft/textures/${itemDir}/${name}.png`, (a) => shortSword(a, swordMaterial(name)));
  rewrite(out, "assets/minecraft/textures/environment/sun.png", skySun);
  rewrite(out, "assets/minecraft/textures/environment/moon_phases.png", skyMoon);
  rewrite(out, "assets/minecraft/textures/environment/clouds.png", skyClouds);
}

async function build(v) {
  const jar = await vanillaJar(v.id, v.jar);
  if (v.id === "1.8.9") {
    const legacy = join(root, "texturepacks", "QynL-BedWars");
    rmSync(legacy, { recursive: true, force: true });
    const result = (await import("node:child_process")).spawnSync(process.execPath, [join(root, "scripts", "gen-qynl-pack.mjs"), jar], { cwd: root, stdio: "inherit" });
    if (result.status !== 0) throw new Error("1.8.9 generator failed");
    rmSync(v.out, { recursive: true, force: true });
    renameSync(legacy, v.out);
    applyPackStyle(v.out, v.id);
    writeFileSync(join(v.out, "pack.mcmeta"), JSON.stringify({ pack: { pack_format: v.format, description: description(v) } }, null, 2));
    writeFileSync(join(v.out, "README.md"), `# Qyn-L Clean BedWars — Minecraft ${v.id}\n\nOriginal modern Bare-Bones-style BedWars/PvP pack generated by the Qyn-L pipeline. It keeps the clean 1.8.9 asset layout while adding readable team wool, low-clutter glass, custom tools, dark GUI and BedWars accents.\n\nInstall this folder in .minecraft/resourcepacks/. Regenerate both versions with bun scripts/gen-qynl-packs.mjs.\n`);
    write(v.out, "pack.png", packIcon());
    console.log(`[qynl-pack] ${v.id}: legacy high-detail overlay → ${v.out}`);
    return;
  }
  const entries = zipEntries(readFileSync(jar));
  rmSync(v.out, { recursive: true, force: true }); mkdirSync(v.out, { recursive: true });
  let blocks = 0, items = 0, gui = 0;
  for (const [name, data] of entries) {
    if (!name.startsWith("assets/minecraft/textures/") || !name.endsWith(".png")) continue;
    const isBlock = name.includes("/textures/block/") || name.includes("/textures/blocks/");
    const isItem = name.includes("/textures/item/") || name.includes("/textures/items/");
    const isGui = name.includes("/textures/gui/");
    const isBedEntity = name.includes("/textures/entity/bed/");
    const isEnvironment = name.includes("/textures/environment/");
    if (!isBlock && !isItem && !isGui && !isBedEntity && !isEnvironment) continue;
    let source; try { source = decodePng(data); } catch { continue; }
    const base = name.slice("assets/minecraft/textures/".length);
    let out = null;
    if (isBlock) { out = renderBlock(base.replace(/^blocks?\//, "").replace(/\.png$/, ""), source); if (out) blocks++; }
    else if (isItem) { out = renderItem(base.replace(/^items?\//, "").replace(/\.png$/, ""), source); if (out) items++; }
    else if (isBedEntity) { out = bed(source, base.includes("/red." ) ? [205, 55, 60] : avg(source)); blocks++; }
    else if (isGui && (base.includes("gui/container/") || /gui\/(widgets|icons|sprites\/hud\/hotbar|sprites\/hud\/crosshair)/.test(base))) { out = grayUi(source); gui++; }
    write(v.out, `assets/minecraft/textures/${base}`, out ?? source);
    const meta = entries.get(`${name}.mcmeta`); if (meta) { const mp = join(v.out, `assets/minecraft/textures/${base}.mcmeta`); mkdirSync(dirname(mp), { recursive: true }); writeFileSync(mp, meta); }
  }
  applyPackStyle(v.out, v.id);
  writeFileSync(join(v.out, "pack.mcmeta"), JSON.stringify({ pack: { pack_format: v.format, description: description(v) } }, null, 2));
  write(v.out, "pack.png", packIcon());
  writeFileSync(join(v.out, "README.md"), `# Qyn-L Clean BedWars — Minecraft ${v.id}\n\nOriginal, clean modern PvP textures with a Bare-Bones-inspired minimal style. Vanilla assets remain as a compatibility fallback; BedWars assets are custom-generated by scripts/gen-qynl-packs.mjs.\n\n## BedWars focus\n- readable team wool and terracotta with subtle weave/depth\n- low-clutter glass with visible edges\n- clean red bed and TNT accents\n- modernized tools, bows, HUD and container UI\n- version-correct asset paths and pack metadata\n\n## Install\nCopy this folder into the resourcepacks directory of Minecraft ${v.id}.\n\n## Regenerate\n    bun scripts/gen-qynl-packs.mjs\n`);
  writeFileSync(join(v.out, "LICENSE.txt"), "Original Qyn-L generated artwork. No third-party resource-pack assets are included.\n");
  console.log(`[qynl-pack] ${v.id}: ${blocks} block overlays, ${items} item overlays, ${gui} GUI overlays → ${v.out}`);
}

for (const v of versions) await build(v);
