#!/usr/bin/env node
// ────────────────────────────────────────────────────────────────────────────
// Qyn-L BedWars PvP — Bare Bones style texture pack generator for 1.8.9.
//
// Reads the vanilla 1.8.9 client jar and generates texturepacks/QynL-BedWars/:
//   • every solid block becomes a flat, clean "bare bones" texture in its
//     authentic vanilla color (average of the opaque pixels),
//   • BedWars / PvP-critical textures are hand-tuned: saturated wool with a
//     subtle weave, terracotta, near-invisible bordered glass + panes, a clean
//     red bed, TNT with letters, seamed planks/logs, ore/metal blocks, a flat
//     water/lava, and clean sword/tool/item icons,
//   • the container GUI (inventory, chest, double chest, crafting, furnace)
//     is redrawn as a clean dark panel with proper slot grids.
//
// Transparent decorative blocks (plants, doors, rails, …) are left vanilla.
//
// Usage:  bun scripts/gen-bedwars-pack.mjs [vanilla-1.8.9-client.jar]
//         (falls back to injector/build/vanilla/1.8.9.jar, then downloads)
// ────────────────────────────────────────────────────────────────────────────
import { deflateSync, inflateRawSync, inflateSync } from "node:zlib";
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, "..", "texturepacks", "QynL-BedWars");
const JAR_CANDIDATES = [
    join(HERE, "..", "injector", "build", "vanilla", "1.8.9.jar"),
    join(HERE, "..", "1.8.9", "run", ".minecraft", "versions", "1.8.9", "1.8.9.jar"),
];

