package net.moderntekkit.cells;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Computes the raw-material bill of every cell tier from the recipe JSONs (AE2's from its jar, ours from
 * src/main/resources) and checks the cost rule: each tier costs (capacity ratio x 1.25) times the raw materials of the
 * previous tier, per material and in total, within 5%. Also checks that README.md's cost table is the computed one.
 * <p>
 * "Raw" = the leaves below: base resources that are mined/smelted, not crafted in AE2. Presses are tools (not consumed
 * in "inscribe" mode) and are not counted.
 */
class CostTableTest {
    static final List<String> RAW = List.of(
            "ae2:certus_quartz_crystal", "minecraft:redstone", "minecraft:glowstone_dust", "minecraft:gold_ingot",
            "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:glass", "ae2:sky_stone_block");
    static final Map<String, String> RAW_LABEL = Map.of(
            "ae2:certus_quartz_crystal", "Certus quartz", "minecraft:redstone", "Redstone",
            "minecraft:glowstone_dust", "Glowstone dust", "minecraft:gold_ingot", "Gold ingot",
            "minecraft:iron_ingot", "Iron ingot", "minecraft:copper_ingot", "Copper ingot",
            "minecraft:glass", "Glass", "ae2:sky_stone_block", "Sky stone");

    /** Tag -> the item it stands for in the bill (the cheapest/canonical member). */
    static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("c:dusts/redstone", "minecraft:redstone"),
            Map.entry("c:dusts/glowstone", "minecraft:glowstone_dust"),
            Map.entry("c:ingots/iron", "minecraft:iron_ingot"),
            Map.entry("c:ingots/copper", "minecraft:copper_ingot"),
            Map.entry("c:ingots/gold", "minecraft:gold_ingot"),
            Map.entry("c:glass_blocks/cheap", "minecraft:glass"),
            Map.entry("c:dusts/sky_stone", "ae2:sky_dust"),
            Map.entry("c:dusts/certus_quartz", "ae2:certus_quartz_dust"),
            Map.entry("ae2:all_quartz_dust", "ae2:certus_quartz_dust"),
            Map.entry("ae2:all_certus_quartz", "ae2:certus_quartz_crystal"),
            Map.entry("c:gems/certus_quartz", "ae2:certus_quartz_crystal"),
            Map.entry("c:silicon", "ae2:silicon"));

    /** When an item has several recipes with different bills, the one AE2's progression uses. */
    static final Map<String, String> PREFERRED = Map.of();

    record Tier(String label, String cell, double target) {
    }

    static final List<Tier> TIERS = List.of(
            new Tier("256k (AE2)", "ae2:item_storage_cell_256k", 0),
            new Tier("1M", "tekkitcells:item_storage_cell_1m", 4 * 1.25),
            new Tier("4M", "tekkitcells:item_storage_cell_4m", 4 * 1.25),
            new Tier("16M", "tekkitcells:item_storage_cell_16m", 4 * 1.25),
            new Tier("32M", "tekkitcells:item_storage_cell_32m", 2 * 1.25));

    record Recipe(String id, String output, long count, Map<String, Long> inputs) {
    }

    final Map<String, List<Recipe>> byOutput = new HashMap<>();
    final Map<String, Map<String, Frac>> memo = new HashMap<>();

    @Test
    void costRuleHolds() throws IOException {
        loadAe2(Path.of(System.getProperty("tekkitcells.ae2Jar")));
        loadDir(Path.of(System.getProperty("tekkitcells.resources"), "data"));

        var bills = new ArrayList<Map<String, Frac>>();
        for (var tier : TIERS) {
            bills.add(bill(tier.cell, new HashSet<>()));
        }
        var table = table(bills);
        System.out.println(table);
        var out = Path.of(System.getProperty("tekkitcells.tableOut"));
        Files.createDirectories(out.getParent());
        Files.writeString(out, table);

        var problems = new ArrayList<String>();
        for (int i = 1; i < TIERS.size(); i++) {
            var tier = TIERS.get(i);
            var prev = bills.get(i - 1);
            var cur = bills.get(i);
            if (!prev.keySet().equals(cur.keySet())) {
                problems.add(tier.label + " uses other raw materials than the previous tier: " + cur.keySet() + " vs "
                        + prev.keySet());
            }
            for (var mat : cur.keySet()) {
                var p = prev.get(mat);
                if (p == null) {
                    continue;
                }
                double r = cur.get(mat).div(p).toDouble();
                if (Math.abs(r / tier.target - 1) > 0.05) {
                    problems.add(String.format("%s %s: x%.3f, want x%.2f +-5%%", tier.label, mat, r, tier.target));
                }
            }
            double rt = total(cur).div(total(prev)).toDouble();
            if (Math.abs(rt / tier.target - 1) > 0.05) {
                problems.add(String.format("%s total: x%.3f, want x%.2f +-5%%", tier.label, rt, tier.target));
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));

        var readme = Files.readString(Path.of(System.getProperty("tekkitcells.readme")));
        var begin = "<!-- cost-table:begin -->\n";
        var end = "<!-- cost-table:end -->";
        int b = readme.indexOf(begin);
        int e = readme.indexOf(end);
        assertTrue(b >= 0 && e > b, "README.md has no cost-table markers");
        assertEquals(table, readme.substring(b + begin.length(), e),
                "README.md cost table is out of date: copy build/cost-table.md between the markers");
    }

    // ------------------------------------------------------------------ bill of materials

    Map<String, Frac> bill(String item, Set<String> visiting) {
        if (RAW.contains(item)) {
            return Map.of(item, Frac.ONE);
        }
        var cached = memo.get(item);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(item)) {
            return null; // cycle
        }
        var candidates = new LinkedHashMap<String, Map<String, Frac>>();
        for (var r : byOutput.getOrDefault(item, List.of())) {
            var sum = new TreeMap<String, Frac>();
            boolean ok = true;
            for (var in : r.inputs.entrySet()) {
                var sub = bill(in.getKey(), visiting);
                if (sub == null) {
                    ok = false;
                    break;
                }
                for (var s : sub.entrySet()) {
                    sum.merge(s.getKey(), s.getValue().mul(Frac.of(in.getValue(), r.count)), Frac::add);
                }
            }
            if (ok) {
                candidates.put(r.id, sum);
            }
        }
        visiting.remove(item);
        if (candidates.isEmpty()) {
            fail("No usable recipe for " + item + " (not raw either)");
        }
        var distinct = new HashSet<>(candidates.values());
        Map<String, Frac> result;
        if (distinct.size() == 1) {
            result = candidates.values().iterator().next();
        } else if (PREFERRED.containsKey(item) && candidates.containsKey(PREFERRED.get(item))) {
            result = candidates.get(PREFERRED.get(item));
        } else {
            fail("Recipes for " + item + " disagree on cost; pick one in PREFERRED: " + candidates);
            return null;
        }
        memo.put(item, result);
        return result;
    }

    static Frac total(Map<String, Frac> bill) {
        return bill.values().stream().reduce(Frac.ZERO, Frac::add);
    }

    // ------------------------------------------------------------------ table

    static String table(List<Map<String, Frac>> bills) {
        var mats = RAW.stream().filter(m -> bills.stream().anyMatch(b -> b.containsKey(m))).toList();
        var sb = new StringBuilder("| Tier |");
        mats.forEach(m -> sb.append(' ').append(RAW_LABEL.get(m)).append(" |"));
        sb.append(" Total items | Ratio vs previous (each material / total) |\n|---|");
        mats.forEach(m -> sb.append("---:|"));
        sb.append("---:|---|\n");
        for (int i = 0; i < TIERS.size(); i++) {
            var b = bills.get(i);
            sb.append("| ").append(TIERS.get(i).label).append(" |");
            for (var m : mats) {
                sb.append(' ').append(fmt(b.getOrDefault(m, Frac.ZERO))).append(" |");
            }
            sb.append(' ').append(fmt(total(b))).append(" | ");
            if (i == 0) {
                sb.append("base");
            } else {
                var prev = bills.get(i - 1);
                double lo = Double.MAX_VALUE, hi = 0;
                for (var m : mats) {
                    double r = b.get(m).div(prev.get(m)).toDouble();
                    lo = Math.min(lo, r);
                    hi = Math.max(hi, r);
                }
                double rt = total(b).div(total(prev)).toDouble();
                sb.append(lo == hi ? String.format("x%.3f", lo) : String.format("x%.3f..x%.3f", lo, hi))
                        .append(String.format(" / x%.3f (rule x%.2f)", rt, TIERS.get(i).target));
            }
            sb.append(" |\n");
        }
        return sb.toString();
    }

    static String fmt(Frac f) {
        double v = f.toDouble();
        if (v == Math.rint(v)) {
            return String.format("%,d", (long) v);
        }
        return String.format("%,.2f", v);
    }

    // ------------------------------------------------------------------ recipe loading

    void loadAe2(Path jar) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                var n = e.getName();
                if (n.startsWith("data/ae2/recipe/") && n.endsWith(".json")) {
                    try (var r = new InputStreamReader(zip.getInputStream(e), StandardCharsets.UTF_8)) {
                        add("ae2:" + n.substring("data/ae2/recipe/".length(), n.length() - 5),
                                JsonParser.parseReader(r).getAsJsonObject());
                    }
                }
            }
        }
    }

    void loadDir(Path data) throws IOException {
        try (var files = Files.walk(data)) {
            for (var f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var rel = data.relativize(f).toString().replace('\\', '/');
                var ns = rel.substring(0, rel.indexOf('/'));
                var prefix = ns + "/recipe/";
                if (rel.startsWith(prefix)) {
                    add(ns + ":" + rel.substring(prefix.length(), rel.length() - 5),
                            JsonParser.parseString(Files.readString(f)).getAsJsonObject());
                }
            }
        }
    }

    void add(String id, JsonObject j) {
        var type = j.get("type").getAsString();
        var inputs = new TreeMap<String, Long>();
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                var key = j.getAsJsonObject("key");
                for (var row : j.getAsJsonArray("pattern")) {
                    for (char c : row.getAsString().toCharArray()) {
                        if (c != ' ' && !addIngredient(inputs, key.get(String.valueOf(c)))) {
                            return;
                        }
                    }
                }
            }
            case "minecraft:crafting_shapeless" -> {
                for (var in : j.getAsJsonArray("ingredients")) {
                    if (!addIngredient(inputs, in)) {
                        return;
                    }
                }
            }
            case "minecraft:smelting" -> {
                if (!addIngredient(inputs, j.get("ingredient"))) {
                    return;
                }
            }
            case "ae2:inscriber" -> {
                var ing = j.getAsJsonObject("ingredients");
                boolean press = "press".equals(j.get("mode").getAsString());
                for (var slot : press ? List.of("top", "middle", "bottom") : List.of("middle")) {
                    if (ing.has(slot) && !addIngredient(inputs, ing.get(slot))) {
                        return;
                    }
                }
            }
            default -> {
                return; // blasting duplicates smelting; upgrade/disassembly/charger/transform are not production paths
            }
        }
        var result = j.getAsJsonObject("result");
        long count = result.has("count") ? result.get("count").getAsLong() : 1;
        byOutput.computeIfAbsent(result.get("id").getAsString(), k -> new ArrayList<>())
                .add(new Recipe(id, result.get("id").getAsString(), count, inputs));
    }

    /** Only plain item ids and the tags in {@link #TAGS}; other ingredient forms make the recipe unusable here. */
    static boolean addIngredient(Map<String, Long> inputs, JsonElement in) {
        if (in == null || !in.isJsonPrimitive()) {
            return false;
        }
        var s = in.getAsString();
        String item;
        if (s.startsWith("#")) {
            item = TAGS.get(s.substring(1));
            if (item == null) {
                return false;
            }
        } else {
            item = s;
        }
        inputs.merge(item, 1L, Long::sum);
        return true;
    }

    record Frac(BigInteger n, BigInteger d) {
        static final Frac ZERO = of(0, 1);
        static final Frac ONE = of(1, 1);

        static Frac of(long n, long d) {
            return norm(BigInteger.valueOf(n), BigInteger.valueOf(d));
        }

        static Frac norm(BigInteger n, BigInteger d) {
            var g = n.gcd(d);
            if (g.signum() == 0) {
                return new Frac(BigInteger.ZERO, BigInteger.ONE);
            }
            return new Frac(n.divide(g), d.divide(g));
        }

        Frac add(Frac o) {
            return norm(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d));
        }

        Frac mul(Frac o) {
            return norm(n.multiply(o.n), d.multiply(o.d));
        }

        Frac div(Frac o) {
            return norm(n.multiply(o.d), d.multiply(o.n));
        }

        double toDouble() {
            return new java.math.BigDecimal(n).divide(new java.math.BigDecimal(d), java.math.MathContext.DECIMAL64)
                    .doubleValue();
        }
    }
}
