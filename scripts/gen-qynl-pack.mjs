#!/usr/bin/env node
/**
 * Qyn-L Clean — Bare-Bones-style BedWars PvP texture pack generator (1.8.9)
 *
 * Reads the vanilla 1.8.9 client jar and produces an ORIGINAL texture pack in
 * `texturepacks/QynL-BedWars/` with the Bare Bones aesthetic (flat, clean,
 * minimal noise) tuned for BedWars PvP:
 *   - every solid block flattened to a clean flat color (grass, wool, clay,
 *     glass, beds, TNT, planks, logs, ores, metal blocks, prismarine, ...)
 *   - near-invisible glass with a bright border (PvP standard)
 *   - hand-drawn clean items (tools, weapons, food, materials)
 *   - dark clean GUI (containers, hotbar, widgets, icons)
 *   - clean environment (sun, moon, clouds)
 * All pixels are generated here — nothing is copied from any resource pack.
 *
 * Usage: bun scripts/gen-qynl-pack.mjs [path-to-1.8.9.jar]
 */
import { deflateSync, inflateRawSync, inflateSync } from "node:zlib";
import { readFileSync, writeFileSync, mkdirSync, existsSync, rmSync } from "node:fs";
import { join, dirname } from "node:path";
import { homedir } from "node:os";

const OUT = join(process.cwd(), "texturepacks", "QynL-BedWars");
const VERSION_ID = "1.8.9";

// ──────────────────────────────────────────────────────────────── PNG ────────
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return (c ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const t = Buffer.from(type, "latin1");
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}
function decodePng(buf) {
  if (buf.slice(0, 8).toString("latin1") !== "\x89PNG\r\n\x1a\n") throw new Error("not a png");
  let pos = 8, w = 0, h = 0, bit = 0, ct = 0;
  const idat = [];
  let palette = null, trns = null;
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos);
    const type = buf.toString("latin1", pos + 4, pos + 8);
    const data = buf.slice(pos + 8, pos + 8 + len);
    if (type === "IHDR") { w = data.readUInt32BE(0); h = data.readUInt32BE(4); bit = data[8]; ct = data[9]; }
    else if (type === "IDAT") idat.push(data);
    else if (type === "PLTE") palette = data;
    else if (type === "tRNS") trns = data;
    else if (type === "IEND") break;
    pos += 12 + len;
  }
  if (bit !== 8) throw new Error(`unsupported bit depth ${bit}`);
  const bpp = ct === 0 || ct === 3 ? 1 : ct === 2 ? 3 : ct === 4 ? 2 : 4;
  const raw = Buffer.from(inflateSync(Buffer.concat(idat)));
  const stride = w * bpp;
  const out = new Uint8Array(w * h * 4);
  let p = 0;
  for (let y = 0; y < h; y++) {
    const f = raw[p++];
    for (let x = 0; x < stride; x++) {
      const i = p;
      const a = x >= bpp ? raw[i - bpp] : 0;
      const b = y > 0 ? raw[i - stride] : 0;
      const c = y > 0 && x >= bpp ? raw[i - stride - bpp] : 0;
      let v = raw[i];
      if (f === 1) v = (v + a) & 0xff;
      else if (f === 2) v = (v + b) & 0xff;
      else if (f === 3) v = (v + ((a + b) >> 1)) & 0xff;
      else if (f === 4) {
        const pa = Math.abs(b - c), pb = Math.abs(a - c), pc = Math.abs(a + b - 2 * c);
        v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 0xff;
      }
      raw[i] = v;
      p++;
    }
    const rowStart = p - stride;
    for (let x = 0; x < w; x++) {
      const o = rowStart + x * bpp, d = (y * w + x) * 4;
      if (ct === 6) { out[d] = raw[o]; out[d + 1] = raw[o + 1]; out[d + 2] = raw[o + 2]; out[d + 3] = raw[o + 3]; }
      else if (ct === 2) { out[d] = raw[o]; out[d + 1] = raw[o + 1]; out[d + 2] = raw[o + 2]; out[d + 3] = 255; }
      else if (ct === 0) { out[d] = raw[o]; out[d + 1] = raw[o]; out[d + 2] = raw[o]; out[d + 3] = 255; }
      else if (ct === 4) { out[d] = raw[o]; out[d + 1] = raw[o]; out[d + 2] = raw[o]; out[d + 3] = raw[o + 1]; }
      else if (ct === 3) {
        const idx = raw[o], po = idx * 3;
        out[d] = palette[po]; out[d + 1] = palette[po + 1]; out[d + 2] = palette[po + 2];
        out[d + 3] = trns && idx < trns.length ? trns[idx] : 255;
      }
    }
  }
  return { w, h, data: out };
}
function encodePng(w, h, data) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    Buffer.from(data.buffer, data.byteOffset + y * w * 4, w * 4).copy(raw, y * (w * 4 + 1) + 1);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk("IHDR", ihdr),
    pngChunk("IDAT", deflateSync(raw)),
    pngChunk("IEND", Buffer.alloc(0)),
  ]);
}