// ────────────────────────────────────────────────────────────── PNG decode
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
    ihdr[8] = 8;   // bit depth
    ihdr[9] = 6;   // RGBA
    const raw = Buffer.alloc(height * (1 + width * 4));
    for (let y = 0; y < height; y++) {
        raw[y * (1 + width * 4)] = 0; // filter: none
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
    if (buf.length < 8 || buf.readUInt32BE(0) !== 0x89504e47) throw new Error("not a png");
    let pos = 8;
    let width = 0, height = 0, bitDepth = 0, colorType = 0;
    const idat = [];
    while (pos < buf.length) {
        const len = buf.readUInt32BE(pos);
        const type = buf.toString("ascii", pos + 4, pos + 8);
        const data = buf.subarray(pos + 8, pos + 8 + len);
        if (type === "IHDR") {
            width = data.readUInt32BE(0);
            height = data.readUInt32BE(4);
            bitDepth = data[8];
            colorType = data[9];
            if (bitDepth !== 8) throw new Error("unsupported bit depth " + bitDepth);
        } else if (type === "IDAT") {
            idat.push(data);
        } else if (type === "IEND") {
            break;
        }
        pos += 12 + len;
    }
    // PNG IDAT is zlib-wrapped (RFC 1950) — NOT raw deflate like zip entries.
    const raw = inflateSync(Buffer.concat(idat));
    const bpp = colorType === 6 ? 4 : colorType === 2 ? 3 : colorType === 0 ? 1 : colorType === 4 ? 2 : 0;
    if (!bpp) throw new Error("unsupported color type " + colorType);
    const stride = width * bpp;
    const out = Buffer.alloc(width * height * 4);
    let prev = Buffer.alloc(stride);
    let cur = Buffer.alloc(stride);
    for (let y = 0; y < height; y++) {
        const filter = raw[y * (stride + 1)];
        raw.copy(cur, 0, y * (stride + 1) + 1, (y + 1) * (stride + 1));
        for (let x = 0; x < stride; x++) {
            const a = x >= bpp ? cur[x - bpp] : 0;
            const b = prev[x];
            const c = x >= bpp ? prev[x - bpp] : 0;
            let v = cur[x];
            switch (filter) {
                case 1: v = (v + a) & 0xff; break;
                case 2: v = (v + b) & 0xff; break;
                case 3: v = (v + ((a + b) >> 1)) & 0xff; break;
                case 4: {
                    const p = a + b - c;
                    const pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
                    v = (v + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 0xff;
                    break;
                }
            }
            cur[x] = v;
        }
        for (let x = 0; x < width; x++) {
            const si = x * bpp, di = (y * width + x) * 4;
            if (colorType === 6) {
                out[di] = cur[si]; out[di + 1] = cur[si + 1]; out[di + 2] = cur[si + 2]; out[di + 3] = cur[si + 3];
            } else if (colorType === 2) {
                out[di] = cur[si]; out[di + 1] = cur[si + 1]; out[di + 2] = cur[si + 2]; out[di + 3] = 255;
            } else if (colorType === 0) {
                out[di] = cur[si]; out[di + 1] = cur[si]; out[di + 2] = cur[si]; out[di + 3] = 255;
            } else if (colorType === 4) {
                out[di] = cur[si]; out[di + 1] = cur[si]; out[di + 2] = cur[si]; out[di + 3] = cur[si + 1];
            }
        }
        const t = prev; prev = cur; cur = t;
    }
    return { width, height, data: out };
}

// ──────────────────────────────────────────────────────────────── ZIP read
function readZipIndex(buf) {
    // find End Of Central Directory
    let eocd = -1;
    for (let i = buf.length - 22; i >= 0; i--) {
        if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error("no EOCD");
    const count = buf.readUInt16LE(eocd + 10);
    const cdSize = buf.readUInt32LE(eocd + 12);
    const cdOffset = buf.readUInt32LE(eocd + 16);
    const entries = new Map();
    let p = cdOffset;
    for (let i = 0; i < count && p < cdOffset + cdSize; i++) {
        if (buf.readUInt32LE(p) !== 0x02014b50) break;
        const method = buf.readUInt16LE(p + 10);
        const csize = buf.readUInt32LE(p + 20);
        const nameLen = buf.readUInt16LE(p + 28);
        const extraLen = buf.readUInt16LE(p + 30);
        const commentLen = buf.readUInt16LE(p + 32);
        const localOff = buf.readUInt32LE(p + 42);
        const name = buf.toString("utf8", p + 46, p + 46 + nameLen);
        entries.set(name, { method, csize, localOff });
        p += 46 + nameLen + extraLen + commentLen;
    }
    return entries;
}

function zipEntry(buf, index, name) {
    const e = index.get(name);
    if (!e) return null;
    const lh = e.localOff;
    const nameLen = buf.readUInt16LE(lh + 26);
    const extraLen = buf.readUInt16LE(lh + 28);
    const start = lh + 30 + nameLen + extraLen;
    const data = buf.subarray(start, start + e.csize);
    if (e.method === 0) return Buffer.from(data);
    if (e.method === 8) return inflateRawSync(data);
    throw new Error("unsupported zip method " + e.method);
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

function solidImg([r, g, b, a = 255]) {
    const img = make(16, 16);
    rect(img, 0, 0, 15, 15, [r, g, b, a]);
    return img;
}

function shade([r, g, b], f) {
    return [Math.round(Math.min(255, r * f)), Math.round(Math.min(255, g * f)), Math.round(Math.min(255, b * f))];
}

function mix([r1, g1, b1], [r2, g2, b2], t) {
    return [Math.round(r1 + (r2 - r1) * t), Math.round(g1 + (g2 - g1) * t), Math.round(b1 + (b2 - b1) * t)];
}

// ──────────────────────────────────────────────────────────────── palette
// vanilla 1.8.9 wool colors — these are the BedWars layer colors
const WOOL = {
    white: [233, 236, 236], orange: [240, 118, 19], magenta: [189, 68, 179],
    light_blue: [58, 175, 217], yellow: [248, 197, 39], lime: [112, 185, 25],
    pink: [237, 141, 172], gray: [62, 68, 71], silver: [142, 142, 134],
    cyan: [21, 137, 145], purple: [121, 42, 172], blue: [53, 57, 157],
    brown: [114, 71, 40], green: [84, 109, 27], red: [161, 39, 34], black: [20, 21, 25],
};

const BLOCK = {
    stone: [125, 125, 125], cobble: [125, 125, 125], bedrock: [72, 72, 72],
    obsidian: [18, 15, 30], endstone: [219, 215, 170], snow: [244, 249, 251],
    ice: [150, 200, 230], packedIce: [160, 190, 220], sand: [219, 205, 152],
    redSand: [185, 85, 50], gravel: [128, 118, 112], dirt: [134, 95, 67],
    coarseDirt: [122, 86, 61], grassTop: [106, 168, 68], grassSideDirt: [134, 95, 67],
    podzolTop: [93, 68, 44], water: [42, 92, 205], lava: [235, 130, 40],
    glowstone: [199, 161, 90], seaLantern: [178, 218, 198], sponge: [198, 189, 80],
    slime: [102, 192, 72], netherrack: [96, 39, 38], soulSand: [68, 54, 44],
    brick: [148, 70, 60], mortar: [196, 191, 182], netherBrick: [38, 20, 24],
    quartz: [238, 232, 220], quartzDark: [222, 214, 198],
};

const ORE = {
    coal: [42, 42, 42], iron: [212, 178, 140], gold: [238, 200, 62],
    diamond: [92, 222, 200], emerald: [42, 198, 82], redstone: [228, 34, 22],
    lapis: [42, 72, 186], quartz: [242, 232, 222],
};

const METAL = {
    diamond: [104, 228, 216], gold: [250, 208, 58], iron: [228, 228, 228],
    emerald: [44, 218, 92], lapis: [32, 62, 186], redstone: [198, 30, 18],
    coal: [44, 44, 44],
};

const PLANKS = {
    oak: [162, 131, 79], spruce: [98, 78, 49], birch: [191, 178, 136],
    jungle: [158, 109, 77], acacia: [161, 93, 58], big_oak: [70, 56, 33],
};

const TOOLS = {
    wood: { blade: [168, 134, 84], dark: [134, 106, 66], handle: [122, 90, 46] },
    stone: { blade: [138, 138, 138], dark: [110, 110, 110], handle: [100, 84, 60] },
    iron: { blade: [216, 216, 216], dark: [174, 174, 174], handle: [116, 88, 54] },
    gold: { blade: [242, 200, 58], dark: [196, 158, 40], handle: [116, 88, 54] },
    diamond: { blade: [78, 224, 208], dark: [46, 178, 164], handle: [116, 88, 54] },
};

// ──────────────────────────────────────────────────── special block renders
function weave(base) {
    const img = make(16, 16);
    const light = shade(base, 1.06);
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

function renderWool(name) {
    const color = name.replace("wool_colored_", "").replace(".png", "");
    return weave(WOOL[color] || [150, 150, 150]);
}

function renderClay(name) {
    let c;
    if (name === "hardened_clay.png") c = [168, 118, 90];
    else {
        const color = name.replace("hardened_clay_stained_", "").replace(".png", "");
        const base = WOOL[color] || [150, 150, 150];
        const gray = [128, 128, 128];
        c = mix(base, gray, 0.28);
        c = shade(c, 0.82);
    }
    return solidImg(c);
}

function renderGlass(name) {
    const colored = name !== "glass.png";
    const color = colored ? (WOOL[name.replace("glass_", "").replace(".png", "")] || [255, 255, 255]) : [255, 255, 255];
    const img = make(16, 16);
    const border = colored ? mix(color, [255, 255, 255], 0.35) : [255, 255, 255];
    const tint = colored ? [color[0], color[1], color[2], 34] : [255, 255, 255, 18];
    // border
    for (let i = 0; i < 16; i++) {
        px(img, i, 0, border); px(img, i, 15, border);
        px(img, 0, i, border); px(img, 15, i, border);
    }
    // interior tint
    for (let y = 1; y < 15; y++) for (let x = 1; x < 15; x++) px(img, x, y, tint);
    return img;
}

function renderPane(name) {
    const colored = name !== "glass_pane_top.png";
    const color = colored ? (WOOL[name.replace("glass_pane_top_", "").replace(".png", "")] || [255, 255, 255]) : [255, 255, 255];
    const img = make(16, 16);
    const bar = colored ? mix(color, [255, 255, 255], 0.25) : [255, 255, 255];
    for (let y = 0; y < 16; y++) {
        px(img, 6, y, bar); px(img, 7, y, bar); px(img, 8, y, bar);
    }
    px(img, 5, 0, bar); px(img, 9, 0, bar);
    px(img, 5, 15, bar); px(img, 9, 15, bar);
    return img;
}

function renderBed(name) {
    const img = make(16, 16);
    const white = [238, 238, 238];
    const whiteDark = [214, 214, 214];
    const red = [161, 39, 34];
    const wood = [150, 108, 62];
    if (name.startsWith("bed_head_top") || name.startsWith("bed_feet_top")) {
        rect(img, 0, 0, 15, 15, whiteDark);
    } else if (name.startsWith("bed_head")) {
        rect(img, 0, 0, 15, 15, white);
        rect(img, 0, 0, 15, 3, red);
        rect(img, 0, 13, 15, 15, wood);
    } else {
        rect(img, 0, 0, 15, 15, white);
        rect(img, 0, 0, 15, 2, red);
        rect(img, 0, 14, 15, 15, wood);
    }
    return img;
}

function renderTnt(name) {
    const img = make(16, 16);
    const red = [196, 42, 34];
    const white = [240, 238, 232];
    if (name === "tnt_top.png" || name === "tnt_bottom.png") {
        rect(img, 0, 0, 15, 15, red);
        rect(img, 1, 1, 14, 14, shade(red, 1.08));
        rect(img, 2, 2, 13, 13, red);
    } else {
        rect(img, 0, 0, 15, 15, red);
        rect(img, 0, 6, 15, 9, white);
        rect(img, 0, 6, 15, 6, shade(white, 0.94));
        const black = [20, 18, 18];
        drawLetter(img, 2, 7, "T", black);
        drawLetter(img, 7, 7, "N", black);
        drawLetter(img, 12, 7, "T", black);
    }
    return img;
}

function drawLetter(img, ox, oy, ch, c) {
    const glyphs = {
        T: ["111", "010", "010", "010", "010"],
        N: ["101", "111", "101", "101", "101"],
    };
    const g = glyphs[ch];
    if (!g) return;
    for (let y = 0; y < g.length; y++) {
        for (let x = 0; x < 3; x++) {
            if (g[y][x] === "1") px(img, ox + x, oy + y, c);
        }
    }
}

function renderPlanks(name) {
    const color = PLANKS[name.replace("planks_", "").replace(".png", "")] || [150, 120, 75];
    const img = make(16, 16);
    const seam = shade(color, 0.78);
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            px(img, x, y, color);
        }
    }
    for (let y = 0; y < 16; y++) {
        if (y % 4 === 0) {
            for (let x = 0; x < 16; x++) px(img, x, y, seam);
        } else {
            const off = (Math.floor(y / 4) * 7) % 16;
            px(img, (off + 8) % 16, y, seam);
            px(img, (off + 8 + 7) % 16, y, seam);
        }
    }
    return img;
}

function renderLog(name) {
    if (name.endsWith("_top.png")) {
        const color = { log_oak: [180, 145, 88], log_spruce: [110, 86, 52], log_birch: [206, 194, 152], log_jungle: [140, 96, 64], log_acacia: [152, 88, 54], log_big_oak: [90, 70, 44] }[name.replace("_top.png", "")] || [150, 120, 75];
        const img = make(16, 16);
        const rings = [shade(color, 1.0), shade(color, 0.86), shade(color, 1.0), shade(color, 0.86), shade(color, 1.0)];
        for (let y = 0; y < 16; y++) {
            for (let x = 0; x < 16; x++) {
                const d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
                px(img, x, y, rings[Math.min(4, Math.floor(d / 2))]);
            }
        }
        return img;
    }
    const color = { log_oak: [168, 134, 84], log_spruce: [98, 78, 49], log_birch: [191, 178, 136], log_jungle: [148, 102, 66], log_acacia: [146, 88, 50], log_big_oak: [72, 56, 36] }[name.replace(".png", "")] || [150, 120, 75];
    const img = solidImg(color);
    const seam = shade(color, 0.7);
    for (let x = 0; x < 16; x += 4) {
        rect(img, x, 0, x, 15, seam);
    }
    return img;
}

function renderOre(name) {
    const ore = ORE[name.replace("_ore.png", "")] || [200, 200, 200];
    const img = solidImg(BLOCK.stone);
    const spots = [[3, 3], [11, 4], [5, 9], [12, 11], [8, 6], [2, 12], [9, 13], [14, 2]];
    for (let i = 0; i < spots.length; i++) {
        const [sx, sy] = spots[i];
        const c = i % 3 === 0 ? shade(ore, 1.15) : ore;
        px(img, sx, sy, c);
        px(img, sx + 1, sy, c);
        px(img, sx, sy + 1, c);
        if (i % 2 === 0) px(img, sx + 1, sy + 1, shade(ore, 0.85));
    }
    return img;
}

function renderMetal(name) {
    const key = name.replace("_block.png", "");
    const c = METAL[key] || [200, 200, 200];
    const img = make(16, 16);
    const top = shade(c, 1.12);
    const bottom = shade(c, 0.82);
    const dark = shade(c, 0.66);
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            let col = c;
            if (y === 0) col = top;
            else if (y === 15) col = bottom;
            else if (x === 0 || x === 15) col = dark;
            px(img, x, y, col);
        }
    }
    return img;
}

