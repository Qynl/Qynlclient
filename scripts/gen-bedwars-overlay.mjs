#!/usr/bin/env node
// ────────────────────────────────────────────────────────────────────────────
// Qyn-L BedWars PvP overlay — builds on the REAL Bare Bones pack
// (RobotPants, https://modrinth.com/resourcepack/bare-bones, version 1.0.1 /
// "Bare Bones 1.8.9.zip", All Rights Reserved).
//
// Usage:
//   bun scripts/gen-bedwars-overlay.mjs <bare-bones-dir> <out-dir>
//
// Copies the whole Bare Bones 1.8.9 pack into <out-dir>, then overlays:
//   • wool (all 16 colors) — a subtle weave on top of Bare Bones' exact flat
//     colors, so BedWars layer colors read better at a distance,
//   • container GUI (inventory, chest, double chest, crafting table, furnace)
//     — a clean dark panel with correct vanilla slot grids that matches the
//     Qyn-L client,
//   • pack.mcmeta — pack_format 1 (1.8.9) + description,
//   • README.md with attribution.
// Everything else stays 100% Bare Bones.
// ────────────────────────────────────────────────────────────────────────────
import { deflateSync, inflateSync } from "node:zlib";
import { cpSync, mkdirSync, writeFileSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const [bbDir, outDir] = process.argv.slice(2);
if (!bbDir || !outDir) {
    console.error("usage: bun scripts/gen-bedwars-overlay.mjs <bare-bones-dir> <out-dir>");
    process.exit(1);
}

// ────────────────────────────────────────────────────────────── PNG codecs
const CRC_TABLE = (() => {
    const t = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        t[n] = c;
    }
    return t;
})();

function crc32(buf) {
    let c = 0xffffffff;
    for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const t = Buffer.from(type, "ascii");
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
    return Buffer.concat([len, t, data, crc]);
}

function encodePng(width, height, rgba) {
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(width, 0);
    ihdr.writeUInt32BE(height, 4);
    ihdr[8] = 8;
    ihdr[9] = 6;
    const raw = Buffer.alloc(height * (1 + width * 4));
    for (let y = 0; y < height; y++) {
        raw[y * (1 + width * 4)] = 0;
        rgba.copy(raw, y * (1 + width * 4) + 1, y * width * 4, (y + 1) * width * 4);
    }
    return Buffer.concat([
        Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
        chunk("IHDR", ihdr),
        chunk("IDAT", deflateSync(raw, { level: 9 })),
        chunk("IEND", Buffer.alloc(0)),
    ]);
}

function decodePng(buf) {
    let pos = 8, width = 0, height = 0, colorType = 0;
    const idat = [];
    while (pos < buf.length) {
        const len = buf.readUInt32BE(pos);
        const type = buf.toString("ascii", pos + 4, pos + 8);
        const data = buf.subarray(pos + 8, pos + 8 + len);
        if (type === "IHDR") { width = data.readUInt32BE(0); height = data.readUInt32BE(4); colorType = data[9]; }
        else if (type === "IDAT") idat.push(data);
        else if (type === "IEND") break;
        pos += 12 + len;
    }
    const raw = inflateSync(Buffer.concat(idat));
    const bpp = colorType === 6 ? 4 : 3;
    const stride = width * bpp;
    const out = Buffer.alloc(width * height * 4);
    let prev = Buffer.alloc(stride), cur = Buffer.alloc(stride);
    for (let y = 0; y < height; y++) {
        const filter = raw[y * (stride + 1)];
        raw.copy(cur, 0, y * (stride + 1) + 1, (y + 1) * (stride + 1));
        for (let x = 0; x < stride; x++) {
            const a = x >= bpp ? cur[x - bpp] : 0, b = prev[x], c = x >= bpp ? prev[x - bpp] : 0;
            let v = cur[x];
            if (filter === 1) v = (v + a) & 0xff;
            else if (filter === 2) v = (v + b) & 0xff;
            else if (filter === 3) v = (v + ((a + b) >> 1)) & 0xff;
            else if (filter === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 0xff; }
            cur[x] = v;
        }
        for (let x = 0; x < width; x++) {
            const si = x * bpp, di = (y * width + x) * 4;
            out[di] = cur[si]; out[di + 1] = cur[si + 1]; out[di + 2] = cur[si + 2];
            out[di + 3] = colorType === 6 ? cur[si + 3] : 255;
        }
        const t = prev; prev = cur; cur = t;
    }
    return { width, height, data: out };
}