// ──────────────────────────────────────────────────────────────── ZIP ────────
function zipEntries(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf[i] === 0x50 && buf[i + 1] === 0x4b && buf[i + 2] === 0x05 && buf[i + 3] === 0x06) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error("no EOCD");
  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const entries = new Map();
  for (let i = 0; i < count; i++) {
    const method = buf.readUInt16LE(off + 10);
    const csize = buf.readUInt32LE(off + 20);
    const nlen = buf.readUInt16LE(off + 28);
    const elen = buf.readUInt16LE(off + 30);
    const clen = buf.readUInt16LE(off + 32);
    const lho = buf.readUInt32LE(off + 42);
    const name = buf.toString("utf8", off + 46, off + 46 + nlen);
    const dataStart = lho + 30 + buf.readUInt16LE(lho + 26) + buf.readUInt16LE(lho + 28);
    const comp = buf.slice(dataStart, dataStart + csize);
    entries.set(name, method === 8 ? inflateRawSync(comp) : comp);
    off += 46 + nlen + elen + clen;
  }
  return entries;
}
async function fetchBuf(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
  return Buffer.from(await res.arrayBuffer());
}
function findJar(argv) {
  const candidates = [
    argv[2],
    "/root/.gradle/caches/fabric-loom/1.8.9/minecraft-client.jar",
    join(homedir(), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar"),
    join(homedir(), ".minecraft/versions/1.8.9/1.8.9.jar"),
  ].filter(Boolean);
  for (const c of candidates) if (existsSync(c)) return c;
  return null;
}
async function downloadJar() {
  console.log("[qynl-pack] downloading vanilla 1.8.9 client jar ...");
  const manifest = JSON.parse((await fetchBuf("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).toString());
  const ver = manifest.versions.find((v) => v.id === VERSION_ID);
  if (!ver) throw new Error("1.8.9 not in version manifest");
  const meta = JSON.parse((await fetchBuf(ver.url)).toString());
  const url = meta.downloads.client.url;
  const dest = join(process.cwd(), ".cache", "1.8.9.jar");
  mkdirSync(dirname(dest), { recursive: true });
  const data = await fetchBuf(url);
  writeFileSync(dest, data);
  console.log("[qynl-pack] saved", dest, data.length, "bytes");
  return dest;
}

// ──────────────────────────────────────────────────────────────── images ─────
function img(w = 16, h = 16) { return { w, h, data: new Uint8Array(w * h * 4) }; }
function setPx(im, x, y, c) {
  if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
  const i = (y * im.w + x) * 4;
  im.data[i] = c[0]; im.data[i + 1] = c[1]; im.data[i + 2] = c[2];
  im.data[i + 3] = c.length > 3 ? c[3] : 255;
}
function getPx(im, x, y) {
  const i = (y * im.w + x) * 4;
  return [im.data[i], im.data[i + 1], im.data[i + 2], im.data[i + 3]];
}
function rect(im, x, y, w, h, c) { for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) setPx(im, x + dx, y + dy, c); }
function circle(im, cx, cy, r, c) {
  for (let y = Math.max(0, cy - r); y <= cy + r && y < im.h; y++)
    for (let x = Math.max(0, cx - r); x <= cx + r && x < im.w; x++)
      if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) setPx(im, x, y, c);
}
function shade(c, f) { return [Math.min(255, Math.round(c[0] * f)), Math.min(255, Math.round(c[1] * f)), Math.min(255, Math.round(c[2] * f)), c.length > 3 ? c[3] : 255]; }
function mix(a, b, t) {
  return [
    Math.round(a[0] + (b[0] - a[0]) * t),
    Math.round(a[1] + (b[1] - a[1]) * t),
    Math.round(a[2] + (b[2] - a[2]) * t),
    a.length > 3 ? a[3] : 255,
  ];
}
function outlineAuto(im, f = 0.42) {
  const d = im.data.slice();
  for (let y = 0; y < im.h; y++)
    for (let x = 0; x < im.w; x++) {
      const i = (y * im.w + x) * 4;
      if (d[i + 3] === 0) continue;
      const edge = [x - 1, y].some((_, k) => {
        const nx = k === 0 ? x - 1 : k === 1 ? x + 1 : k === 2 ? x : x;
        const ny = k === 0 ? y : k === 1 ? y : k === 2 ? y - 1 : y + 1;
        return nx < 0 || ny < 0 || nx >= im.w || ny >= im.h || d[(ny * im.w + nx) * 4 + 3] === 0;
      });
      if (edge) { im.data[i] = Math.round(d[i] * f); im.data[i + 1] = Math.round(d[i + 1] * f); im.data[i + 2] = Math.round(d[i + 2] * f); }
    }
}
function topLight(im, f, rows = 3) {
  for (let y = 0; y < rows && y < im.h; y++)
    for (let x = 0; x < im.w; x++) {
      const i = (y * im.w + x) * 4;
      if (im.data[i + 3] === 0) continue;
      const t = (rows - y) / rows * (f - 1);
      im.data[i] = Math.min(255, Math.round(im.data[i] * (1 + t)));
      im.data[i + 1] = Math.min(255, Math.round(im.data[i + 1] * (1 + t)));
      im.data[i + 2] = Math.min(255, Math.round(im.data[i + 2] * (1 + t)));
    }
}
function drawStr(im, rows, colors, ox = 0, oy = 0) {
  for (let y = 0; y < rows.length; y++) {
    const row = rows[y];
    for (let x = 0; x < row.length; x++) {
      const ch = row[x];
      if (ch !== "." && colors[ch]) setPx(im, ox + x, oy + y, colors[ch]);
    }
  }
}
function hashSeed(s) {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}
function mulberry(seed) {
  let a = seed >>> 0;
  return () => {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function writePng(rel, im) {
  const full = join(OUT, rel);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, encodePng(im.w, im.h, im.data));
}

// ──────────────────────────────────────────────────────────────── vanilla ────
function avgColor(png) {
  let r = 0, g = 0, b = 0, n = 0;
  for (let i = 0; i < png.data.length; i += 4) {
    if (png.data[i + 3] < 200) continue;
    r += png.data[i]; g += png.data[i + 1]; b += png.data[i + 2]; n++;
  }
  if (!n) return [128, 128, 128];
  return [Math.round(r / n), Math.round(g / n), Math.round(b / n)];
}
function transRatio(png) {
  let n = 0;
  for (let i = 3; i < png.data.length; i += 4) if (png.data[i] < 8) n++;
  return n / (png.w * png.h);
}
function flatGradient(base) {
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  topLight(im, 1.07, 1);
  rect(im, 0, 15, 16, 1, shade(base, 0.86));
  return im;
}
function speckle(im, base, dark, light, count, seedStr) {
  const rnd = mulberry(hashSeed(seedStr));
  for (let i = 0; i < count; i++) {
    const x = Math.floor(rnd() * 16), y = Math.floor(rnd() * 16);
    const c = rnd() < 0.7 ? dark : light;
    setPx(im, x, y, c);
    if (rnd() < 0.35 && x < 15) setPx(im, x + 1, y, c);
  }
}

// ──────────────────────────────────────────────────────────────── blocks ─────
const TIER = {
  wood: [0x9c, 0x7a, 0x4d], stone: [0x8f, 0x8f, 0x8f], iron: [0xd8, 0xd8, 0xd8],
  gold: [0xfc, 0xee, 0x4b], diamond: [0x4a, 0xed, 0xd9],
};
const HANDLE = [0x7a, 0x4f, 0x24];

function wool(png) {
  const base = avgColor(png);
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  const dark = shade(base, 0.93), light = shade(base, 1.07);
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++)
      if ((x + y) % 8 === 0) setPx(im, x, y, dark);
  for (let y = 0; y < 16; y += 4) for (let x = 0; x < 16; x += 4) setPx(im, x, y, light);
  topLight(im, 1.05, 2);
  rect(im, 0, 15, 16, 1, shade(base, 0.85));
  return im;
}
function clay(png) {
  const base = avgColor(png);
  return flatGradient(base);
}
function glass(png) {
  const base = avgColor(png);
  const im = img();
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) setPx(im, x, y, [base[0], base[1], base[2], 16]);
  const border = base[0] + base[1] + base[2] > 380 ? shade(base, 1.25) : base;
  for (let x = 0; x < 16; x++) { setPx(im, x, 0, border); setPx(im, x, 15, border); setPx(im, 0, x, border); setPx(im, 15, x, border); }
  setPx(im, 0, 0, shade(border, 0.8)); setPx(im, 15, 0, shade(border, 0.8));
  setPx(im, 0, 15, shade(border, 0.8)); setPx(im, 15, 15, shade(border, 0.8));
  return im;
}
function bed(png) {
  // sample the dominant red from the vanilla bed texture
  let red = [0xc2, 0x3b, 0x3b];
  let best = 0;
  for (let i = 0; i < png.data.length; i += 4) {
    if (png.data[i + 3] < 200) continue;
    const sat = png.data[i] - Math.min(png.data[i + 1], png.data[i + 2]);
    if (sat > best) { best = sat; red = [png.data[i], png.data[i + 1], png.data[i + 2]]; }
  }
  const im = img();
  rect(im, 0, 0, 16, 16, red);
  const white = [0xec, 0xec, 0xec];
  rect(im, 0, 0, 16, 2, white);
  rect(im, 0, 0, 2, 16, white);
  rect(im, 0, 14, 16, 2, shade(red, 0.7));
  rect(im, 14, 0, 2, 16, shade(red, 0.85));
  return im;
}
function tntSide(png) {
  const red = avgColor(png);
  const im = img();
  rect(im, 0, 0, 16, 16, red);
  rect(im, 0, 3, 16, 3, [0xf2, 0xf2, 0xf2]);
  rect(im, 0, 10, 16, 3, [0xf2, 0xf2, 0xf2]);
  // "TNT" letters in red on the white band
  const L = [red[0] * 0.8, red[1] * 0.8, red[2] * 0.8];
  const T = (x, y) => { rect(im, x, y, 3, 1, L); rect(im, x + 1, y + 1, 1, 2, L); };
  const N = (x, y) => { rect(im, x, y, 1, 3, L); rect(im, x + 2, y, 1, 3, L); rect(im, x + 1, y + 1, 1, 1, L); };
  T(1, 4); N(5, 4); T(10, 4);
  return im;
}
function tntTop(png) {
  const red = avgColor(png);
  const im = img();
  rect(im, 0, 0, 16, 16, red);
  rect(im, 4, 4, 8, 8, [0xf2, 0xf2, 0xf2]);
  rect(im, 5, 5, 6, 6, shade(red, 0.85));
  return im;
}
function planks(png) {
  const base = avgColor(png);
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  const dark = shade(base, 0.88);
  for (const x of [3, 9, 13]) rect(im, x, 0, 1, 16, dark);
  topLight(im, 1.05, 2);
  rect(im, 0, 15, 16, 1, shade(base, 0.82));
  return im;
}
function logSide(png) {
  const base = avgColor(png);
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  const dark = shade(base, 0.9);
  for (const x of [2, 6, 10, 14]) rect(im, x, 0, 1, 16, dark);
  rect(im, 0, 0, 16, 1, shade(base, 1.1));
  rect(im, 0, 15, 16, 1, shade(base, 0.82));
  return im;
}
function logTop(png) {
  const base = avgColor(png);
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  const dark = shade(base, 0.82);
  circle(im, 8, 8, 6, dark);
  circle(im, 8, 8, 4, mix(base, dark, 0.5));
  circle(im, 8, 8, 2, base);
  setPx(im, 8, 8, dark);
  return im;
}
const ORE_COLORS = {
  coal: [0x2b, 0x2b, 0x2b], iron: [0xd8, 0xaf, 0x93], gold: [0xfc, 0xee, 0x4b],
  redstone: [0xff, 0x2b, 0x2b], lapis: [0x2f, 0x52, 0xd8], diamond: [0x4a, 0xed, 0xd9],
  emerald: [0x17, 0xdd, 0x62], quartz: [0xf5, 0xf5, 0xf5],
};
function ore(png, name) {
  const im = flatGradient([0x8a, 0x8c, 0x8e]);
  const key = Object.keys(ORE_COLORS).find((k) => name.startsWith(k));
  const c = ORE_COLORS[key || "coal"];
  const rnd = mulberry(hashSeed(name));
  for (let i = 0; i < 5; i++) {
    const x = 2 + Math.floor(rnd() * 12), y = 2 + Math.floor(rnd() * 12);
    setPx(im, x, y, c);
    if (x < 15) setPx(im, x + 1, y, shade(c, 0.75));
    if (y < 15) setPx(im, x, y + 1, shade(c, 0.75));
  }
  return im;
}
const BLOCK_COLORS = {
  diamond: [0x4a, 0xed, 0xd9], gold: [0xfc, 0xee, 0x4b], iron: [0xd8, 0xd8, 0xd8],
  emerald: [0x17, 0xdd, 0x62], lapis: [0x2f, 0x52, 0xd8], redstone: [0xff, 0x2b, 0x2b],
  coal: [0x2b, 0x2b, 0x2b], quartz: [0xe8, 0xe8, 0xe8],
};
function metalBlock(name) {
  const base = BLOCK_COLORS[Object.keys(BLOCK_COLORS).find((k) => name.startsWith(k))] || [0xd8, 0xd8, 0xd8];
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  rect(im, 0, 0, 16, 2, shade(base, 1.18));
  rect(im, 0, 14, 16, 2, shade(base, 0.78));
  rect(im, 0, 2, 1, 12, shade(base, 1.08));
  rect(im, 15, 2, 1, 12, shade(base, 0.88));
  return im;
}
function stoneVariant(name) {
  const colors = {
    granite: [0xa0, 0x85, 0x76], diorite: [0xc8, 0xc8, 0xc8], andesite: [0x8b, 0x8b, 0x8b],
  };
  const key = Object.keys(colors).find((k) => name.includes(k));
  const base = colors[key] || [0x8b, 0x8b, 0x8b];
  const im = flatGradient(base);
  if (!name.includes("smooth")) speckle(im, base, shade(base, 0.82), shade(base, 1.12), 14, name);
  return im;
}
function stonebrick(name) {
  const im = flatGradient([0x8a, 0x8b, 0x8d]);
  if (name.includes("cracked")) {
    const rnd = mulberry(hashSeed(name));
    for (let i = 0; i < 6; i++) {
      const x = Math.floor(rnd() * 15), y = Math.floor(rnd() * 15);
      setPx(im, x, y, [0x6a, 0x6b, 0x6d]); setPx(im, x + 1, y, [0x6a, 0x6b, 0x6d]);
    }
  } else if (name.includes("mossy")) {
    const rnd = mulberry(hashSeed(name));
    for (let i = 0; i < 10; i++) setPx(im, Math.floor(rnd() * 16), Math.floor(rnd() * 16), [0x63, 0x7a, 0x4a]);
  } else if (name.includes("carved")) {
    rect(im, 7, 0, 1, 16, shade([0x8a, 0x8b, 0x8d], 0.8));
  } else {
    rect(im, 0, 7, 16, 1, shade([0x8a, 0x8b, 0x8d], 0.88));
    rect(im, 7, 0, 1, 7, shade([0x8a, 0x8b, 0x8d], 0.9));
    rect(im, 3, 8, 1, 8, shade([0x8a, 0x8b, 0x8d], 0.9));
    rect(im, 11, 8, 1, 8, shade([0x8a, 0x8b, 0x8d], 0.9));
  }
  return im;
}
function grass(png, name) {
  const im = img();
  if (name === "grass_top") {
    // vanilla grass_top is a GRAY noise texture tinted by the biome colormap at
    // render time -> ship a flat mid-gray so it renders as clean flat green
    const g = [0x9a, 0x9a, 0x9a];
    rect(im, 0, 0, 16, 16, g);
    topLight(im, 1.05, 1);
    rect(im, 0, 15, 16, 1, shade(g, 0.88));
  } else if (name === "grass_side_overlay") {
    // overlay is also biome-tinted -> flat mid-gray, keep vanilla alpha shape
    const g = [0x9a, 0x9a, 0x9a];
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const p = getPx(png, x, y);
        setPx(im, x, y, p[3] > 128 ? g : [g[0], g[1], g[2], 0]);
      }
  } else {
    const dirtC = avgColor(png);
    rect(im, 0, 0, 16, 16, dirtC);
    const g = name.includes("snowed") ? [0xf2, 0xf8, 0xff] : [0x70, 0xa8, 0x44];
    rect(im, 0, 0, 16, 3, g);
    rect(im, 0, 3, 16, 1, shade(g, 0.78));
    rect(im, 0, 15, 16, 1, shade(dirtC, 0.85));
  }
  return im;
}
function dirt(png) {
  const base = avgColor(png);
  const im = flatGradient(base);
  speckle(im, base, shade(base, 0.82), shade(base, 1.1), 10, "dirt");
  return im;
}
function sand(png) {
  return flatGradient(avgColor(png));
}
function sandstone(png, name) {
  const base = avgColor(png);
  const im = flatGradient(base);
  if (name.includes("carved")) rect(im, 7, 2, 2, 12, shade(base, 0.85));
  else if (name.includes("smooth")) { /* flat */ }
  else rect(im, 0, 7, 16, 1, shade(base, 0.88));
  return im;
}
function gravel(png) {
  const base = avgColor(png);
  const im = flatGradient(base);
  speckle(im, base, shade(base, 0.7), shade(base, 1.25), 16, "gravel");
  return im;
}
function bedrock() {
  const im = flatGradient([0x4a, 0x4a, 0x4a]);
  const rnd = mulberry(hashSeed("bedrock"));
  for (let i = 0; i < 7; i++) {
    const x = Math.floor(rnd() * 14), y = Math.floor(rnd() * 14);
    rect(im, x, y, 2 + Math.floor(rnd() * 2), 1 + Math.floor(rnd() * 2), [0x5c, 0x5c, 0x5c]);
  }
  return im;
}
function cobble(name) {
  const im = flatGradient([0x83, 0x85, 0x87]);
  const rnd = mulberry(hashSeed(name));
  const dark = [0x6b, 0x6d, 0x6f], light = [0x98, 0x9a, 0x9c];
  const patches = name.includes("mossy") ? [0x5e, 0x74, 0x46] : dark;
  for (let i = 0; i < 9; i++) {
    const x = Math.floor(rnd() * 13), y = Math.floor(rnd() * 13);
    const c = i < 3 ? patches : rnd() < 0.5 ? dark : light;
    rect(im, x, y, 2 + Math.floor(rnd() * 2), 2 + Math.floor(rnd() * 2), c);
  }
  return im;
}
function brick(name) {
  const base = name.includes("nether") ? [0x32, 0x1f, 0x1c] : [0x9c, 0x50, 0x43];
  const mortar = name.includes("nether") ? [0x1a, 0x0e, 0x0c] : [0xd9, 0xc9, 0xb0];
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  rect(im, 0, 7, 16, 1, mortar);
  rect(im, 0, 15, 16, 1, mortar);
  rect(im, 7, 0, 1, 7, mortar);
  rect(im, 3, 8, 1, 8, mortar);
  rect(im, 11, 8, 1, 8, mortar);
  return im;
}
function flatColor(c) { return flatGradient(c); }
function netherrack() {
  const im = flatGradient([0x4a, 0x20, 0x20]);
  speckle(im, [0x4a, 0x20, 0x20], [0x36, 0x14, 0x14], [0x5a, 0x2c, 0x2c], 12, "netherrack");
  return im;
}
function soulSand() {
  const im = flatGradient([0x40, 0x30, 0x1e]);
  const rnd = mulberry(hashSeed("soulsand"));
  for (let i = 0; i < 6; i++) setPx(im, Math.floor(rnd() * 16), Math.floor(rnd() * 16), [0x2c, 0x20, 0x12]);
  return im;
}
function ice() {
  const im = flatGradient([0x9e, 0xc8, 0xf0]);
  rect(im, 0, 15, 16, 1, [0x7d, 0xa8, 0xd9]);
  return im;
}
function icePacked() { return flatGradient([0x7d, 0xa8, 0xd9]); }
function mycelium(png, name) {
  const im = img();
  if (name.includes("top")) {
    const base = avgColor(png);
    rect(im, 0, 0, 16, 16, base);
    const rnd = mulberry(hashSeed("mycelium"));
    for (let i = 0; i < 10; i++) setPx(im, Math.floor(rnd() * 16), Math.floor(rnd() * 16), [0xd8, 0xd0, 0xc0]);
  } else {
    const base = avgColor(png);
    rect(im, 0, 0, 16, 16, base);
    rect(im, 0, 0, 16, 3, [0x6f, 0x5c, 0x56]);
  }
  return im;
}
function podzol(png, name) {
  const im = img();
  const base = avgColor(png);
  rect(im, 0, 0, 16, 16, base);
  if (name.includes("top")) speckle(im, base, shade(base, 0.8), shade(base, 1.12), 12, "podzol");
  else rect(im, 0, 0, 16, 3, [0x5b, 0x40, 0x24]);
  return im;
}
function melon(png, name) {
  const im = img();
  if (name === "melon_top") {
    const base = avgColor(png);
    rect(im, 0, 0, 16, 16, base);
    circle(im, 8, 8, 3, shade(base, 1.15));
    circle(im, 8, 8, 2, base);
  } else {
    const base = avgColor(png);
    rect(im, 0, 0, 16, 16, base);
    for (const x of [2, 6, 10, 14]) rect(im, x, 0, 1, 16, shade(base, 0.86));
    rect(im, 0, 15, 16, 1, shade(base, 0.8));
  }
  return im;
}
function pumpkin(png, name) {
  const base = [0xd7, 0x7f, 0x2a];
  const im = img();
  if (name === "pumpkin_top") {
    rect(im, 0, 0, 16, 16, base);
    circle(im, 8, 8, 3, shade(base, 0.75));
    rect(im, 0, 15, 16, 1, shade(base, 0.8));
  } else if (name === "pumpkin_face_off" || name === "pumpkin_face_on") {
    rect(im, 0, 0, 16, 16, base);
    for (let y = 0; y < 16; y++)
      for (let x = 0; x < 16; x++) {
        const q = getPx(png, x, y);
        if (q[3] > 100 && q[0] < 90 && q[1] < 90 && q[2] < 90) setPx(im, x, y, [0x1e, 0x12, 0x06]);
      }
    if (name === "pumpkin_face_on") circle(im, 8, 6, 3, [0xff, 0xd9, 0x7a]);
  } else {
    rect(im, 0, 0, 16, 16, base);
    for (const x of [3, 8, 13]) rect(im, x, 0, 1, 16, shade(base, 0.92));
    rect(im, 0, 15, 16, 1, shade(base, 0.8));
  }
  return im;
}
function cactus(png, name) {
  const base = [0x3f, 0x6b, 0x2e];
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  if (name === "cactus_top") {
    circle(im, 8, 8, 3, shade(base, 0.8));
    rect(im, 7, 4, 2, 4, shade(base, 1.15));
  } else {
    for (const x of [2, 5, 8, 11, 14]) rect(im, x, 0, 1, 16, shade(base, 1.12));
    rect(im, 0, 15, 16, 1, shade(base, 0.8));
  }
  return im;
}
function mushroomBlock(png, name) {
  const im = img();
  if (name.includes("inside")) { const base = avgColor(png); rect(im, 0, 0, 16, 16, base); return im; }
  let base;
  if (name.includes("skin_red")) base = [0xc9, 0x3a, 0x2e];
  else if (name.includes("skin_brown")) base = [0x9a, 0x6b, 0x45];
  else base = [0xe8, 0xdc, 0xbe];
  rect(im, 0, 0, 16, 16, base);
  if (!name.includes("stem")) {
    const rnd = mulberry(hashSeed(name));
    for (let i = 0; i < 8; i++) setPx(im, Math.floor(rnd() * 16), Math.floor(rnd() * 16), shade(base, 1.18));
  } else {
    for (const x of [3, 8, 12]) rect(im, x, 0, 1, 16, shade(base, 0.9));
  }
  return im;
}
function prismarine(png, name) {
  let base = [0x5c, 0xae, 0x9e];
  if (name.includes("dark")) base = [0x3d, 0x70, 0x66];
  const im = flatGradient(base);
  if (name.includes("bricks")) {
    const l = shade(base, 1.15);
    rect(im, 0, 0, 16, 1, l); rect(im, 0, 8, 16, 1, l);
    rect(im, 7, 0, 1, 8, l); rect(im, 3, 9, 1, 7, l); rect(im, 11, 9, 1, 7, l);
  } else if (name.includes("dark")) {
    speckle(im, base, shade(base, 0.8), shade(base, 1.15), 10, name);
  } else {
    speckle(im, base, shade(base, 0.85), shade(base, 1.12), 8, name);
  }
  return im;
}
function seaLantern() {
  const im = flatGradient([0xe6, 0xef, 0xe0]);
  const rnd = mulberry(hashSeed("sealantern"));
  for (let i = 0; i < 8; i++) setPx(im, Math.floor(rnd() * 16), Math.floor(rnd() * 16), [0xff, 0xff, 0xf5]);
  return im;
}
function slimeBlock() {
  const im = flatGradient([0x66, 0xc2, 0x66]);
  const rnd = mulberry(hashSeed("slime"));
  for (let i = 0; i < 8; i++) setPx(im, Math.floor(rnd() * 16), Math.floor(rnd() * 16), [0x4e, 0x9c, 0x4e]);
  return im;
}
function sponge(png) {
  const base = avgColor(png);
  const im = flatGradient(base);
  const rnd = mulberry(hashSeed("sponge"));
  for (let i = 0; i < 6; i++) {
    const x = Math.floor(rnd() * 15), y = Math.floor(rnd() * 15);
    setPx(im, x, y, shade(base, 0.6));
    setPx(im, x + 1, y, shade(base, 0.6));
  }
  return im;
}
function glowstone() {
  const im = flatGradient([0xd8, 0xa9, 0x4d]);
  speckle(im, [0xd8, 0xa9, 0x4d], [0xb8, 0x8c, 0x38], [0xec, 0xc6, 0x70], 12, "glowstone");
  return im;
}
function redstoneLamp(name) {
  if (name.includes("on")) {
    const im = flatGradient([0xff, 0x9a, 0x3c]);
    circle(im, 8, 8, 5, [0xff, 0xc9, 0x7a]);
    return im;
  }
  return flatGradient([0x5a, 0x50, 0x4a]);
}
function hay(name) {
  const base = [0xc9, 0xa6, 0x3f];
  const im = img();
  rect(im, 0, 0, 16, 16, base);
  if (name.includes("top")) {
    rect(im, 7, 0, 1, 16, shade(base, 0.86));
    rect(im, 0, 7, 16, 1, shade(base, 0.86));
  } else {
    for (const y of [3, 7, 11]) rect(im, 0, y, 16, 1, shade(base, 0.85));
    rect(im, 0, 0, 16, 1, shade(base, 1.1));
  }
  return im;
}
function bookshelf(png) {
  const wood = avgColor(png);
  const im = img();
  rect(im, 0, 0, 16, 16, wood);
  for (const x of [3, 9, 13]) rect(im, x, 0, 1, 16, shade(wood, 0.88));
  const shelf = [0x46, 0x2d, 0x1a];
  rect(im, 0, 4, 16, 3, shelf);
  rect(im, 0, 11, 16, 3, shelf);
  const books = [[0xa0, 0x30, 0x30], [0x30, 0x40, 0xa0], [0x2f, 0x6b, 0x2f], [0xb3, 0x9b, 0x2f], [0x6b, 0x3f, 0x8f], [0x8f, 0x5f, 0x3f]];
  const rnd = mulberry(hashSeed("bookshelf"));
  for (let row = 0; row < 2; row++)
    for (let x = 1; x < 15; x += 2) {
      const c = books[Math.floor(rnd() * books.length)];
      rect(im, x, row === 0 ? 5 : 12, 2, 2, c);
    }
  return im;
}
function craftingTable(png, name) {
  const wood = avgColor(png);
  const im = img();
  rect(im, 0, 0, 16, 16, wood);
  if (name.includes("top")) {
    const dark = shade(wood, 0.82);
    rect(im, 0, 5, 16, 1, dark); rect(im, 0, 10, 16, 1, dark);
    rect(im, 5, 0, 1, 16, dark); rect(im, 10, 0, 1, 16, dark);
    rect(im, 0, 15, 16, 1, shade(wood, 0.82));
  } else {
    rect(im, 0, 12, 16, 4, shade(wood, 0.82));
    if (name.includes("front")) rect(im, 2, 5, 12, 4, shade(wood, 0.88));
  }
  return im;
}
function furnace(png, name) {
  const stone = [0x8a, 0x8b, 0x8d];
  const im = flatGradient(stone);
  if (name === "furnace_front_off" || name === "furnace_front_on") {
    rect(im, 4, 9, 8, 5, [0x2e, 0x2e, 0x30]);
    rect(im, 5, 10, 6, 3, [0x14, 0x14, 0x15]);
    if (name.includes("on")) {
      rect(im, 6, 10, 4, 2, [0xff, 0x8c, 0x3c]);
      setPx(im, 7, 11, [0xff, 0xd9, 0x7a]);
      setPx(im, 8, 10, [0xff, 0xd9, 0x7a]);
    }
  }
  return im;
}
function quartz(name) {
  const base = name.includes("bottom") ? [0xd8, 0xd8, 0xd8] : [0xe8, 0xe8, 0xe8];
  const im = flatGradient(base);
  if (name.includes("chiseled")) {
    rect(im, 7, 0, 1, 16, shade(base, 0.82));
    rect(im, 6, 0, 1, 4, shade(base, 0.82));
    rect(im, 8, 0, 1, 4, shade(base, 0.82));
  } else if (name.includes("lines")) {
    for (const x of [4, 8, 12]) rect(im, x, 0, 1, 16, shade(base, 0.85));
  }
  return im;
}
function redSand(png) { return flatGradient(avgColor(png)); }