function renderStoneBrick() {
    const img = solidImg([128, 128, 128]);
    const seam = [108, 108, 108];
    for (let y = 0; y < 16; y += 4) rect(img, 0, y, 15, y, seam);
    for (let y = 0; y < 16; y += 8) {
        rect(img, 7, y + 1, 7, y + 3, seam);
        rect(img, 0, y + 5, 0, y + 7, seam);
        rect(img, 14, y + 5, 14, y + 7, seam);
    }
    return img;
}

function renderCobble() {
    const img = solidImg([126, 126, 126]);
    const dark = [108, 108, 108];
    const strokes = [
        [[0, 3], [5, 3], [9, 3], [13, 3]],
        [[3, 7], [8, 7], [12, 7]],
        [[0, 11], [5, 11], [10, 11], [14, 11]],
        [[2, 15], [8, 15], [13, 15]],
    ];
    for (const row of strokes) {
        for (const [x, y] of row) px(img, x, y, dark);
    }
    return img;
}

function renderBricks(isNether) {
    const base = isNether ? BLOCK.netherBrick : BLOCK.brick;
    const mortar = isNether ? [30, 16, 19] : BLOCK.mortar;
    const img = make(16, 16);
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            let c = base;
            if (y % 4 === 0) c = mortar;
            else {
                const row = Math.floor(y / 4);
                const off = row % 2 === 0 ? 0 : 8;
                if ((x - off + 16) % 16 === 0) c = mortar;
                if ((x - off + 16) % 16 === 7) c = mortar;
            }
            px(img, x, y, c);
        }
    }
    return img;
}

function renderSandstone(red) {
    const base = red ? BLOCK.redSand : BLOCK.sand;
    const seam = shade(base, 0.8);
    const img = solidImg(base);
    for (let y = 0; y < 16; y += 4) rect(img, 0, y, 15, y, seam);
    rect(img, 0, 1, 15, 2, seam);
    rect(img, 0, 5, 15, 6, seam);
    return img;
}

function renderGrassSide() {
    const img = solidImg(BLOCK.grassSideDirt);
    rect(img, 0, 0, 15, 3, BLOCK.grassTop);
    rect(img, 0, 4, 15, 4, shade(BLOCK.grassTop, 0.85));
    return img;
}

function renderWaterLava(flow) {
    const base = flow === "lava" ? BLOCK.lava : BLOCK.water;
    const img = solidImg(base);
    const streak = shade(base, 0.88);
    for (let y = 0; y < 16; y += 4) {
        const off = (y / 4) % 2 === 0 ? 0 : 8;
        for (let x = 0; x < 16; x++) {
            if ((x + off) % 16 === 0 || (x + off) % 16 === 1) px(img, x, y, streak);
        }
    }
    return img;
}

