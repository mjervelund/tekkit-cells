# Tekkit Cells

Bigger [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) storage cells for
Minecraft 26.3 / NeoForge 26.3.0.99: **1M, 4M, 16M and 32M** ME Item Storage Cells, ME Fluid Storage Cells and their
storage components.

Built for the Modern Tekkit server. AI-assisted: written with Claude (Anthropic) and reviewed by the maintainer.

## What it adds

| Cell | Bytes | Bytes per type | Types | Idle drain (AE/t) | Items (1 type) | Items (63 types) |
|---|---:|---:|---:|---:|---:|---:|
| 1M | 1,048,576 | 8,192 | 63 | 3.0 | 8,323,072 | 4,259,840 |
| 4M | 4,194,304 | 32,768 | 63 | 3.5 | 33,292,288 | 17,039,360 |
| 16M | 16,777,216 | 131,072 | 63 | 4.0 | 133,169,152 | 68,157,440 |
| 32M | 33,554,432 | 262,144 | 63 | 4.25 | 266,338,304 | 136,314,880 |

Fluid cells of the same sizes (same bytes, bytes per type and idle drain) hold **18 types**, like AE2's fluid cells;
AE2 stores 8 buckets per byte (where an item cell stores 8 items), so a fluid cell holds as many buckets as the item
cell of its size holds items of one type:

| Fluid cell | Types | Buckets (1 type) | Buckets (18 types) |
|---|---:|---:|---:|
| 1M | 18 | 8,323,072 | 7,208,960 |
| 4M | 18 | 33,292,288 | 28,835,840 |
| 16M | 18 | 133,169,152 | 115,343,360 |
| 32M | 18 | 266,338,304 | 230,686,720 |

The cells are AE2's own `BasicStorageCell` with AE2's scaling (bytes per type = bytes / 128, 63 types, idle drain
+0.25 AE/t per doubling of capacity), so they work like AE2's 1k..256k cells everywhere: ME drives and ME chests (own
drive-slot colour, AE2's status LEDs), the cell tooltip, the cell workbench and the same upgrade cards (fuzzy, inverter,
equal distribution, overflow destruction; fluid cells take the same minus the fuzzy card, as in AE2). Like AE2's cells they can be taken apart when empty (sneak + use) and
upgraded in place (previous-tier cell + next component, crafted together, keeps contents and gives the old component
back).

## Recipes and cost

Each tier costs **(capacity ratio x 1.25) times the raw materials of the previous tier**: 1M = 5 x 256k, 4M = 5 x 1M,
16M = 5 x 4M, 32M = 2.5 x 16M. A cell is a housing plus a component, as in AE2, so:

- 1M / 4M / 16M component: 5 components of the previous tier + 4 ME Item Cell Housings (shaped, `chc/hch/chc`);
- 32M component: 5 x 16M components + 3 housings make **two** 32M components (`chc/hch/c c`);
- cell: ME Item Cell Housing + component (shapeless), or AE2's shaped cell recipe around the component.

This makes the raw bill exactly the rule, for every material. Raw = the base resources below every AE2 recipe (presses
are tools and not counted). The table is computed from the recipe JSONs (AE2's from its jar, ours from
`src/main/resources`) by `CostTableTest`, which fails the build if a tier is off the rule by more than 5% or if this
table is out of date:

<!-- cost-table:begin -->
| Tier | Certus quartz | Redstone | Glowstone dust | Gold ingot | Iron ingot | Copper ingot | Glass | Sky stone | Total items | Ratio vs previous (each material / total) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 256k (AE2) | 537.50 | 556 | 48 | 81 | 2 | 1 | 42 | 4 | 1,271.50 | base |
| 1M | 2,687.50 | 2,780 | 240 | 405 | 10 | 5 | 210 | 20 | 6,357.50 | x5.000 / x5.000 (rule x5.00) |
| 4M | 13,437.50 | 13,900 | 1,200 | 2,025 | 50 | 25 | 1,050 | 100 | 31,787.50 | x5.000 / x5.000 (rule x5.00) |
| 16M | 67,187.50 | 69,500 | 6,000 | 10,125 | 250 | 125 | 5,250 | 500 | 158,937.50 | x5.000 / x5.000 (rule x5.00) |
| 32M | 167,968.75 | 173,750 | 15,000 | 25,312.50 | 625 | 312.50 | 13,125 | 1,250 | 397,343.75 | x2.500 / x2.500 (rule x2.50) |
<!-- cost-table:end -->

Fluid cells use the same components, as AE2's do: cell = ME Fluid Cell Housing + component (shapeless), or AE2's
shaped fluid cell recipe (copper bottom row) around the component; upgrade in place from the previous fluid cell. The
one difference to the rule is in the component: it contains item cell housings (2 iron + 1 copper) where 5 fluid
cells would contain fluid cell housings (3 copper), so iron and copper trade places; counted together they follow the
rule exactly, as does every other material and the total:

<!-- fluid-cost-table:begin -->
| Tier | Certus quartz | Redstone | Glowstone dust | Gold ingot | Iron ingot | Copper ingot | Glass | Sky stone | Total items | Ratio vs previous (each material, iron + copper ingots together / total) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 256k (AE2) | 537.50 | 556 | 48 | 81 | 0 | 3 | 42 | 4 | 1,271.50 | base |
| 1M | 2,687.50 | 2,780 | 240 | 405 | 8 | 7 | 210 | 20 | 6,357.50 | x5.000 / x5.000 (rule x5.00) |
| 4M | 13,437.50 | 13,900 | 1,200 | 2,025 | 48 | 27 | 1,050 | 100 | 31,787.50 | x5.000 / x5.000 (rule x5.00) |
| 16M | 67,187.50 | 69,500 | 6,000 | 10,125 | 248 | 127 | 5,250 | 500 | 158,937.50 | x5.000 / x5.000 (rule x5.00) |
| 32M | 167,968.75 | 173,750 | 15,000 | 25,312.50 | 623 | 314.50 | 13,125 | 1,250 | 397,343.75 | x2.500 / x2.500 (rule x2.50) |
<!-- fluid-cost-table:end -->

## Building

Needs Java 25. The add-on compiles and runs against AE2 26.3.1-alpha and GuideME 26.3.6-alpha: the jars in `libs/`
if present, otherwise the same releases from Modrinth's maven.

    scripts/fetch-libs.sh            # optional: AE2_JAR=<path> puts a locally built AE2 into libs/
    ./gradlew build                  # jar in build/libs, runs the cost-table test
    ./gradlew runGameTestServer      # GameTests: 32M item/fluid cells in an ME drive, all tiers' capacity, recipes load

`scripts/gen-assets.py` regenerates the recipes, models, lang and textures (the textures are drawn by that script;
no AE2 artwork is included).

## License

MIT (see `LICENSE`). Applied Energistics 2 is LGPL-3.0 and is not included; this add-on only uses its API at runtime.
