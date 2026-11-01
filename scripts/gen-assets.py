#!/usr/bin/env python3
"""Generate Tekkit Cells' data and assets: recipes, item/drive models, lang, and the textures.

    python3 scripts/gen-assets.py        # rewrites src/main/resources/{data,assets}/tekkitcells

The textures are drawn here pixel by pixel (no AE2 artwork is copied; AE2's textures are not MIT). The item models
reuse AE2's status-LED overlay (ae2:item/storage_cell_led) by reference, as AE2's own cells do, so the LED shows the
cell state in inventories. The cost table these recipes produce is computed and checked by
src/test/java/net/moderntekkit/cells/CostTableTest.java.
"""
import json
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
NS = "tekkitcells"

# id, label, kilobytes, previous cell, previous component, colours (light, mid, dark)
TIERS = [
    ("1m", "1M", 1024, "ae2:item_storage_cell_256k", "ae2:cell_component_256k", ((255, 214, 120), (255, 176, 46), (196, 118, 18))),
    ("4m", "4M", 4096, f"{NS}:item_storage_cell_1m", f"{NS}:cell_component_1m", ((255, 150, 112), (255, 106, 61), (196, 58, 30))),
    ("16m", "16M", 16384, f"{NS}:item_storage_cell_4m", f"{NS}:cell_component_4m", ((246, 128, 192), (224, 64, 154), (156, 30, 104))),
    ("32m", "32M", 32768, f"{NS}:item_storage_cell_16m", f"{NS}:cell_component_16m", ((196, 160, 255), (155, 89, 255), (98, 46, 188))),
]
HOUSING = "ae2:item_cell_housing"


def write_json(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


# ---------------------------------------------------------------- recipes
def recipes() -> None:
    rdir = ROOT / f"data/{NS}/recipe"
    for f in rdir.rglob("*.json") if rdir.exists() else []:
        f.unlink()
    for tid, _label, _kb, prev_cell, prev_comp, _c in TIERS:
        comp = f"{NS}:cell_component_{tid}"
        cell = f"{NS}:item_storage_cell_{tid}"
        # Cost rule (notes/tekkit-cells.md): each tier costs (capacity ratio x 1.25) x the previous tier's raw
        # materials. cell = housing + component, so for a x4 step (x5 cost) the component is 5 previous components + 4
        # housings; for the x2 step to 32M (x2.5 cost) 5 previous components + 3 housings make two components.
        if tid == "32m":
            pattern, count = ["chc", "hch", "c c"], 2
        else:
            pattern, count = ["chc", "hch", "chc"], 1
        result = {"id": comp}
        if count != 1:
            result["count"] = count
        write_json(rdir / f"cell_component_{tid}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "key": {"c": prev_comp, "h": HOUSING},
            "pattern": pattern,
            "result": result,
        })
        # Cell from housing + component, and the full shaped recipe (same raw materials as the housing), like AE2.
        write_json(rdir / f"item_storage_cell_{tid}_storage.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [HOUSING, comp],
            "result": {"id": cell},
        })
        write_json(rdir / f"item_storage_cell_{tid}.json", {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "key": {"a": "ae2:quartz_glass", "b": "#c:dusts/redstone", "c": comp,
                    "d": "#c:ingots/iron", "e": "#c:ingots/copper"},
            "pattern": ["aba", "bcb", "ded"],
            "result": {"id": cell},
        })
        # Shift-right-click an empty cell to take it apart, like AE2's cells.
        write_json(rdir / f"cell_upgrade/item_storage_cell_{tid}.json", {
            "type": "ae2:storage_cell_disassembly",
            "cell": cell,
            "cell_disassembly_items": [{"id": HOUSING}, {"id": comp}],
        })
        # Previous tier's cell + this component -> this cell, keeping contents and upgrades; gives the old component back.
        prev_tid = prev_cell.rsplit("_", 1)[1]
        write_json(rdir / f"upgrade/item_storage_cell_{prev_tid}_to_{tid}.json", {
            "type": "ae2:storage_cell_upgrade",
            "input_cell": prev_cell,
            "input_component": comp,
            "result_cell": cell,
            "result_component": prev_comp,
        })


# ---------------------------------------------------------------- models + lang
def models_and_lang() -> None:
    a = ROOT / f"assets/{NS}"
    lang = {}
    for i, (tid, label, _kb, *_rest) in enumerate(TIERS):
        cell = f"item_storage_cell_{tid}"
        comp = f"cell_component_{tid}"
        write_json(a / f"items/{cell}.json", {"model": {
            "type": "minecraft:model", "model": f"{NS}:item/{cell}",
            "tints": [{"type": "minecraft:constant", "value": -1}, {"type": "ae2:storage_cell_state"}]}})
        write_json(a / f"models/item/{cell}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{cell}", "layer1": "ae2:item/storage_cell_led"}})
        write_json(a / f"items/{comp}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{comp}"}})
        write_json(a / f"models/item/{comp}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{comp}"}})
        # The cell as drawn in an ME drive slot: a 6x2x2 box, front face = row pair i of drive_cells.png.
        v = 2 * i
        write_json(a / f"models/block/drive_{tid}_item_cell.json", {
            "ambientocclusion": False,
            "textures": {"particle": f"{NS}:block/drive_cells", "cell": f"{NS}:block/drive_cells"},
            "elements": [{
                "from": [0, 0, 0], "to": [6, 2, 2],
                "faces": {
                    "north": {"uv": [0, v, 6, v + 2], "texture": "#cell", "cullface": "north"},
                    "up": {"uv": [6, 8, 0, 10], "texture": "#cell", "cullface": "north"},
                    "down": {"uv": [6, 8, 0, 10], "texture": "#cell", "cullface": "north"},
                },
            }],
        })
        lang[f"item.{NS}.{cell}"] = f"{label} ME Item Storage Cell"
        lang[f"item.{NS}.{comp}"] = f"{label} ME Storage Component"
    write_json(a / "lang/en_us.json", lang)