function renderQuartz() {
    const img = solidImg(BLOCK.quartz);
    rect(img, 0, 0, 15, 0, BLOCK.quartzDark);
    rect(img, 0, 15, 15, 15, BLOCK.quartzDark);
    rect(img, 0, 0, 0, 15, BLOCK.quartzDark);
    rect(img, 15, 0, 15, 15, BLOCK.quartzDark);
    return img;
}

function renderBookshelf() {
    const img = renderPlanks("planks_oak.png");
    const bookColors = [[150, 60, 50], [60, 100, 170], [70, 140, 70], [190, 170, 60], [130, 70, 150]];
    let i = 0;
    for (let y = 3; y <= 11; y++) {
        for (let x = 1; x <= 14; x++) {
            if (y >= 4 && y <= 10) px(img, x, y, bookColors[i % bookColors.length]);
            i++;
        }
    }
    rect(img, 1, 3, 14, 3, [60, 45, 30]);
    rect(img, 1, 11, 14, 11, [60, 45, 30]);
    return img;
}

function renderCraftingTable() {
    const img = renderPlanks("planks_oak.png");
    const seam = shade(PLANKS.oak, 0.72);
    rect(img, 4, 0, 4, 15, seam);
    rect(img, 11, 0, 11, 15, seam);
    rect(img, 0, 4, 15, 4, seam);
    rect(img, 0, 11, 15, 11, seam);
    return img;
}

function renderFurnaceSide() {
    const img = solidImg(BLOCK.stone);
    rect(img, 0, 3, 15, 3, shade(BLOCK.stone, 0.8));
    rect(img, 0, 12, 15, 12, shade(BLOCK.stone, 0.8));
    return img;
}

function renderFurnaceFront(on) {
    const img = solidImg(BLOCK.stone);
    const opening = on ? [222, 120, 44] : [24, 22, 22];
    rect(img, 4, 4, 11, 11, opening);
    rect(img, 3, 3, 12, 3, shade(BLOCK.stone, 0.85));
    rect(img, 3, 12, 12, 12, shade(BLOCK.stone, 0.85));
    rect(img, 3, 3, 3, 12, shade(BLOCK.stone, 0.85));
    rect(img, 12, 3, 12, 12, shade(BLOCK.stone, 0.85));
    if (on) {
        px(img, 5, 5, [255, 200, 120]);
        px(img, 10, 10, [255, 200, 120]);
    }
    return img;
}

function renderEnderChest() {
    const img = solidImg([28, 24, 44]);
    const band = [80, 60, 120];
    rect(img, 0, 6, 15, 8, band);
    rect(img, 7, 4, 8, 10, [40, 32, 60]);
    return img;
}

// ──────────────────────────────────────────────────────────────── items
function renderSword(matName) {
    const mat = TOOLS[matName];
    const img = make(16, 16);
    for (let y = 1; y <= 8; y++) {
        px(img, 7, y, mat.blade);
        px(img, 8, y, mat.blade);
    }
    px(img, 7, 0, mat.blade);
    px(img, 8, 0, shade(mat.blade, 0.7));
    for (let y = 0; y <= 8; y++) px(img, 7, y, shade(mat.blade, 1.12));
    rect(img, 5, 9, 10, 9, mat.dark);
    px(img, 5, 10, mat.dark); px(img, 10, 10, mat.dark);
    rect(img, 7, 10, 8, 13, mat.handle);
    px(img, 7, 12, shade(mat.handle, 0.85));
    rect(img, 7, 14, 8, 14, mat.dark);
    return img;
}

function renderPickaxe(matName) {
    const mat = TOOLS[matName];
    const img = make(16, 16);
    rect(img, 3, 2, 12, 3, mat.blade);
    px(img, 2, 2, mat.blade); px(img, 13, 2, mat.blade);
    px(img, 3, 1, mat.dark); px(img, 12, 1, mat.dark);
    for (const tx of [4, 7, 10]) {
        px(img, tx, 4, mat.blade);
        px(img, tx, 5, shade(mat.blade, 0.9));
    }
    for (let y = 4; y <= 13; y++) px(img, 7, y, mat.handle);
    px(img, 8, 12, mat.handle); px(img, 9, 13, mat.handle);
    px(img, 7, 14, mat.dark);
    return img;
}

function renderAxe(matName) {
    const mat = TOOLS[matName];
    const img = make(16, 16);
    for (let y = 1; y <= 9; y++) {
        const w = y <= 5 ? 6 : 6 - (y - 5);
        for (let x = 3; x < 3 + w; x++) px(img, x, y, mat.blade);
    }
    px(img, 2, 3, mat.dark); px(img, 2, 6, mat.dark); px(img, 2, 8, mat.dark);
    for (let y = 7; y <= 12; y++) {
        px(img, 8, y, mat.handle);
        px(img, 9, y, mat.handle);
    }
    px(img, 10, 11, mat.handle); px(img, 11, 12, mat.handle);
    px(img, 8, 13, mat.dark); px(img, 11, 13, mat.dark);
    return img;
}

function renderShovel(matName) {
    const mat = TOOLS[matName];
    const img = make(16, 16);
    rect(img, 6, 2, 9, 6, mat.blade);
    px(img, 6, 1, mat.blade); px(img, 9, 1, mat.blade);
    rect(img, 6, 6, 9, 6, mat.dark);
    rect(img, 7, 7, 8, 12, mat.handle);
    px(img, 7, 13, mat.dark); px(img, 8, 13, mat.dark);
    return img;
}

function renderHoe(matName) {
    const mat = TOOLS[matName];
    const img = make(16, 16);
    rect(img, 3, 3, 12, 4, mat.blade);
    px(img, 2, 3, mat.blade); px(img, 13, 3, mat.blade);
    rect(img, 4, 5, 5, 6, mat.blade);
    rect(img, 10, 5, 11, 6, mat.blade);
    for (let y = 4; y <= 12; y++) px(img, 8, y, mat.handle);
    px(img, 9, 12, mat.handle); px(img, 10, 13, mat.handle);
    px(img, 8, 13, mat.dark);
    return img;
}

function renderBow(pull) {
    const img = make(16, 16);
    const wood = [150, 108, 62];
    const woodDark = [120, 84, 48];
    const string = [226, 222, 214];
    const arc = [[7, 1], [8, 2], [9, 3], [10, 4], [10, 5], [10, 6], [9, 7], [9, 8], [9, 9], [10, 10], [10, 11], [10, 12], [9, 13], [8, 14], [7, 15]];
    for (const [x, y] of arc) {
        px(img, x, y, wood);
        px(img, x - 1, y, woodDark);
    }
    const sx = 3 + pull;
    for (let y = 2; y <= 13; y++) px(img, sx, y, string);
    px(img, sx + 1, 2, string); px(img, sx + 1, 13, string);
    return img;
}