// keep these opaque-but-complex blocks vanilla
const KEEP_BLOCKS = new Set([
  "anvil_base", "anvil_top_damaged_0", "anvil_top_damaged_1", "anvil_top_damaged_2",
  "enchanting_table_side", "enchanting_table_top", "enchanting_table_bottom",
  "end_portal_frame_side", "end_portal_frame_top", "end_portal_frame_eye",
  "beacon", "dragon_egg", "cauldron_inner", "cauldron_top", "cauldron_bottom",
]);

function renderBlock(name, png) {
  if (KEEP_BLOCKS.has(name)) return null;
  if (name.startsWith("wool_colored") || name === "wool") return wool(png);
  if (name.startsWith("hardened_clay_stained")) return clay(png);
  if (name === "hardened_clay" || name === "clay") return clay(png);
  if (name.startsWith("glass")) return glass(png);
  if (name.startsWith("bed_")) return bed(png);
  if (name === "tnt_side") return tntSide(png);
  if (name === "tnt_top") return tntTop(png);
  if (name === "tnt_bottom") return flatGradient([0xcc, 0x39, 0x39]);
  if (name.startsWith("planks_")) return planks(png);
  if (name.startsWith("log_")) return name.includes("top") ? logTop(png) : logSide(png);
  if (name.endsWith("_ore")) return ore(png, name);
  if (name.endsWith("_block") && BLOCK_COLORS[Object.keys(BLOCK_COLORS).find((k) => name.startsWith(k))]) return metalBlock(name);
  if (name.startsWith("stone_") && ["granite", "diorite", "andesite"].some((k) => name.includes(k))) return stoneVariant(name);
  if (name.startsWith("stonebrick")) return stonebrick(name);
  if (name === "grass_top" || name === "grass_side" || name === "grass_side_snowed" || name === "grass_side_overlay") return grass(png, name);
  if (name.startsWith("dirt") || name === "coarse_dirt") return dirt(png);
  if (name === "sand" || name === "red_sand") return redSand(png);
  if (name.startsWith("sandstone") || name.startsWith("red_sandstone")) return sandstone(png, name);
  if (name === "gravel") return gravel(png);
  if (name === "bedrock") return bedrock();
  if (name === "cobblestone" || name === "cobblestone_mossy") return cobble(name);
  if (name === "brick") return brick(name);
  if (name === "nether_brick") return brick(name);
  if (name === "obsidian") return flatColor([0x14, 0x12, 0x1d]);
  if (name === "end_stone") { const im = flatColor([0xdc, 0xd5, 0xb8]); speckle(im, [0xdc, 0xd5, 0xb8], shade([0xdc, 0xd5, 0xb8], 0.9), shade([0xdc, 0xd5, 0xb8], 1.1), 8, "endstone"); return im; }
  if (name === "netherrack") return netherrack();
  if (name === "soul_sand") return soulSand();
  if (name === "snow") return flatColor([0xf2, 0xf8, 0xff]);
  if (name === "ice") return ice();
  if (name === "ice_packed") return icePacked();
  if (name.startsWith("mycelium")) return mycelium(png, name);
  if (name.startsWith("dirt_podzol")) return podzol(png, name);
  if (name.startsWith("melon")) return melon(png, name);
  if (name.startsWith("pumpkin")) return pumpkin(png, name);
  if (name.startsWith("cactus")) return cactus(png, name);
  if (name.startsWith("mushroom_block")) return mushroomBlock(png, name);
  if (name.startsWith("prismarine") || name === "sea_lantern") return prismarine(png, name);
  if (name === "slime") return slimeBlock();
  if (name === "sponge" || name === "sponge_wet") return sponge(png);
  if (name === "glowstone") return glowstone();
  if (name === "redstone_lamp_off" || name === "redstone_lamp_on") return redstoneLamp(name);
  if (name === "hay_block_side" || name === "hay_block_top") return hay(name);
  if (name === "bookshelf") return bookshelf(png);
  if (name.startsWith("crafting_table")) return craftingTable(png, name);
  if (name.startsWith("furnace")) return furnace(png, name);
  if (name.startsWith("quartz_block")) return quartz(name);
  if (name === "stone") return flatGradient([0x8b, 0x8c, 0x8e]);
  if (name === "stone_slab_side" || name === "stone_slab_top") return flatGradient([0x8b, 0x8c, 0x8e]);
  // default: if it looks transparent/decorative, keep vanilla; else flatten
  if (transRatio(png) > 0.03) return null;
  return flatGradient(avgColor(png));
}