// ──────────────────────────────────────────────────────────── drawing utils
function make(w, h) {
    return { w, h, data: Buffer.alloc(w * h * 4) };
}

function px(img, x, y, [r, g, b, a = 255]) {
    if (x < 0 || y < 0 || x >= img.w || y >= img.h) return;
    const i = (y * img.w + x) * 4;
    img.data[i] = r; img.data[i + 1] = g; img.data[i + 2] = b; img.data[i + 3] = a;
}

function rect(img, x0, y0, x1, y1, c) {
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) px(img, x, y, c);
}

function shade([r, g, b], f) {
    return [Math.round(Math.min(255, r * f)), Math.round(Math.min(255, g * f)), Math.round(Math.min(255, b * f))];
}

// ──────────────────────────────────────────────────────────── wool weave
function weave(base) {
    const img = make(16, 16);
    const light = shade(base, 1.05);
    const dark = shade(base, 0.9);
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            let c = base;
            if ((x + y) % 8 === 0) c = dark;
            if ((x * 3 + y * 5) % 13 === 0) c = light;
            px(img, x, y, c);
        }
    }
    return img;
}

// ──────────────────────────────────────────────────────────── container GUI
function slotRow(y) {
    const row = [];
    for (let x = 8; x <= 152; x += 18) row.push([x, y]);
    return row;
}

function renderContainer(width, height, slots) {
    const img = make(width, height);
    const panel = [21, 24, 31];
    const border = [42, 48, 62];
    const slotFill = [50, 57, 74];
    const slotEdge = [32, 37, 50];
    rect(img, 0, 0, width - 1, height - 1, panel);
    rect(img, 0, 0, width - 1, 0, border);
    rect(img, 0, height - 1, width - 1, height - 1, border);
    rect(img, 0, 0, 0, height - 1, border);
    rect(img, width - 1, 0, width - 1, height - 1, border);
    for (const [sx, sy] of slots) {
        rect(img, sx, sy, sx + 17, sy + 17, slotFill);
        rect(img, sx, sy, sx + 17, sy, slotEdge);
        rect(img, sx, sy + 17, sx + 17, sy + 17, slotEdge);
        rect(img, sx, sy, sx, sy + 17, slotEdge);
        rect(img, sx + 17, sy, sx + 17, sy + 17, slotEdge);
    }
    return img;
}

function renderInventory() {
    const slots = [];
    const img = make(176, 166);
    rect(img, 0, 0, 175, 165, [21, 24, 31]);
    rect(img, 25, 17, 90, 76, [0, 0, 0, 0]); // player model cutout
    rect(img, 0, 0, 175, 0, [42, 48, 62]);
    rect(img, 0, 165, 175, 165, [42, 48, 62]);
    rect(img, 0, 0, 0, 165, [42, 48, 62]);
    rect(img, 175, 0, 175, 165, [42, 48, 62]);
    for (const y of [84, 102, 120, 142]) slots.push(...slotRow(y));
    slots.push([98, 18], [116, 18], [98, 36], [116, 36], [124, 34]);
    for (const [sx, sy] of slots) {
        rect(img, sx, sy, sx + 17, sy + 17, [50, 57, 74]);
        rect(img, sx, sy, sx + 17, sy, [32, 37, 50]);
        rect(img, sx, sy + 17, sx + 17, sy + 17, [32, 37, 50]);
        rect(img, sx, sy, sx, sy + 17, [32, 37, 50]);
        rect(img, sx + 17, sy, sx + 17, sy + 17, [32, 37, 50]);
    }
    return img;
}

function renderGeneric(tall) {
    const slots = [];
    if (tall) {
        for (let r = 0; r < 6; r++) slots.push(...slotRow(18 + r * 18));
        for (const y of [140, 158, 176, 198]) slots.push(...slotRow(y));
        return renderContainer(176, 222, slots);
    }
    for (let r = 0; r < 3; r++) slots.push(...slotRow(18 + r * 18));
    for (const y of [84, 102, 120, 142]) slots.push(...slotRow(y));
    return renderContainer(176, 166, slots);
}