function renderArrow() {
    const img = make(16, 16);
    const head = [206, 206, 206];
    const shaft = [150, 108, 62];
    const fletch = [214, 214, 214];
    px(img, 7, 1, head); px(img, 8, 1, head);
    px(img, 6, 2, head); px(img, 7, 2, head); px(img, 8, 2, head); px(img, 9, 2, head);
    px(img, 7, 3, head); px(img, 8, 3, head);
    rect(img, 7, 4, 8, 13, shaft);
    px(img, 5, 12, fletch); px(img, 6, 12, fletch);
    px(img, 9, 12, fletch); px(img, 10, 12, fletch);
    px(img, 5, 13, fletch); px(img, 10, 13, fletch);
    px(img, 6, 14, fletch); px(img, 9, 14, fletch);
    return img;
}

function renderApple(golden) {
    const img = make(16, 16);
    if (golden) {
        for (let y = 4; y <= 11; y++) {
            for (let x = 4; x <= 11; x++) {
                const d = Math.hypot(x - 7.5, y - 7.5);
                if (d <= 4.0) {
                    const c = mix([238, 190, 50], [255, 232, 140], Math.max(0, 1 - d / 4.5));
                    px(img, x, y, c);
                }
            }
        }
        px(img, 7, 2, [120, 84, 48]); px(img, 8, 2, [120, 84, 48]);
        px(img, 9, 3, [90, 150, 70]);
        px(img, 6, 5, [255, 250, 200]);
        px(img, 10, 9, [222, 176, 44]);
    } else {
        for (let y = 4; y <= 11; y++) {
            for (let x = 3; x <= 12; x++) {
                const d = Math.hypot(x - 7.5, y - 7.5);
                if (d <= 4.2) {
                    const c = mix([186, 42, 34], [226, 92, 74], Math.max(0, 1 - d / 4.5));
                    px(img, x, y, c);
                }
            }
        }
        px(img, 7, 2, [120, 84, 48]); px(img, 8, 2, [120, 84, 48]);
        px(img, 9, 3, [90, 150, 70]);
        px(img, 5, 5, [244, 180, 150]);
    }
    return img;
}

function renderBread() {
    const img = make(16, 16);
    const crust = [168, 116, 54];
    const crumb = [220, 178, 110];
    rect(img, 4, 6, 11, 12, crust);
    rect(img, 5, 5, 10, 6, crust);
    rect(img, 5, 7, 10, 11, crumb);
    rect(img, 7, 8, 7, 10, crust);
    px(img, 9, 8, crust);
    return img;
}

function renderEnderPearl() {
    const img = make(16, 16);
    const c = [34, 168, 142];
    for (let y = 4; y <= 11; y++) {
        for (let x = 4; x <= 11; x++) {
            const d = Math.hypot(x - 7.5, y - 7.5);
            if (d <= 4.0) px(img, x, y, mix(c, [120, 230, 200], Math.max(0, 1 - d / 4.2)));
        }
    }
    px(img, 7, 2, [50, 50, 50]); px(img, 8, 2, [50, 50, 50]);
    px(img, 6, 3, [70, 70, 70]);
    px(img, 5, 6, [180, 250, 230]);
    return img;
}

function renderEnderEye() {
    const img = renderEnderPearl();
    rect(img, 6, 0, 9, 1, [170, 210, 130]);
    px(img, 10, 1, [170, 210, 130]);
    return img;
}

function renderEgg() {
    const img = make(16, 16);
    for (let y = 4; y <= 11; y++) {
        for (let x = 5; x <= 10; x++) {
            const dx = Math.abs(x - 7.5) / 3.2;
            const dy = (y - 7.5) / 4.2;
            if (dx * dx + dy * dy <= 1) {
                px(img, x, y, mix([238, 222, 190], [250, 244, 226], 1 - dy));
            }
        }
    }
    return img;
}

function renderSnowball() {
    const img = make(16, 16);
    for (let y = 5; y <= 10; y++) {
        for (let x = 5; x <= 10; x++) {
            const d = Math.hypot(x - 7.5, y - 7.5);
            if (d <= 3.0) px(img, x, y, mix([230, 240, 246], [250, 252, 254], 1 - d / 3.2));
        }
    }
    px(img, 6, 6, [255, 255, 255]);
    return img;
}

function renderShears() {
    const img = make(16, 16);
    const steel = [206, 210, 216];
    const dark = [150, 154, 162];
    for (let y = 3; y <= 7; y++) {
        px(img, 4, y, steel); px(img, 5, y, steel);
        px(img, 8, y, steel); px(img, 9, y, steel);
    }
    px(img, 4, 8, steel); px(img, 5, 8, steel);
    px(img, 8, 8, steel); px(img, 9, 8, steel);
    px(img, 5, 9, dark); px(img, 8, 9, dark);
    for (let y = 10; y <= 12; y++) {
        px(img, 3, y, dark); px(img, 4, y, dark);
        px(img, 11, y, dark); px(img, 12, y, dark);
    }
    px(img, 3, 13, dark); px(img, 12, 13, dark);
    return img;
}

function renderCompass() {
    const img = make(16, 16);
    for (let y = 2; y <= 13; y++) {
        for (let x = 2; x <= 13; x++) {
            const d = Math.hypot(x - 7.5, y - 7.5);
            if (d <= 5.8) px(img, x, y, [108, 108, 108]);
            if (d <= 5.4) px(img, x, y, [210, 210, 210]);
        }
    }
    for (let i = 0; i <= 4; i++) {
        px(img, 7 + i, 7 - i, [200, 40, 40]);
        px(img, 7 + i + 1, 7 - i, [150, 30, 30]);
    }
    px(img, 7, 8, [30, 30, 30]);
    return img;
}

function renderClock() {
    const img = make(16, 16);
    for (let y = 2; y <= 13; y++) {
        for (let x = 2; x <= 13; x++) {
            const d = Math.hypot(x - 7.5, y - 7.5);
            if (d <= 5.8) px(img, x, y, [150, 120, 70]);
            if (d <= 5.4) px(img, x, y, [232, 214, 160]);
        }
    }
    px(img, 7, 7, [60, 45, 25]); px(img, 8, 7, [60, 45, 25]);
    for (let y = 3; y <= 6; y++) px(img, 7, y, [60, 45, 25]);
    px(img, 9, 5, [60, 45, 25]);
    px(img, 7, 8, [40, 30, 16]);
    return img;
}

function renderStick() {
    const img = make(16, 16);
    const c = [150, 108, 62];
    for (let y = 2; y <= 13; y++) {
        px(img, 7, y, c);
        px(img, 8, y, shade(c, 0.8));
    }
    px(img, 6, 3, c); px(img, 9, 12, c);
    return img;
}

function renderString() {
    const img = make(16, 16);
    const c = [224, 222, 210];
    for (let y = 3; y <= 12; y++) {
        px(img, 6 + (y % 3 === 0 ? 1 : 0), y, c);
    }
    return img;
}