# ---------------------------------------------------------------- textures (own pixel art)
def png(path: Path, px) -> None:
    """px: 16 rows of 16 RGBA tuples (or None = transparent)."""
    raw = b"".join(b"\0" + b"".join(bytes(p if p else (0, 0, 0, 0)) for p in row) for row in px)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


OUTLINE = (43, 45, 51)
BODY_HI = (214, 217, 224)
BODY = (182, 186, 197)
BODY_LO = (140, 145, 158)
SOCKET = (24, 24, 28)


def cell_texture(col):
    light, mid, dark = col
    px = [[None] * 16 for _ in range(16)]
    for y in range(1, 15):
        for x in range(3, 13):
            corner = (x in (3, 12)) and (y in (1, 14))
            if corner:
                continue
            edge = x in (3, 12) or y in (1, 14) or ((x in (4, 11)) and (y in (1, 14)))
            if edge:
                px[y][x] = rgba(OUTLINE)
            else:
                shade = BODY_HI if x == 4 or y == 2 else (BODY_LO if x == 11 or y == 13 else BODY)
                px[y][x] = rgba(shade)
    for x in range(4, 12):  # tier-coloured cap
        px[2][x] = rgba(light)
        px[3][x] = rgba(mid)
        px[4][x] = rgba(dark)
    for y in range(6, 10):  # window
        for x in range(5, 11):
            px[y][x] = rgba(OUTLINE if y in (6, 9) or x in (5, 10) else (mid if (x + y) % 3 else light))
    for x in range(5, 8):  # label grooves
        px[11][x] = rgba(BODY_LO)
        px[12][x] = rgba(BODY_LO)
    px[11][9] = rgba(SOCKET)  # LED socket: ae2:item/storage_cell_led draws at (9, 11)
    return px


def component_texture(col):
    light, mid, dark = col
    px = [[None] * 16 for _ in range(16)]
    pin = (160, 166, 176)
    for i in range(4, 12, 2):  # pins on all four sides
        px[2][i] = px[13][i] = px[i][2] = px[i][13] = rgba(pin)
    for y in range(3, 13):
        for x in range(3, 13):
            px[y][x] = rgba((58, 61, 70) if (x in (3, 12) or y in (3, 12)) else (36, 38, 44))
    for y in range(5, 11):
        for x in range(5, 11):
            if x == 5 or y == 5:
                c = light
            elif x == 10 or y == 10:
                c = dark
            else:
                c = mid
            px[y][x] = rgba(c)
    px[7][7] = px[8][8] = rgba(light)
    return px


def drive_texture():
    px = [[(255, 255, 255, 255)] * 16 for _ in range(16)]
    for i, (*_x, col) in enumerate(TIERS):
        light, mid, dark = col
        for dy in range(2):
            y = 2 * i + dy
            row = [BODY_HI, BODY if dy else BODY_HI, BODY_HI, light if dy == 0 else mid,
                   mid if dy == 0 else dark, BODY_LO]
            for x, c in enumerate(row):
                px[y][x] = rgba(c)
            px[y][1] = rgba(SOCKET if dy else BODY)
    for y in (8, 9):  # top/bottom faces of the slot box
        for x in range(6):
            px[y][x] = rgba(BODY if (x + y) % 2 else BODY_LO)
    return px


def textures() -> None:
    t = ROOT / f"assets/{NS}/textures"
    for tid, _l, _kb, _pc, _pp, col in TIERS:
        png(t / f"item/item_storage_cell_{tid}.png", cell_texture(col))
        png(t / f"item/cell_component_{tid}.png", component_texture(col))
    png(t / "block/drive_cells.png", drive_texture())


def nbt_empty_structure(path: Path, size=(5, 3, 5)) -> None:
    """Empty structure for the GameTests (gzip NBT; no DataVersion, so the game upgrades it from the oldest)."""
    import gzip

    def name(n):
        b = n.encode()
        return struct.pack(">H", len(b)) + b

    def tag(t, n, payload):
        return bytes([t]) + name(n) + payload

    body = tag(9, "size", bytes([3]) + struct.pack(">i", 3) + b"".join(struct.pack(">i", v) for v in size))
    body += tag(9, "palette", bytes([10]) + struct.pack(">i", 1) + tag(8, "Name", name("minecraft:air")) + b"\0")
    body += tag(9, "blocks", bytes([10]) + struct.pack(">i", 0))
    body += tag(9, "entities", bytes([10]) + struct.pack(">i", 0))
    data = tag(10, "", body + b"\0")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(data, mtime=0))


if __name__ == "__main__":
    nbt_empty_structure(ROOT.parent.parent / "gametest/resources/data/tekkitcellstests/structure/empty.nbt")
    recipes()
    models_and_lang()
    textures()
    print("generated into", ROOT, file=sys.stderr)