function renderCrafting() {
    const slots = [];
    for (const x of [30, 48, 66]) for (const y of [18, 36, 54]) slots.push([x, y]);
    slots.push([124, 36]);
    for (const y of [84, 102, 120, 142]) slots.push(...slotRow(y));
    return renderContainer(176, 166, slots);
}

function renderFurnace() {
    const slots = [];
    slots.push([56, 18], [56, 54], [116, 36]);
    for (const y of [84, 102, 120, 142]) slots.push(...slotRow(y));
    const img = renderContainer(176, 166, slots);
    rect(img, 59, 39, 69, 50, [30, 34, 44]);
    rect(img, 79, 39, 110, 50, [30, 34, 44]);
    return img;
}

// ──────────────────────────────────────────────────────────────── main
function copyTree(src, dst) {
    cpSync(src, dst, { recursive: true });
}

function firstOpaqueColor(png) {
    for (let i = 0; i < png.data.length; i += 4) {
        if (png.data[i + 3] === 255) {
            return [png.data[i], png.data[i + 1], png.data[i + 2]];
        }
    }
    return [150, 150, 150];
}

function main() {
    mkdirSync(outDir, { recursive: true });

    // 1. copy the whole Bare Bones pack
    for (const entry of readdirSync(bbDir)) {
        copyTree(join(bbDir, entry), join(outDir, entry));
    }

    const blocksDir = join(outDir, "assets", "minecraft", "textures", "blocks");
    const guiDir = join(outDir, "assets", "minecraft", "textures", "gui", "container");
    mkdirSync(blocksDir, { recursive: true });
    mkdirSync(guiDir, { recursive: true });

    // 2. wool: subtle weave on the exact Bare Bones colors
    let wools = 0;
    for (const f of readdirSync(blocksDir)) {
        if (!f.startsWith("wool_colored_") || !f.endsWith(".png")) continue;
        const color = firstOpaqueColor(decodePng(readFileSync(join(blocksDir, f))));
        writeFileSync(join(blocksDir, f), encodePng(16, 16, weave(color).data));
        wools++;
    }

    // 3. container GUI: clean dark panel
    const gui = {
        "inventory.png": renderInventory,
        "chest.png": () => renderGeneric(false),
        "generic_54.png": () => renderGeneric(true),
        "crafting_table.png": renderCrafting,
        "furnace.png": renderFurnace,
    };
    let guis = 0;
    for (const [name, render] of Object.entries(gui)) {
        const img = render();
        writeFileSync(join(guiDir, name), encodePng(img.w, img.h, img.data));
        guis++;
    }

    // 4. pack.mcmeta
    writeFileSync(join(outDir, "pack.mcmeta"), JSON.stringify({
        pack: {
            pack_format: 1,
            description: "Qyn-L BedWars PvP — real Bare Bones base (RobotPants), BedWars-tuned wool + dark GUI",
        },
    }, null, 2) + "\n");

    // 5. README
    writeFileSync(join(outDir, "README.md"),
        "# Qyn-L BedWars PvP (1.8.9)\n\n" +
        "Built on the **real Bare Bones** resource pack by **RobotPants** " +
        "(https://modrinth.com/resourcepack/bare-bones, All Rights Reserved).\n\n" +
        "BedWars tuning on top of Bare Bones:\n" +
        "- **Wool (all 16 colors)**: subtle weave on Bare Bones' flat colors so\n" +
        "  bed / layer colors read better at a distance\n" +
        "- **Container GUI**: clean dark panel (inventory, chest, double chest,\n" +
        "  crafting table, furnace) matching the Qyn-L client\n" +
        "- Everything else stays 100% Bare Bones (glass, beds, TNT, items, …)\n\n" +
        "Place the folder in `.minecraft/resourcepacks/` (or your launcher's pack folder).\n");

    console.log(`[overlay] copied Bare Bones + ${wools} wool weaves + ${guis} container GUIs -> ${outDir}`);
}

main();