// ──────────────────────────────────────────────────────────────── items ──────
const TOOL_ROWS = {
  sword: [
    "......######....",
    ".....######.....",
    "....######......",
    "...######.......",
    "..######........",
    ".######.........",
    "######..........",
    ".#####..........",
    "..####..........",
    "...###..........",
    "....GG..........",
    "....HH..........",
    "...HH...........",
    "..HH............",
    ".HH.............",
    "HH..............",
  ],
  pickaxe: [
    "........####....",
    ".......#####....",
    "......######....",
    ".....#####......",
    "....#####.......",
    "....###.........",
    "....##..........",
    "....##..........",
    "...#.#..........",
    "...#..##........",
    "..#....##.......",
    ".#......##......",
    "#........##.....",
    "..........##....",
    "...........##...",
    "............#...",
  ],
  axe: [
    "....########....",
    "...##########...",
    "..############..",
    "..############..",
    "..##########....",
    "....######......",
    ".....###........",
    ".....###........",
    ".....###........",
    ".....##.........",
    "....##..........",
    "...##...........",
    "..##............",
    ".##.............",
    "#...............",
    "................",
  ],
  shovel: [
    "........###.....",
    ".......#####....",
    ".......#####....",
    ".......#####....",
    "........###.....",
    "........##......",
    "........##......",
    ".......##.......",
    ".......##.......",
    "......##........",
    "......##........",
    ".....##.........",
    "....##..........",
    "...##...........",
    "..##............",
    ".#..............",
  ],
  hoe: [
    "......####......",
    ".....######.....",
    ".....######.....",
    "......####......",
    "........##......",
    "........##......",
    ".......##.......",
    ".......##.......",
    "......##........",
    "......##........",
    ".....##.........",
    "....##..........",
    "...##...........",
    "..##............",
    ".##.............",
    "#...............",
  ],
};
function toolItem(name) {
  const [tier, kind] = name.split("_");
  const rows = TOOL_ROWS[kind];
  if (!rows || !TIER[tier]) return null;
  const im = img();
  const blade = TIER[tier];
  const guard = shade(blade, 0.62);
  drawStr(im, rows, {
    "#": blade, b: blade, G: guard, g: guard, H: HANDLE, h: HANDLE, P: shade(blade, 0.75), p: shade(blade, 0.75),
  });
  outlineAuto(im, 0.4);
  topLight(im, 1.08, 4);
  return im;
}
function bowItem(name) {
  const sx = name === "bow_standby" ? 13 : name === "bow_pulling_0" ? 11 : name === "bow_pulling_1" ? 8 : 5;
  const rows = [];
  for (let y = 0; y < 16; y++) {
    const r = Array(16).fill(".");
    const t = Math.abs(y - 7.5) / 7.5;
    const arc = 2 + Math.round(3 * (1 - t));
    for (let k = 0; k < 2; k++) { const x = arc + k; if (x < 16) r[x] = "w"; }
    if (name !== "bow_standby") {
      for (let k = 0; k < 3; k++) { const x = sx + 1 + k; if (x < 16) r[x] = "f"; }
      if (y >= 6 && y <= 8) { for (let x = sx + 3; x < 15; x++) r[x] = "a"; if (y === 7) r[15] = "t"; }
    }
    r[sx] = "s";
    rows.push(r.join(""));
  }
  const im = img();
  drawStr(im, rows, { w: [0x8a, 0x5a, 0x2b], s: [0xec, 0xec, 0xec], f: [0xf0, 0xf0, 0xf0], a: [0x9a, 0x9c, 0x9e], t: [0x2b, 0x2b, 0x2b] });
  outlineAuto(im, 0.42);
  topLight(im, 1.06, 3);
  return im;
}
function arrowItem() {
  const im = img();
  const rows = [
    "........#.......",
    ".........#......",
    "..........#.....",
    "...........#....",
    "............#...",
    ".............#..",
    "..............#.",
    "...............#",
    "..............#.",
    ".............#..",
    "............#...",
    "...........#....",
    "..........#.....",
    ".........#......",
    "........#.......",
    ".......#........",
  ];
  drawStr(im, rows, { "#": [0xc8, 0xc8, 0xc8] });
  rect(im, 6, 13, 3, 1, [0xf0, 0xf0, 0xf0]);
  rect(im, 5, 12, 2, 1, [0xf0, 0xf0, 0xf0]);
  rect(im, 8, 14, 2, 1, [0xf0, 0xf0, 0xf0]);
  setPx(im, 15, 7, [0x2b, 0x2b, 0x2b]);
  outlineAuto(im, 0.45);
  return im;
}
function gemShape(name) {
  const c = name === "diamond" ? [0x4a, 0xed, 0xd9] : name === "emerald" ? [0x17, 0xdd, 0x62] : [0x4a, 0xed, 0xd9];
  const im = img();
  const rows = [
    "................",
    "......####......",
    ".....######.....",
    "....########....",
    "...##########...",
    "..############..",
    ".##############.",
    ".##############.",
    ".############...",
    "..##########....",
    "...########.....",
    "....######......",
    ".....####.......",
    "......##........",
    "................",
    "................",
  ];
  drawStr(im, rows, { "#": c });
  topLight(im, 1.15, 4);
  outlineAuto(im, 0.4);
  return im;
}
function ingot(name) {
  const c = name === "iron_ingot" ? [0xd8, 0xd8, 0xd8] : name === "gold_ingot" ? [0xfc, 0xee, 0x4b] : [0xd8, 0xd8, 0xd8];
  const im = img();
  const rows = [
    "................",
    "....########....",
    "...##########...",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "...##########...",
    "....########....",
    "................",
    "................",
    "................",
  ];
  drawStr(im, rows, { "#": c });
  topLight(im, 1.1, 3);
  outlineAuto(im, 0.42);
  return im;
}
function blobItem(name, rows, c, opts = {}) {
  const im = img();
  drawStr(im, rows, { "#": c, "2": opts.secondary || c });
  if (opts.highlight !== false) topLight(im, 1.12, opts.hlRows || 3);
  outlineAuto(im, 0.42);
  return im;
}
function circleItem(name, c, r = 5, opts = {}) {
  const im = img();
  circle(im, 8, 8, r, c);
  if (opts.highlight) circle(im, 6, 6, 1, shade(c, 1.5));
  if (opts.shine) setPx(im, 6, 6, shade(c, 1.6));
  outlineAuto(im, 0.42);
  return im;
}
function bucketItem(name) {
  const im = img();
  const rows = [
    "................",
    "..##........##..",
    "..##........##..",
    "..###......###..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "...##########...",
    "....########....",
    "................",
  ];
  const metal = [0x9a, 0x9c, 0x9e];
  drawStr(im, rows, { "#": metal });
  if (name === "bucket_water") rect(im, 3, 4, 10, 5, [0x30, 0x66, 0xd8]);
  else if (name === "bucket_lava") rect(im, 3, 4, 10, 5, [0xe8, 0x6a, 0x1a]);
  else if (name === "bucket_milk") rect(im, 3, 4, 10, 5, [0xf2, 0xf2, 0xf2]);
  else if (name === "bucket_empty") { /* empty */ }
  topLight(im, 1.1, 3);
  outlineAuto(im, 0.42);
  return im;
}
const SIMPLE_ITEM_ROWS = {
  apple: ["................", "......#####.....", "....#########...", "...##########...", "..############..", "..############..", "..############..", "..############..", "..############..", "..############..", "...##########...", "....########....", "......####......", "................", "................", "................"],
  bread: ["................", "................", ".....#####......", "....#######.....", "...#########....", "...#########....", "...#########....", "...#########....", "...#########....", "....#######.....", ".....#####......", "................", "................", "................", "................", "................"],
  ender_pearl: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  snowball: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  slime_ball: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  clay_ball: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  egg: ["................", "................", "......####......", ".....######.....", "....########....", "....########....", "....########....", "....########....", "....########....", ".....######.....", "......####......", "................", "................", "................", "................", "................"],
  coal: ["................", "................", "......####......", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................"],
  charcoal: ["................", "................", "......####......", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................"],
  feather: ["................", "................", "......###.......", ".....####.......", "....####........", "...####.........", "...###..........", "..###...........", "..###...........", "..###...........", "..###...........", "...###..........", "....###.........", ".....###........", "......#.........", "................"],
  bone: ["................", "................", "....######......", "...########.....", "...########.....", "....######......", "......###.......", ".......###......", "........###.....", ".........###....", "..........###...", ".......######...", "......########..", "......########..", ".......######...", "................"],
  flint: ["................", "................", "......###.......", ".....####.......", "....####........", "...####.........", "...###..........", "..###...........", "..###...........", "..###...........", "...###..........", "....###.........", ".....###........", "......###.......", "................", "................"],
  leather: ["................", "................", "......####......", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................"],
  paper: ["................", "................", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "................", "................"],
  book_normal: ["................", "................", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "................", "................"],
  stick: ["................", "................", "........#.......", "........#.......", ".......#........", ".......#........", "......#.........", "......#.........", ".....#..........", ".....#..........", "....#...........", "....#...........", "...#............", "...#............", "................", "................"],
  string: ["................", "................", "........#.......", "........#.......", ".......#........", ".......#........", "......#.........", "......#.........", ".....#..........", ".....#..........", "....#...........", "....#...........", "...#............", "...#............", "................", "................"],
  carrot: ["................", "....##..........", "....##..........", "....##..........", ".....####.......", ".....#####......", "......#####.....", ".......####.....", "........###.....", ".........##.....", "..........##....", "..........##....", "...........##...", "............##..", "................", "................"],
  potato: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  porkchop_raw: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  beef_raw: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  mutton_raw: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  rabbit_raw: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
  chicken_raw: ["................", "................", "......####......", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................"],
  fish_cod_raw: ["................", "................", "....######......", "...########.....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "...########.....", "....######......", "......##........", "................", "................", "................", "................"],
  fish_salmon_raw: ["................", "................", "....######......", "...########.....", "..##########....", "..##########....", "..##########....", "..##########....", "..##########....", "...########.....", "....######......", "......##........", "................", "................", "................", "................"],
  gunpowder: ["................", "................", "....##....##....", ".....##..##.....", "......####......", ".....######.....", "....########....", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................", "................"],
  glowstone_dust: ["................", "................", "....##....##....", ".....##..##.....", "......####......", ".....######.....", "....########....", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................", "................"],
  redstone: ["................", "................", "....##....##....", ".....##..##.....", "......####......", ".....######.....", "....########....", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................", "................"],
  sugar: ["................", "................", "....##....##....", ".....##..##.....", "......####......", ".....######.....", "....########....", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................", "................"],
  quartz: ["................", "................", "......####......", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................"],
  gold_nugget: ["................", "................", "......####......", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................"],
  melon: ["................", "................", "....####........", "...######.......", "..########......", "..##########....", "..###########...", "..###########...", "..###########...", "..##########....", "...########.....", "....######......", ".....####.......", "................", "................", "................"],
  bowl: ["................", "................", "..############..", "..############..", "..############..", "..############..", "..############..", "..############..", "...##########...", "....########....", ".....######.....", "......####......", "................", "................", "................", "................"],
  dye_powder_white: ["................", "................", "......#####.....", ".....#######....", "....#########...", "...###########..", "...###########..", "...###########..", "...###########..", "....#########...", ".....#######....", "......#####.....", "................", "................", "................", "................"],
};
const SIMPLE_COLORS = {
  apple: [0xf2, 0x3b, 0x3b], bread: [0xe8, 0xc9, 0x7a], ender_pearl: [0x2a, 0xb8, 0x9e],
  snowball: [0xf2, 0xf2, 0xf2], slime_ball: [0x66, 0xc2, 0x66], clay_ball: [0x9a, 0xa0, 0xa6],
  egg: [0xf2, 0xf2, 0xf2], coal: [0x2b, 0x2b, 0x2b], charcoal: [0x3a, 0x36, 0x30],
  feather: [0xf0, 0xf0, 0xf0], bone: [0xe8, 0xe8, 0xe0], flint: [0x5a, 0x5c, 0x5e],
  leather: [0x8a, 0x5a, 0x2b], paper: [0xf2, 0xf2, 0xf2], book_normal: [0x8a, 0x5a, 0x2b],
  stick: [0x8a, 0x5a, 0x2b], string: [0xe0, 0xe0, 0xe0], carrot: [0xe8, 0x8a, 0x1a],
  potato: [0xc9, 0xa6, 0x5a], porkchop_raw: [0xf0, 0x9a, 0x9a], beef_raw: [0xb0, 0x3a, 0x2e],
  mutton_raw: [0xc9, 0x4a, 0x4a], rabbit_raw: [0xe8, 0x9a, 0x9a], chicken_raw: [0xe8, 0xd8, 0xc0],
  fish_cod_raw: [0x9a, 0x9c, 0x9e], fish_salmon_raw: [0xe8, 0x9a, 0x5a],
  gunpowder: [0x6a, 0x6a, 0x6a], glowstone_dust: [0xf0, 0xc9, 0x5a], redstone: [0xd8, 0x2b, 0x2b],
  sugar: [0xf2, 0xf2, 0xf2], quartz: [0xe8, 0xe8, 0xe8], gold_nugget: [0xfc, 0xee, 0x4b],
  melon: [0x78, 0xa3, 0x3c], bowl: [0x8a, 0x5a, 0x2b],
  dye_powder_white: [0xf2, 0xf2, 0xf2], dye_powder_orange: [0xf0, 0x8a, 0x1a], dye_powder_magenta: [0xc8, 0x3a, 0xc8],
  dye_powder_light_blue: [0x7a, 0xb8, 0xf0], dye_powder_yellow: [0xf0, 0xe8, 0x3c], dye_powder_lime: [0x66, 0xc8, 0x3c],
  dye_powder_pink: [0xf0, 0x9a, 0xb8], dye_powder_gray: [0x8a, 0x8a, 0x8a], dye_powder_silver: [0xc8, 0xc8, 0xc8],
  dye_powder_cyan: [0x3c, 0xc8, 0xc8], dye_powder_purple: [0x8a, 0x3c, 0xc8], dye_powder_blue: [0x3c, 0x52, 0xc8],
  dye_powder_brown: [0x8a, 0x5a, 0x2b], dye_powder_green: [0x3c, 0x8a, 0x2b], dye_powder_red: [0xd8, 0x2b, 0x2b],
  dye_powder_black: [0x2b, 0x2b, 0x2b],
};
function simpleItem(name) {
  const rows = SIMPLE_ITEM_ROWS[name];
  if (!rows) return null;
  const c = SIMPLE_COLORS[name];
  return blobItem(name, rows, c, { highlight: true });
}
function foodItem(name, c) {
  const base = name.replace("_cooked", "").replace("_raw", "");
  const rows = SIMPLE_ITEM_ROWS[`${base}_raw`] || SIMPLE_ITEM_ROWS.potato;
  return blobItem(name, rows, c, { highlight: true });
}
function renderItem(name) {
  if (/^(wood|stone|iron|gold|diamond)_(sword|pickaxe|axe|shovel|hoe)$/.test(name)) return toolItem(name);
  if (name.startsWith("bow_")) return bowItem(name);
  if (name === "arrow") return arrowItem();
  if (name === "diamond" || name === "emerald") return gemShape(name);
  if (name === "iron_ingot" || name === "gold_ingot") return ingot(name);
  if (name.startsWith("bucket")) return bucketItem(name);
  if (SIMPLE_ITEM_ROWS[name]) return simpleItem(name);
  if (name === "apple_golden") return circleItem(name, [0xf0, 0xd0, 0x3c], 5, { shine: true });
  if (name === "carrot_golden") return blobItem(name, SIMPLE_ITEM_ROWS.carrot, [0xe8, 0xc9, 0x3c], {});
  if (name === "porkchop_cooked") return foodItem(name, [0xc9, 0x8a, 0x5a]);
  if (name === "beef_cooked") return foodItem(name, [0x8a, 0x5a, 0x3a]);
  if (name === "mutton_cooked") return foodItem(name, [0xa0, 0x6a, 0x4a]);
  if (name === "rabbit_cooked") return foodItem(name, [0xc0, 0x8a, 0x5a]);
  if (name === "chicken_cooked") return foodItem(name, [0xd8, 0xb8, 0x8a]);
  if (name === "fish_cod_cooked") return blobItem(name, SIMPLE_ITEM_ROWS.fish_cod_raw, [0xd8, 0xc0, 0x8a], {});
  if (name === "fish_salmon_cooked") return blobItem(name, SIMPLE_ITEM_ROWS.fish_salmon_raw, [0xd8, 0x8a, 0x5a], {});
  if (name === "potato_baked") return blobItem(name, SIMPLE_ITEM_ROWS.potato, [0xd8, 0xb8, 0x7a], {});
  if (name === "potato_poisonous") return blobItem(name, SIMPLE_ITEM_ROWS.potato, [0xc8, 0xc0, 0x8a], {});
  if (name === "melon_speckled") return blobItem(name, SIMPLE_ITEM_ROWS.melon, [0x78, 0xa3, 0x3c], { secondary: [0xf0, 0xd0, 0x3c] });
  if (name === "prismarine_shard") return gemShape(name);
  if (name === "prismarine_crystals") return blobItem(name, SIMPLE_ITEM_ROWS.quartz, [0x9a, 0xd8, 0xc8], {});
  if (name === "rabbit_foot") return blobItem(name, SIMPLE_ITEM_ROWS.egg, [0xe8, 0xe0, 0xd0], {});
  if (name === "rabbit_hide") return blobItem(name, SIMPLE_ITEM_ROWS.leather, [0xa0, 0x7a, 0x4a], {});
  if (name === "rabbit_stew") return blobItem(name, SIMPLE_ITEM_ROWS.bowl, [0xc8, 0x8a, 0x4a], {});
  if (name === "mushroom_stew") return blobItem(name, SIMPLE_ITEM_ROWS.bowl, [0xc8, 0x8a, 0x4a], {});
  if (name === "book_writable" || name === "book_written" || name === "book_enchanted") {
    return blobItem(name, SIMPLE_ITEM_ROWS.book_normal, name.includes("enchanted") ? [0x3c, 0x52, 0xc8] : [0x8a, 0x5a, 0x2b], {});
  }
  return null;
}

// ──────────────────────────────────────────────────────────────── GUI ────────
const DARK_RAMP = [
  [0x0b, 0x0c, 0x0e], [0x13, 0x15, 0x18], [0x1c, 0x1f, 0x23], [0x25, 0x29, 0x2e],
  [0x2f, 0x34, 0x3a], [0x3a, 0x40, 0x47], [0x47, 0x4e, 0x56], [0x58, 0x60, 0x68],
  [0x6c, 0x74, 0x7c], [0x84, 0x8c, 0x94], [0xa0, 0xa8, 0xaf], [0xc0, 0xc6, 0xcc],
  [0xd8, 0xda, 0xde], [0xf2, 0xf4, 0xf6],
];
function graySwap(png, keepChromatic = true) {
  const out = img(png.w, png.h);
  for (let i = 0; i < png.data.length; i += 4) {
    const a = png.data[i + 3];
    if (a === 0) continue;
    const r = png.data[i], g = png.data[i + 1], b = png.data[i + 2];
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    if (keepChromatic && max - min > 26 && max > 70) {
      out.data[i] = r; out.data[i + 1] = g; out.data[i + 2] = b; out.data[i + 3] = a;
      continue;
    }
    const L = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    // gamma curve: pull everything into a dark clean theme, keep white accents bright
    let idx = Math.round(Math.pow(L, 2.5) * (DARK_RAMP.length - 1));
    idx = Math.max(0, Math.min(DARK_RAMP.length - 1, idx));
    const c = DARK_RAMP[idx];
    out.data[i] = c[0]; out.data[i + 1] = c[1]; out.data[i + 2] = c[2]; out.data[i + 3] = a;
  }
  return out;
}

// ──────────────────────────────────────────────────────────────── env ────────
function sun() {
  const im = img(32, 32);
  circle(im, 16, 16, 13, [0xfb, 0xf2, 0x36]);
  circle(im, 16, 16, 9, [0xfe, 0xfa, 0x8a]);
  return im;
}
function moonPhases() {
  const w = 64, h = 32;
  const im = img(w, h);
  const cell = w / 8;
  for (let i = 0; i < 8; i++) {
    const cx = i * cell + cell / 2;
    circle(im, cx, h / 2, 12, [0xf2, 0xf2, 0xf2]);
    if (i === 0) { circle(im, cx, h / 2, 12, [0x0a, 0x0a, 0x0a]); continue; }
    if (i === 4) continue;
    const off = (i < 4 ? i : 8 - i) * (cell / 8);
    const dx = i < 4 ? off : -off;
    circle(im, cx + dx, h / 2, 12, [0x0a, 0x0a, 0x0a]);
  }
  return im;
}
function clouds() {
  const w = 256, h = 32;
  const im = img(w, h);
  const rnd = mulberry(hashSeed("clouds"));
  for (let x = 0; x < w; x += 32) {
    const y0 = 6 + Math.floor(rnd() * 16);
    circle(im, x + 10, y0, 6, [0xff, 0xff, 0xff]);
    circle(im, x + 18, y0 - 3, 7, [0xff, 0xff, 0xff]);
    circle(im, x + 26, y0, 6, [0xff, 0xff, 0xff]);
    rect(im, x + 8, y0, 20, 6, [0xff, 0xff, 0xff]);
  }
  return im;
}

// ──────────────────────────────────────────────────────────────── meta ───────
function packIcon() {
  const im = img(128, 128);
  rect(im, 4, 4, 120, 120, [0x13, 0x15, 0x18]);
  rect(im, 4, 4, 120, 2, [0x2a, 0x2f, 0x35]);
  rect(im, 4, 122, 120, 2, [0x0b, 0x0c, 0x0e]);
  rect(im, 4, 6, 2, 116, [0x0b, 0x0c, 0x0e]);
  rect(im, 122, 6, 2, 116, [0x0b, 0x0c, 0x0e]);
  const cyan = [0x4a, 0xed, 0xd9];
  // blocky "Q": ring + tail
  const cx = 64, cy = 60;
  for (let y = 0; y < 128; y++)
    for (let x = 0; x < 128; x++) {
      const d = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2);
      if (d >= 26 && d <= 34) setPx(im, x, y, cyan);
    }
  rect(im, 70, 70, 20, 10, cyan);
  rect(im, 62, 78, 12, 8, cyan);
  rect(im, 54, 84, 14, 8, cyan);
  // accent dot
  circle(im, 102, 24, 6, [0x3c, 0x52, 0xc8]);
  return im;
}

// ──────────────────────────────────────────────────────────────── main ───────
async function main() {
  const jarPath = findJar(process.argv) || (await downloadJar());
  console.log("[qynl-pack] using jar:", jarPath);
  const entries = zipEntries(readFileSync(jarPath));

  rmSync(OUT, { recursive: true, force: true });
  mkdirSync(OUT, { recursive: true });

  let blocks = 0, items = 0, gui = 0, env = 0;

  // blocks
  for (const [name, buf] of entries) {
    if (!name.startsWith("assets/minecraft/textures/blocks/") || !name.endsWith(".png")) continue;
    const file = name.slice("assets/minecraft/textures/blocks/".length).replace(/\.png$/, "");
    let png;
    try { png = decodePng(buf); } catch (e) { console.warn("  skip block", file, e.message); continue; }
    const out = renderBlock(file, png);
    if (!out) continue;
    writePng(`assets/minecraft/textures/blocks/${file}.png`, out);
    blocks++;
    const mcmeta = entries.get(`assets/minecraft/textures/blocks/${file}.png.mcmeta`);
    if (mcmeta) writeFileSync(join(OUT, `assets/minecraft/textures/blocks/${file}.png.mcmeta`), mcmeta);
  }

  // items
  for (const [name, buf] of entries) {
    if (!name.startsWith("assets/minecraft/textures/items/") || !name.endsWith(".png")) continue;
    const file = name.slice("assets/minecraft/textures/items/".length).replace(/\.png$/, "");
    let png;
    try { png = decodePng(buf); } catch (e) { continue; }
    const out = renderItem(file);
    if (!out) continue;
    writePng(`assets/minecraft/textures/items/${file}.png`, out);
    items++;
    const mcmeta = entries.get(`assets/minecraft/textures/items/${file}.png.mcmeta`);
    if (mcmeta) writeFileSync(join(OUT, `assets/minecraft/textures/items/${file}.png.mcmeta`), mcmeta);
  }

  // gui — dark swap for containers/widgets/icons + flat dark options background
  const guiFiles = ["widgets.png", "icons.png", "spectator_widgets.png", "server_selection.png", "resource_packs.png"];
  for (const f of guiFiles) {
    const buf = entries.get(`assets/minecraft/textures/gui/${f}`);
    if (!buf) continue;
    let png;
    try { png = decodePng(buf); } catch (e) { continue; }
    writePng(`assets/minecraft/textures/gui/${f}`, graySwap(png));
    gui++;
  }
  for (const [name, buf] of entries) {
    if (!name.startsWith("assets/minecraft/textures/gui/container/") || !name.endsWith(".png")) continue;
    const rel = name.slice("assets/minecraft/textures/".length);
    let png;
    try { png = decodePng(buf); } catch (e) { continue; }
    // containers are pure UI chrome: map EVERYTHING (incl. the dev test-pattern
    // in inventory.png's player area) to the dark ramp for a uniform clean look
    writePng(`assets/minecraft/textures/${rel}`, graySwap(png, false));
    gui++;
  }
  const ob = entries.get("assets/minecraft/textures/gui/options_background.png");
  if (ob) {
    try {
      const p = decodePng(ob);
      const flat = img(p.w, p.h);
      rect(flat, 0, 0, p.w, p.h, [0x17, 0x19, 0x1c]);
      writePng("assets/minecraft/textures/gui/options_background.png", flat);
      gui++;
    } catch (e) { /* ignore */ }
  }

  // environment
  if (entries.has("assets/minecraft/textures/environment/sun.png")) {
    writePng("assets/minecraft/textures/environment/sun.png", sun());
    writePng("assets/minecraft/textures/environment/moon_phases.png", moonPhases());
    writePng("assets/minecraft/textures/environment/clouds.png", clouds());
    env = 3;
  }

  // meta + icon + readme
  writeFileSync(join(OUT, "pack.mcmeta"), JSON.stringify({
    pack: {
      pack_format: 1,
      description: "Qyn-L Clean — Bare-Bones-style BedWars PvP: flat blocks, near-invisible glass, dark GUI, clean items",
    },
  }, null, 2));
  writePng("pack.png", packIcon());
  writeFileSync(join(OUT, "README.md"), `# Qyn-L Clean (BedWars PvP)

A clean, **Bare-Bones-style** resource pack for **Minecraft 1.8.9**, tuned for BedWars / PvP
and matching the Qyn-L client aesthetic. Every texture is generated from the vanilla 1.8.9
jar by \`scripts/gen-qynl-pack.mjs\` — no pixels are copied from any other resource pack.

## What it changes
- **Blocks** — every solid block flattened to clean, flat colors (grass, stone, cobblestone,
  bricks, planks, logs, ores, metal blocks, prismarine, sea lantern, slime, sponge, beds, TNT …)
- **Wool (16)** — flat with a subtle weave so bed layers / defenses read at a distance
- **Glass + panes (17)** — near-invisible with a bright 1px border (PvP standard)
- **Stained clay / terracotta (16)** — clean flat colors
- **Items** — hand-drawn clean tools (sword/pickaxe/axe/shovel/hoe in 5 tiers), bow (4 states),
  arrow, gems, ingots, food, materials, buckets, dyes
- **GUI** — dark clean containers (inventory, chest, crafting, furnace, …), hotbar/widgets,
  icons, menus
- **Environment** — clean sun, moon phases, clouds
- Everything else falls back to vanilla 1.8.9 textures.

## Install
Copy the \`QynL-BedWars\` folder into \`.minecraft/resourcepacks/\` (or your launcher's
resource-pack folder) and enable it in Options → Resource Packs.

## Regenerate
\`\`\`bash
bun scripts/gen-qynl-pack.mjs [path/to/1.8.9.jar]
\`\`\`
(Downloads the vanilla 1.8.9 client jar automatically if no path is given.)
`);
  writeFileSync(join(OUT, "LICENSE.txt"), "Original generated textures — free to use and modify for personal use.\n");
  writeFileSync(join(OUT, "pack.png.mcmeta"), JSON.stringify({ animation: {} }));

  console.log(`[qynl-pack] done → ${OUT}`);
  console.log(`  blocks: ${blocks}  items: ${items}  gui: ${gui}  env: ${env}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