function renderPaper() {
    const img = make(16, 16);
    rect(img, 5, 2, 11, 13, [236, 232, 218]);
    rect(img, 4, 3, 5, 12, [210, 205, 188]);
    px(img, 7, 5, [180, 175, 158]); px(img, 9, 5, [180, 175, 158]);
    px(img, 7, 7, [180, 175, 158]); px(img, 9, 7, [180, 175, 158]);
    px(img, 7, 9, [180, 175, 158]);
    return img;
}

function renderMap() {
    const img = make(16, 16);
    rect(img, 2, 2, 13, 13, [198, 168, 96]);
    rect(img, 4, 4, 11, 11, [226, 204, 138]);
    px(img, 6, 5, [120, 170, 90]); px(img, 8, 5, [120, 170, 90]);
    px(img, 7, 7, [90, 140, 210]);
    return img;
}

function renderFirework() {
    const img = make(16, 16);
    const body = [200, 60, 50];
    const band = [226, 220, 200];
    rect(img, 6, 5, 9, 11, body);
    rect(img, 6, 7, 9, 8, band);
    rect(img, 6, 10, 9, 10, band);
    rect(img, 6, 4, 9, 4, shade(body, 1.2));
    px(img, 6, 5, shade(body, 0.7)); px(img, 9, 5, shade(body, 0.7));
    px(img, 8, 2, [160, 130, 60]); px(img, 9, 1, [160, 130, 60]);
    return img;
}

function renderFishingRod(cast) {
    const img = make(16, 16);
    const wood = [150, 108, 62];
    const line = [214, 210, 200];
    for (let y = 3; y <= 12; y++) {
        px(img, 7, y, wood);
        px(img, 8, y, shade(wood, 0.8));
    }
    px(img, 7, 2, wood);
    px(img, 6, 13, wood); px(img, 8, 13, wood);
    if (cast) {
        for (let y = 0; y <= 4; y++) px(img, 9 + y, y, line);
        px(img, 14, 5, line);
    } else {
        px(img, 5, 4, line); px(img, 4, 5, line); px(img, 4, 6, line);
    }
    return img;
}

function renderBucket(fill) {
    const img = make(16, 16);
    const steel = [196, 200, 206];
    const dark = [140, 144, 150];
    rect(img, 4, 5, 11, 11, dark);
    rect(img, 5, 4, 10, 5, steel);
    px(img, 4, 6, steel); px(img, 11, 6, steel);
    rect(img, 6, 7, 9, 10, fill || [196, 200, 206]);
    px(img, 5, 3, steel); px(img, 10, 3, steel);
    px(img, 5, 2, steel); px(img, 10, 2, steel);
    return img;
}

function renderPotion(kind) {
    const img = make(16, 16);
    const glass = [216, 208, 188];
    const dark = [150, 142, 124];
    const liquid = kind === "splash" ? [190, 80, 180] : [150, 60, 140];
    rect(img, 6, 2, 9, 2, dark);
    rect(img, 6, 3, 9, 3, glass);
    rect(img, 5, 4, 10, 9, glass);
    rect(img, 6, 10, 9, 12, glass);
    if (kind !== "empty") {
        rect(img, 6, 6, 9, 8, liquid);
        if (kind === "splash") {
            px(img, 3, 6, liquid); px(img, 12, 5, liquid);
            px(img, 4, 9, liquid); px(img, 11, 10, liquid);
        }
    }
    return img;
}

function renderGem(kind) {
    const img = make(16, 16);
    const c = { diamond: [110, 232, 218], emerald: [46, 214, 88], iron: [220, 220, 220], gold: [246, 204, 54], brick: [168, 84, 68], netherbrick: [52, 30, 36] }[kind] || [200, 200, 200];
    const dark = shade(c, 0.72);
    const shape = [
        [7, 1], [6, 2], [8, 2], [5, 3], [9, 3], [4, 4], [10, 4],
        [3, 5], [11, 5], [3, 6], [11, 6], [4, 7], [10, 7], [5, 8], [9, 8],
        [6, 9], [8, 9], [7, 10],
    ];
    for (const [x, y] of shape) {
        px(img, x, y, c);
    }
    px(img, 6, 3, shade(c, 1.2));
    px(img, 7, 4, shade(c, 1.2));
    px(img, 4, 5, shade(c, 0.85));
    px(img, 8, 6, dark);
    return img;
}

function renderGoldenCarrot() {
    const img = make(16, 16);
    const gold = [238, 196, 60];
    for (let y = 3; y <= 12; y++) {
        const w = y < 7 ? 3 : 4;
        for (let x = 7; x < 7 + w; x++) px(img, x, y, gold);
    }
    px(img, 8, 2, [110, 170, 70]); px(img, 9, 2, [110, 170, 70]);
    px(img, 8, 3, shade(gold, 1.15)); px(img, 9, 3, shade(gold, 1.15));
    return img;
}

function renderBowl() {
    const img = make(16, 16);
    const wood = [150, 108, 62];
    rect(img, 5, 6, 10, 6, wood);
    rect(img, 4, 7, 11, 8, wood);
    rect(img, 5, 9, 10, 9, shade(wood, 0.8));
    rect(img, 6, 10, 9, 10, shade(wood, 0.65));
    return img;
}

// ──────────────────────────────────────────────────────────────── GUI
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

function slotRow(y) {
    const row = [];
    for (let x = 8; x <= 152; x += 18) row.push([x, y]);
    return row;
}

