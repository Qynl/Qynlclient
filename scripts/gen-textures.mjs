// QynlClient — generates the "Qynl Performance+" resource pack textures and the mod icon.
// Run with: bun scripts/gen-textures.mjs  (or node scripts/gen-textures.mjs)
import { deflateSync } from "node:zlib";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const PACK = join(here, "..", "resourcepack");
const ICON = join(here, "..", "src", "main", "resources", "assets", "qynlclient");

// ---------------------------------------------------------------- PNG encoder
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

function png(width, height, rgba) {
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(width, 0);
	ihdr.writeUInt32BE(height, 4);
	ihdr[8] = 8; // bit depth
	ihdr[9] = 6; // RGBA
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

// ---------------------------------------------------------------- helpers
function mulberry(seed) {
	return () => {
		seed |= 0;
		seed = (seed + 0x6d2b79f5) | 0;
		let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
		t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
		return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
	};
}

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const hex = (h) => [
	parseInt(h.slice(1, 3), 16),
	parseInt(h.slice(3, 5), 16),
	parseInt(h.slice(5, 7), 16),
];

function makeTexture(size, seed, fn) {
	const rnd = mulberry(seed);
	const rgba = Buffer.alloc(size * size * 4);
	for (let y = 0; y < size; y++) {
		for (let x = 0; x < size; x++) {
			const c = fn(x, y, rnd);
			const i = (y * size + x) * 4;
			rgba[i] = clamp(Math.round(c[0]), 0, 255);
			rgba[i + 1] = clamp(Math.round(c[1]), 0, 255);
			rgba[i + 2] = clamp(Math.round(c[2]), 0, 255);
			rgba[i + 3] = c[3] === undefined ? 255 : clamp(Math.round(c[3]), 0, 255);
		}
	}
	return png(size, size, rgba);
}

function flat([r, g, b], amt, rnd) {
	return [r + (rnd() - 0.5) * 2 * amt, g + (rnd() - 0.5) * 2 * amt, b + (rnd() - 0.5) * 2 * amt];
}

const TEX = (name, size, fn) => ({ name, size, fn });

// ---------------------------------------------------------------- textures
const textures = [
	// Stone family — flat, low-noise surfaces to cut fill rate and look clean.
	TEX("stone", 16, (x, y, rnd) => flat(hex("#8b8d8f"), 6, rnd)),
	TEX("cobblestone", 16, (x, y, rnd) =>
		rnd() < 0.06 ? flat(hex("#6f7175"), 8, rnd) : flat(hex("#7c7f82"), 10, rnd)),
	TEX("dirt", 16, (x, y, rnd) =>
		rnd() < 0.05 ? flat(hex("#5f4028"), 6, rnd) : flat(hex("#7a5236"), 8, rnd)),
	TEX("grass_block_top", 16, (x, y, rnd) =>
		rnd() < 0.04 ? flat(hex("#7fc85e"), 5, rnd) : flat(hex("#63a544"), 5, rnd)),
	TEX("grass_block_side", 16, (x, y, rnd) => {
		const grassDepth = 4 + Math.round(rnd() * 2);
		if (y < grassDepth) {
			return rnd() < 0.1 ? flat(hex("#7fc85e"), 5, rnd) : flat(hex("#63a544"), 5, rnd);
		}
		if (y === grassDepth && rnd() < 0.25) {
			return flat(hex("#63a544"), 5, rnd); // ragged grass edge
		}
		return rnd() < 0.05 ? flat(hex("#5f4028"), 6, rnd) : flat(hex("#7a5236"), 8, rnd);
	}),
	TEX("sand", 16, (x, y, rnd) => flat(hex("#dcd3a3"), 7, rnd)),
	TEX("gravel", 16, (x, y, rnd) => {
		const p = rnd();
		if (p < 0.08) return flat(hex("#6f675f"), 14, rnd);
		if (p > 0.92) return flat(hex("#a29a90"), 14, rnd);
		return flat(hex("#8b8278"), 12, rnd);
	}),
	TEX("snow", 16, (x, y, rnd) => flat(hex("#eef3f6"), 4, rnd)),
	TEX("ice", 16, (x, y, rnd) => flat(hex("#a8d8f0"), 3, rnd).concat(200)),
	TEX("oak_planks", 16, (x, y) => {
		const base = hex("#a88555");
		const seam = hex("#8a6c42");
		const plankRow = Math.floor(y / 4);
		const offset = (plankRow * 3) % 16;
		const isHSeam = y % 4 === 0;
		const isVSeam = (x - offset + 16) % 16 === 0;
		return isHSeam || isVSeam ? seam : base;
	}),
	TEX("oak_log", 16, (x, y, rnd) => {
		const base = flat(hex("#6b4f2d"), 6, rnd);
		if ((x + Math.round(rnd() * 2)) % 4 === 0) return flat(hex("#57401f"), 6, rnd);
		return base;
	}),
	TEX("oak_log_top", 16, (x, y) => {
		const cx = 7.5, cy = 7.5;
		const d = Math.hypot(x - cx, y - cy);
		if (d > 7) return hex("#6b4f2d");
		return Math.floor(d * 2) % 2 === 0 ? hex("#a98a5c") : hex("#8a6a42");
	}),
	// Glass — clean, near-invisible glass for performance and looks.
	TEX("glass", 16, (x, y) => {
		const border = x === 0 || y === 0 || x === 15 || y === 15;
		const corner = (x <= 2 && y <= 2) || (x >= 13 && y <= 2) || (x <= 2 && y >= 13) || (x >= 13 && y >= 13);
		if (corner) return [255, 255, 255, 160];
		if (border) return [235, 245, 255, 120];
		return [255, 255, 255, 0];
	}),
	TEX("glass_pane_top", 16, (x, y) => {
		if (y < 3 || y > 12) return [235, 245, 255, 140];
		return [255, 255, 255, 0];
	}),
	TEX("water_still", 16, (x, y, rnd) => {
		const base = flat(hex("#3156b6"), 5, rnd);
		if ((y + Math.round(rnd() * 1)) % 5 === 0) return [base[0] + 12, base[1] + 16, base[2] + 10];
		return base;
	}),
	TEX("water_flow", 16, (x, y, rnd) => {
		const base = flat(hex("#2f52ad"), 6, rnd);
		const wave = Math.round((x + y * 0.7) % 4);
		if (wave === 0) return [base[0] + 14, base[1] + 18, base[2] + 12];
		return base;
	}),
	// Ore blocks — smooth metallic gradients.
	TEX("iron_block", 16, (x, y, rnd) => {
		const t = y / 15;
		const base = [142 + t * 30 + (rnd() - 0.5) * 4, 148 + t * 28 + (rnd() - 0.5) * 4, 156 + t * 26 + (rnd() - 0.5) * 4];
		return base;
	}),
	TEX("gold_block", 16, (x, y, rnd) => {
		const t = y / 15;
		const base = [247 - t * 15 + (rnd() - 0.5) * 6, 207 - t * 18 + (rnd() - 0.5) * 6, 62 - t * 6 + (rnd() - 0.5) * 4];
		return base;
	}),
	TEX("diamond_block", 16, (x, y, rnd) => {
		const t = y / 15;
		const base = [84 - t * 22 + (rnd() - 0.5) * 6, 217 - t * 26 + (rnd() - 0.5) * 6, 212 - t * 24 + (rnd() - 0.5) * 6];
		return base;
	}),
	// Environment — crisp sun and moon.
	TEX("sun", 16, (x, y) => {
		const d = Math.hypot(x - 7.5, y - 7.5);
		if (d <= 6) return hex("#ffd75e");
		if (d <= 7.5) return [255, 215, 94, 90];
		return [255, 255, 255, 0];
	}),
	TEX("moon", 16, (x, y, rnd) => {
		const d = Math.hypot(x - 7.5, y - 7.5);
		if (d <= 6) {
			const crater = Math.hypot(x - (6 + Math.round(rnd() * 3)), y - (4 + Math.round(rnd() * 3)));
			if (crater <= 1.6) return [190, 195, 208];
			return [230, 233, 240];
		}
		if (d <= 7.5) return [230, 233, 240, 80];
		return [255, 255, 255, 0];
	}),
];

// ---------------------------------------------------------------- write pack
const packMeta = {
	pack: {
		pack_format: 34,
		description: "\u00a7aQynl Performance+\u00a77 \u2014 clean, high-FPS textures \u00a78| \u00a771.21.1",
	},
};
writeFileSync(join(PACK, "pack.mcmeta"), JSON.stringify(packMeta, null, 2) + "\n");

for (const tex of textures) {
	const dir = join(PACK, "assets", "minecraft", "textures",
		tex.name === "sun" || tex.name === "moon" ? "environment" : "block");
	mkdirSync(dir, { recursive: true });
	writeFileSync(join(dir, tex.name + ".png"), makeTexture(tex.size, seedOf(tex.name), tex.fn));
	console.log("wrote", join(dir, tex.name + ".png"));
}

// Pack icon (128x128) — dark slate with a lime toggle/target mark.
const iconRgba = Buffer.alloc(128 * 128 * 4);
for (let y = 0; y < 128; y++) {
	for (let x = 0; x < 128; x++) {
		const i = (y * 128 + x) * 4;
		const d = Math.hypot(x - 64, y - 64);
		if (d > 63) {
			// rounded corners -> transparent
			iconRgba[i] = 0; iconRgba[i + 1] = 0; iconRgba[i + 2] = 0; iconRgba[i + 3] = 0;
			continue;
		}
		const inner = d <= 50;
		const ring = !inner;
		const mark = Math.hypot(x - 64, y - 64) <= 16;
		if (ring) { iconRgba[i] = 13; iconRgba[i + 1] = 17; iconRgba[i + 2] = 23; iconRgba[i + 3] = 255; }
		else if (mark) { iconRgba[i] = 13; iconRgba[i + 1] = 17; iconRgba[i + 2] = 23; iconRgba[i + 3] = 255; }
		else { iconRgba[i] = 110; iconRgba[i + 1] = 231; iconRgba[i + 2] = 160; iconRgba[i + 3] = 255; }
	}
}
const packPng = png(128, 128, iconRgba);
writeFileSync(join(PACK, "pack.png"), packPng);
console.log("wrote", join(PACK, "pack.png"));

mkdirSync(ICON, { recursive: true });
writeFileSync(join(ICON, "icon.png"), packPng);
console.log("wrote", join(ICON, "icon.png"));

function seedOf(name) {
	let h = 0;
	for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) | 0;
	return h & 0x7fffffff;
}

console.log("Qynl Performance+ pack generated.");