function renderGuiInventory() {
    const slots = [];
    const img = make(176, 166);
    rect(img, 0, 0, 175, 165, [21, 24, 31]);
    rect(img, 25, 17, 90, 76, [0, 0, 0, 0]);
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

function renderGuiGeneric(slotsTopRows, height) {
    let slots = [];
    if (height > 166) {
        // 54-slot layout: 6 rows, player inventory pushed down
        for (let r = 0; r < 6; r++) slots.push(...slotRow(18 + r * 18));
        for (const y of [140, 158, 176, 198]) slots.push(...slotRow(y));
    } else {
        for (let r = 0; r < slotsTopRows; r++) slots.push(...slotRow(18 + r * 18));
        for (const y of [84, 102, 120, 142]) slots.push(...slotRow(y));
    }
    return renderContainer(176, height, slots);
}

function renderGuiCrafting() {
    const slots = [];
    for (const x of [30, 48, 66]) for (const y of [18, 36, 54]) slots.push([x, y]);
    slots.push([124, 36]);
    for (const y of [84, 102, 120, 142]) slots.push(...slotRow(y));
    return renderContainer(176, 166, slots);
}

function renderGuiFurnace() {
    const slots = [];
    slots.push([56, 18], [56, 54], [116, 36]);
    for (const y of [84, 102, 120, 142]) slots.push(...slotRow(y));
    const img = renderContainer(176, 166, slots);
    // flame + arrow areas as subtle panels
    rect(img, 59, 39, 69, 50, [30, 34, 44]);
    rect(img, 79, 39, 110, 50, [30, 34, 44]);
    return img;
}

// ──────────────────────────────────────────────────────────── block dispatch
function renderBlock(name) {
    if (name.startsWith("wool_colored_")) return renderWool(name);
    if (name === "hardened_clay.png" || name.startsWith("hardened_clay_stained_")) return renderClay(name);
    if (name === "glass.png" || (name.startsWith("glass_") && !name.startsWith("glass_pane_top_"))) return renderGlass(name);
    if (name.startsWith("glass_pane_top_")) return renderPane(name);
    if (name.startsWith("bed_")) return renderBed(name);
    if (name.startsWith("tnt_")) return renderTnt(name);
    if (name.startsWith("planks_")) return renderPlanks(name);
    if (name.startsWith("log_")) return renderLog(name);
    if (name.endsWith("_ore.png")) return renderOre(name);
    if (name.endsWith("_block.png") && METAL[name.replace("_block.png", "")]) return renderMetal(name);
    if (name === "stonebrick.png") return renderStoneBrick();
    if (name === "cobblestone.png" || name === "cobblestone_mossy.png") return renderCobble();
    if (name === "brick.png") return renderBricks(false);
    if (name === "nether_brick.png") return renderBricks(true);
    if (name === "sandstone_top.png" || name === "sandstone_bottom.png" || name === "sandstone_normal.png" || name === "sandstone_smooth.png" || name === "sandstone_carved.png") return renderSandstone(false);
    if (name.startsWith("red_sandstone_")) return renderSandstone(true);
    if (name === "grass_side.png" || name === "grass_side_snowed.png") return renderGrassSide();
    if (name === "water_still.png" || name === "water_flow.png") return renderWaterLava("water");
    if (name === "lava_still.png" || name === "lava_flow.png") return renderWaterLava("lava");
    if (name.startsWith("quartz_block")) return renderQuartz();
    if (name === "bookshelf.png") return renderBookshelf();
    if (name.startsWith("crafting_table_")) return renderCraftingTable();
    if (name === "furnace_side.png") return renderFurnaceSide();
    if (name === "furnace_front_off.png") return renderFurnaceFront(false);
    if (name === "furnace_front_on.png") return renderFurnaceFront(true);
    if (name === "enderchest_front.png" || name === "enderchest_side.png" || name === "enderchest_top.png") return renderEnderChest();
    return null;
}

const FLAT = {
    "stone.png": BLOCK.stone, "bedrock.png": BLOCK.bedrock, "obsidian.png": BLOCK.obsidian,
    "end_stone.png": BLOCK.endstone, "snow.png": BLOCK.snow, "ice.png": BLOCK.ice,
    "ice_packed.png": BLOCK.packedIce, "sand.png": BLOCK.sand, "red_sand.png": BLOCK.redSand,
    "gravel.png": BLOCK.gravel, "dirt.png": BLOCK.dirt, "coarse_dirt.png": BLOCK.coarseDirt,
    "grass_top.png": BLOCK.grassTop, "dirt_podzol_top.png": BLOCK.podzolTop,
    "glowstone.png": BLOCK.glowstone, "sea_lantern.png": BLOCK.seaLantern,
    "sponge.png": BLOCK.sponge, "sponge_wet.png": shade(BLOCK.sponge, 0.8),
    "slime.png": BLOCK.slime, "netherrack.png": BLOCK.netherrack, "soul_sand.png": BLOCK.soulSand,
    "quartz_ore.png": [150, 150, 150],
};

// ──────────────────────────────────────────────────────────────── main
function resolveJar(args) {
    if (args.length > 0) {
        const p = args[0];
        try {
            readFileSync(p);
            return p;
        } catch {
            console.error("[gen] jar not found: " + p);
            process.exit(1);
        }
    }
    for (const c of JAR_CANDIDATES) {
        try {
            readFileSync(c);
            return c;
        } catch {
            /* try next */
        }
    }
    return null;
}

async function downloadJar() {
    console.log("[gen] downloading vanilla 1.8.9 client jar ...");
    const manifest = await (await fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).json();
    const version = manifest.versions.find((v) => v.id === "1.8.9");
    if (!version) throw new Error("1.8.9 not found in version manifest");
    const meta = await (await fetch(version.url)).json();
    const resp = await fetch(meta.downloads.client.url);
    const buf = Buffer.from(await resp.arrayBuffer());
    writeFileSync("/tmp/minecraft-1.8.9-client.jar", buf);
    return "/tmp/minecraft-1.8.9-client.jar";
}

async function main() {
    const jarPath = resolveJar(process.argv.slice(2)) || (await downloadJar());
    console.log("[gen] using jar: " + jarPath);

    const jarBuf = readFileSync(jarPath);
    const index = readZipIndex(jarBuf);

    mkdirSync(OUT, { recursive: true });
    const base = "assets/minecraft/textures/";
    let written = 0, skipped = 0, flat = 0;

    const blockTargets = ["blocks/"];
    const itemTargets = ["items/"];
    const guiTargets = ["gui/container/"];

    const writePng = (relPath, img) => {
        const file = join(OUT, relPath);
        mkdirSync(dirname(file), { recursive: true });
        writeFileSync(file, encodePng(img.w, img.h, img.data));
        written++;
    };

    // ── blocks ──
    for (const dir of blockTargets) {
        for (const [name] of index) {
            if (!name.startsWith(base + dir) || !name.endsWith(".png")) continue;
            const fileName = name.slice(name.lastIndexOf("/") + 1);
            const rel = base + name.slice(base.length);
            const special = renderBlock(fileName);
            if (special) {
                writePng(rel, special);
                continue;
            }
            const flatColor = FLAT[fileName];
            if (flatColor) {
                writePng(rel, solidImg(flatColor));
                flat++;
                continue;
            }
            let buf;
            try {
                buf = zipEntry(jarBuf, index, name);
            } catch (e) {
                console.error("[gen] zip entry failed: " + name + " -> " + e.message);
                process.exit(1);
            }
            if (!buf) continue;
            const png = decodePng(buf);
            // fully opaque → flat bare-bones color
            let opaque = true;
            let r = 0, g = 0, b = 0, n = 0;
            for (let i = 0; i < png.data.length; i += 4) {
                const a = png.data[i + 3];
                if (a > 0) {
                    r += png.data[i] * a; g += png.data[i + 1] * a; b += png.data[i + 2] * a; n += a;
                }
                if (a < 255) opaque = false;
            }
            if (!opaque || n === 0) {
                skipped++;
                continue;
            }
            const c = [Math.round(r / n), Math.round(g / n), Math.round(b / n)];
            writePng(rel, solidImg(c));
            flat++;
        }
    }

    // ── items ──
    const ITEM_RENDER = {
        "wood_sword.png": () => renderSword("wood"), "stone_sword.png": () => renderSword("stone"),
        "iron_sword.png": () => renderSword("iron"), "gold_sword.png": () => renderSword("gold"),
        "diamond_sword.png": () => renderSword("diamond"),
        "wood_pickaxe.png": () => renderPickaxe("wood"), "stone_pickaxe.png": () => renderPickaxe("stone"),
        "iron_pickaxe.png": () => renderPickaxe("iron"), "gold_pickaxe.png": () => renderPickaxe("gold"),
        "diamond_pickaxe.png": () => renderPickaxe("diamond"),
        "wood_axe.png": () => renderAxe("wood"), "stone_axe.png": () => renderAxe("stone"),
        "iron_axe.png": () => renderAxe("iron"), "gold_axe.png": () => renderAxe("gold"),
        "diamond_axe.png": () => renderAxe("diamond"),
        "wood_shovel.png": () => renderShovel("wood"), "stone_shovel.png": () => renderShovel("stone"),
        "iron_shovel.png": () => renderShovel("iron"), "gold_shovel.png": () => renderShovel("gold"),
        "diamond_shovel.png": () => renderShovel("diamond"),
        "wood_hoe.png": () => renderHoe("wood"), "stone_hoe.png": () => renderHoe("stone"),
        "iron_hoe.png": () => renderHoe("iron"), "gold_hoe.png": () => renderHoe("gold"),
        "diamond_hoe.png": () => renderHoe("diamond"),
        "bow_standby.png": () => renderBow(0), "bow_pulling_0.png": () => renderBow(1),
        "bow_pulling_1.png": () => renderBow(2), "bow_pulling_2.png": () => renderBow(3),
        "arrow.png": renderArrow, "apple.png": () => renderApple(false), "apple_golden.png": () => renderApple(true),
        "bread.png": renderBread, "ender_pearl.png": renderEnderPearl, "ender_eye.png": renderEnderEye,
        "egg.png": renderEgg, "snowball.png": renderSnowball, "shears.png": renderShears,
        "compass.png": renderCompass, "clock.png": renderClock, "stick.png": renderStick,
        "string.png": renderString, "paper.png": renderPaper, "map_empty.png": renderMap,
        "fireworks.png": renderFirework, "fishing_rod_uncast.png": () => renderFishingRod(false),
        "fishing_rod_cast.png": () => renderFishingRod(true),
        "bucket_empty.png": () => renderBucket(null), "bucket_water.png": () => renderBucket([62, 122, 232]),
        "bucket_lava.png": () => renderBucket([236, 128, 38]), "bucket_milk.png": () => renderBucket([242, 240, 230]),
        "potion_bottle_empty.png": () => renderPotion("empty"), "potion_bottle_drinkable.png": () => renderPotion("drinkable"),
        "potion_bottle_splash.png": () => renderPotion("splash"),
        "diamond.png": () => renderGem("diamond"), "emerald.png": () => renderGem("emerald"),
        "iron_ingot.png": () => renderGem("iron"), "gold_ingot.png": () => renderGem("gold"),
        "brick.png": () => renderGem("brick"), "netherbrick.png": () => renderGem("netherbrick"),
        "carrot_golden.png": renderGoldenCarrot, "bowl.png": renderBowl,
    };

    for (const dir of itemTargets) {
        for (const [name] of index) {
            if (!name.startsWith(base + dir) || !name.endsWith(".png")) continue;
            const fileName = name.slice(name.lastIndexOf("/") + 1);
            if (fileName.endsWith(".png.mcmeta")) continue;
            const render = ITEM_RENDER[fileName];
            if (!render) continue;
            writePng(base + name.slice(base.length), render());
        }
    }

    // ── GUI containers ──
    const GUI_RENDER = {
        "inventory.png": renderGuiInventory,
        "chest.png": () => renderGuiGeneric(3, 166),
        "generic_54.png": () => renderGuiGeneric(6, 222),
        "crafting_table.png": renderGuiCrafting,
        "furnace.png": renderGuiFurnace,
    };
    for (const dir of guiTargets) {
        for (const [name] of index) {
            if (!name.startsWith(base + dir) || !name.endsWith(".png")) continue;
            const fileName = name.slice(name.lastIndexOf("/") + 1);
            const render = GUI_RENDER[fileName];
            if (!render) continue;
            writePng(base + name.slice(base.length), render());
        }
    }

    // ── pack.mcmeta + icon + readme ──
    writeFileSync(join(OUT, "pack.mcmeta"), JSON.stringify({
        pack: {
            pack_format: 1,
            description: "Qyn-L BedWars PvP — clean Bare Bones style for 1.8.9",
        },
    }, null, 2) + "\n");
    writeFileSync(join(OUT, "pack.png"), encodePng(128, 128, packIcon()));
    writeFileSync(join(OUT, "README.md"),
        "# Qyn-L BedWars PvP (1.8.9)\n\n" +
        "Clean Bare Bones style pack tuned for BedWars:\n" +
        "- Saturated flat wool (all 16 layer colors) with a subtle weave\n" +
        "- Muted terracotta, near-invisible bordered glass + panes\n" +
        "- Clean red bed, TNT with letters, seamed planks/logs, flat ores/metal\n" +
        "- Clean sword/tool/item icons and a dark container GUI\n" +
        "- Every other solid block is a flat authentic-vanilla-color texture\n\n" +
        "Place the folder in `.minecraft/resourcepacks/` (or your launcher's pack folder).\n");

    console.log(`[gen] wrote ${written} textures to ${OUT}`);
    console.log(`[gen]   flat/auto: ${flat}, hand-tuned: ${written - flat}, vanilla-skipped: ${skipped}`);
}

function packIcon() {
    const img = make(128, 128);
    rect(img, 0, 0, 127, 127, [20, 22, 28]);
    rect(img, 16, 16, 111, 111, [161, 39, 34]);
    rect(img, 22, 22, 105, 105, [196, 52, 44]);
    const cx = 64, cy = 64;
    for (let i = 0; i <= 32; i++) {
        rect(img, cx - i, cy - (32 - i), cx + i, cy - (32 - i), [240, 240, 240]);
        rect(img, cx - i, cy + (32 - i), cx + i, cy + (32 - i), [240, 240, 240]);
    }
    for (let y = 20; y <= 108; y += 2) {
        const x = 108 - y;
        px(img, x, y, [78, 224, 208]);
        px(img, x + 1, y, [78, 224, 208]);
        px(img, x + 1, y + 1, [46, 178, 164]);
    }
    return img.data;
}

main().catch((e) => {
    console.error("[gen] failed: " + e);
    process.exit(1);
});
