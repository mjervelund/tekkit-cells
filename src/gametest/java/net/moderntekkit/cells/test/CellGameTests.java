package net.moderntekkit.cells.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cells.BasicCellInventory;

import net.moderntekkit.cells.CellTier;
import net.moderntekkit.cells.TekkitCells;

final class CellGameTests {
    private CellGameTests() {
    }

    static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) {
            throw helper.assertionException(message);
        }
    }

    static void checkEq(GameTestHelper helper, long want, long got, String what) {
        check(helper, want == got, what + ": want " + want + ", got " + got);
    }

    /** Items capacity of an empty cell holding `types` types: AE2 stores 8 items per byte. */
    static long capacity(CellTier tier, int types) {
        return ((long) tier.totalBytes() - (long) types * tier.bytesPerType()) * 8;
    }

    /** `n` distinct plain items that are not storage cells (any cell may not go into a cell). */
    static List<AEItemKey> distinctItems(int n) {
        var keys = new ArrayList<AEItemKey>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            var stack = new ItemStack(item);
            if (StorageCells.isCellHandled(stack)) {
                continue;
            }
            var key = AEItemKey.of(stack);
            if (key != null) {
                keys.add(key);
            }
            if (keys.size() == n) {
                break;
            }
        }
        return keys;
    }

    /** Fluid capacity (in AE2 fluid units, mB) of an empty fluid cell holding `types` types: 8 buckets per byte. */
    static long fluidCapacity(CellTier tier, int types) {
        return ((long) tier.totalBytes() - (long) types * tier.bytesPerType()) * AEKeyType.fluids().getAmountPerByte();
    }

    /** `n` distinct source fluids (the empty fluid excluded). */
    static List<AEFluidKey> distinctFluids(int n) {
        var keys = new ArrayList<AEFluidKey>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) {
                continue;
            }
            var key = AEFluidKey.of(fluid);
            if (key != null && !keys.contains(key)) {
                keys.add(key);
            }
            if (keys.size() == n) {
                break;
            }
        }
        return keys;
    }

    /**
     * A 32M fluid cell in an ME drive: AE2 sees bytes/types (18), accepts fluids but not items, fills it to the exact
     * fluid capacity and drains it again. With only a few fluids registered in a dev run, the type limit is checked with
     * as many fluids as exist (max 18), and the 19th-type refusal only when 19 are registered.
     */
    static void fluidCell32mInDrive(GameTestHelper helper) {
        var tier = CellTier.SIZE_32M;
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, AEBlocks.DRIVE.block().defaultBlockState());
        var drive = helper.getBlockEntity(pos, DriveBlockEntity.class);
        drive.getInternalInventory().setItemDirect(0, new ItemStack(TekkitCells.FLUID_CELLS.get(tier).get()));

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            check(helper, drive.getCellItem(0) == TekkitCells.FLUID_CELLS.get(tier).get(), "drive does not hold the cell");
            check(helper, drive.getCellStatus(0) == CellState.EMPTY, "status " + drive.getCellStatus(0) + ", want EMPTY");
            var cell = drive.getOriginalCellInventory(0);
            check(helper, cell instanceof BasicCellInventory, "AE2 does not handle the cell as a basic cell: " + cell);
            var inv = (BasicCellInventory) cell;
            checkEq(helper, 33_554_432L, inv.getTotalBytes(), "total bytes");
            checkEq(helper, 262_144L, inv.getBytesPerType(), "bytes per type");
            checkEq(helper, 18L, inv.getTotalItemTypes(), "types");
            check(helper, inv.getIdleDrain() == 4.25, "idle drain " + inv.getIdleDrain() + ", want 4.25");

            var storage = drive.getCellInventory(0);
            var src = IActionSource.empty();
            checkEq(helper, 0, storage.insert(AEItemKey.of(Items.COBBLESTONE), 1, Actionable.MODULATE, src),
                    "item accepted by a fluid cell");

            var fluids = distinctFluids(19);
            check(helper, fluids.size() >= 2, "need at least 2 fluids, found " + fluids);
            int types = Math.min(18, fluids.size());
            for (int i = 0; i < types; i++) {
                checkEq(helper, 1, storage.insert(fluids.get(i), 1, Actionable.MODULATE, src), "insert fluid " + i);
            }
            if (fluids.size() == 19) {
                checkEq(helper, 0, storage.insert(fluids.get(18), 1, Actionable.MODULATE, src), "19th type accepted");
                check(helper, drive.getCellStatus(0) == CellState.TYPES_FULL,
                        "status " + drive.getCellStatus(0) + ", want TYPES_FULL");
            }

            var water = AEFluidKey.of(Fluids.WATER);
            long want = fluidCapacity(tier, types);
            long inserted = storage.insert(water, Long.MAX_VALUE / 4, Actionable.MODULATE, src);
            long others = fluids.contains(water) ? types - 1 : types;
            checkEq(helper, want - types, inserted, "mB inserted until full (" + others + " other types)");
            checkEq(helper, want, inv.getStoredItemCount(), "stored mB");
            if (types == 18) {
                checkEq(helper, inv.getTotalBytes(), inv.getUsedBytes(), "used bytes");
                check(helper, drive.getCellStatus(0) == CellState.FULL,
                        "status " + drive.getCellStatus(0) + ", want FULL");
            }
            long hadWater = fluids.contains(water) ? 1 : 0;
            long out = storage.extract(water, Long.MAX_VALUE, Actionable.MODULATE, src);
            checkEq(helper, inserted + hadWater, out, "mB of water extracted");
        }).thenSucceed();
    }

    /** A 32M cell in an ME drive: AE2 sees its bytes and types, fills it to the exact capacity, LED states follow. */
    static void cell32mInDrive(GameTestHelper helper) {
        var tier = CellTier.SIZE_32M;
        var pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, AEBlocks.DRIVE.block().defaultBlockState());
        var drive = helper.getBlockEntity(pos, DriveBlockEntity.class);
        drive.getInternalInventory().setItemDirect(0, new ItemStack(TekkitCells.CELLS.get(tier).get()));

        helper.startSequence().thenIdle(5).thenExecute(() -> {
            check(helper, drive.getCellItem(0) == TekkitCells.CELLS.get(tier).get(), "drive does not hold the cell");
            check(helper, drive.getCellStatus(0) == CellState.EMPTY, "status " + drive.getCellStatus(0) + ", want EMPTY");
            var cell = drive.getOriginalCellInventory(0);
            check(helper, cell instanceof BasicCellInventory, "AE2 does not handle the cell as a basic cell: " + cell);
            var inv = (BasicCellInventory) cell;
            checkEq(helper, 33_554_432L, inv.getTotalBytes(), "total bytes");
            checkEq(helper, 262_144L, inv.getBytesPerType(), "bytes per type");
            checkEq(helper, 63L, inv.getTotalItemTypes(), "types");
            check(helper, inv.getIdleDrain() == 4.25, "idle drain " + inv.getIdleDrain() + ", want 4.25");

            var storage = drive.getCellInventory(0);
            var src = IActionSource.empty();
            var keys = distinctItems(64);
            checkEq(helper, 64, keys.size(), "test items found");
            for (int i = 0; i < 63; i++) {
                checkEq(helper, 1, storage.insert(keys.get(i), 1, Actionable.MODULATE, src), "insert type " + i);
            }
            checkEq(helper, 0, storage.insert(keys.get(63), 1, Actionable.MODULATE, src), "64th type accepted");
            check(helper, drive.getCellStatus(0) == CellState.TYPES_FULL,
                    "status " + drive.getCellStatus(0) + ", want TYPES_FULL");

            long want = capacity(tier, 63); // 136,314,880 items
            long inserted = storage.insert(keys.get(0), Long.MAX_VALUE / 4, Actionable.MODULATE, src);
            checkEq(helper, want - 63, inserted, "items inserted until full");
            checkEq(helper, want, inv.getStoredItemCount(), "stored items");
            checkEq(helper, inv.getTotalBytes(), inv.getUsedBytes(), "used bytes");
            check(helper, drive.getCellStatus(0) == CellState.FULL, "status " + drive.getCellStatus(0) + ", want FULL");

            long out = storage.extract(keys.get(0), Long.MAX_VALUE, Actionable.MODULATE, src);
            checkEq(helper, want - 62, out, "extracted");
        }).thenSucceed();
    }

    /** Every tier: bytes, bytes per type, types and the single-type capacity, as AE2's cell handler sees them. */
    static void allTiersCapacity(GameTestHelper helper) {
        var src = IActionSource.empty();
        var key = AEItemKey.of(Items.COBBLESTONE);
        for (var tier : CellTier.values()) {
            var inv = (BasicCellInventory) StorageCells
                    .getCellInventory(new ItemStack(TekkitCells.CELLS.get(tier).get()), null);
            check(helper, inv != null, tier + ": not a cell");
            checkEq(helper, tier.totalBytes(), inv.getTotalBytes(), tier + " bytes");
            checkEq(helper, tier.totalBytes() / 128, inv.getBytesPerType(), tier + " bytes per type");
            checkEq(helper, 63, inv.getTotalItemTypes(), tier + " types");
            checkEq(helper, capacity(tier, 1), inv.insert(key, Long.MAX_VALUE / 4, Actionable.MODULATE, src),
                    tier + " single-type capacity");
            // The component reports the same size (AE2's IStorageComponent, used e.g. in tooltips/JEI).
            var comp = TekkitCells.COMPONENTS.get(tier).get();
            checkEq(helper, tier.totalBytes(), comp.getBytes(new ItemStack(comp)), tier + " component bytes");

            var fluidInv = (BasicCellInventory) StorageCells
                    .getCellInventory(new ItemStack(TekkitCells.FLUID_CELLS.get(tier).get()), null);
            check(helper, fluidInv != null, tier + ": fluid cell is not a cell");
            var fluidCell = TekkitCells.FLUID_CELLS.get(tier).get();
            check(helper, fluidCell.getKeyType() == AEKeyType.fluids(), tier + ": fluid cell key type " + fluidCell.getKeyType());
            checkEq(helper, tier.totalBytes(), fluidInv.getTotalBytes(), tier + " fluid bytes");
            checkEq(helper, tier.totalBytes() / 128, fluidInv.getBytesPerType(), tier + " fluid bytes per type");
            checkEq(helper, 18, fluidInv.getTotalItemTypes(), tier + " fluid types");
            check(helper, fluidInv.getIdleDrain() == tier.idleDrain(), tier + " fluid idle drain " + fluidInv.getIdleDrain());
            checkEq(helper, fluidCapacity(tier, 1),
                    fluidInv.insert(AEFluidKey.of(Fluids.LAVA), Long.MAX_VALUE / 4, Actionable.MODULATE, src),
                    tier + " fluid single-type capacity (mB)");
        }
        helper.succeed();
    }

    /**
     * All 36 recipes load; the 1M component crafts from 5 x 256k components + 4 housings, the 32M one makes 2; a fluid
     * cell crafts from an ME Fluid Cell Housing + component.
     */
    static void recipesLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getServer().getRecipeManager();
        var ids = new ArrayList<String>();
        for (var tier : CellTier.values()) {
            ids.add(tier.componentId());
            ids.add(tier.cellId());
            ids.add(tier.cellId() + "_storage");
            ids.add("cell_upgrade/" + tier.cellId());
            ids.add(tier.fluidCellId());
            ids.add(tier.fluidCellId() + "_storage");
            ids.add("cell_upgrade/" + tier.fluidCellId());
        }
        ids.add("upgrade/item_storage_cell_256k_to_1m");
        ids.add("upgrade/item_storage_cell_1m_to_4m");
        ids.add("upgrade/item_storage_cell_4m_to_16m");
        ids.add("upgrade/item_storage_cell_16m_to_32m");
        ids.add("upgrade/fluid_storage_cell_256k_to_1m");
        ids.add("upgrade/fluid_storage_cell_1m_to_4m");
        ids.add("upgrade/fluid_storage_cell_4m_to_16m");
        ids.add("upgrade/fluid_storage_cell_16m_to_32m");
        for (var id : ids) {
            var key = ResourceKey.create(Registries.RECIPE, TekkitCells.id(id));
            check(helper, recipes.byKey(key).isPresent(), "recipe " + id + " did not load");
        }

        var c = new ItemStack(AEItems.CELL_COMPONENT_256K.asItem());
        var h = new ItemStack(AEItems.ITEM_CELL_HOUSING.asItem());
        var input = CraftingInput.of(3, 3, List.of(c, h, c, h, c, h, c, h, c));
        var match = recipes.getRecipeFor(RecipeType.CRAFTING, input, level);
        check(helper, match.isPresent(), "no crafting recipe for 5 x 256k component + 4 housings");
        var out = match.get().value().assemble(input);
        check(helper, out.getItem() == TekkitCells.COMPONENTS.get(CellTier.SIZE_1M).get() && out.getCount() == 1,
                "5 x 256k + 4 housings made " + out);

        var c16 = new ItemStack(TekkitCells.COMPONENTS.get(CellTier.SIZE_16M).get());
        var e = ItemStack.EMPTY;
        var input32 = CraftingInput.of(3, 3, List.of(c16, h, c16, h, c16, h, c16, e, c16));
        var match32 = recipes.getRecipeFor(RecipeType.CRAFTING, input32, level);
        check(helper, match32.isPresent(), "no crafting recipe for 5 x 16M component + 3 housings");
        var out32 = match32.get().value().assemble(input32);
        check(helper, out32.getItem() == TekkitCells.COMPONENTS.get(CellTier.SIZE_32M).get() && out32.getCount() == 2,
                "5 x 16M + 3 housings made " + out32);

        var fh = new ItemStack(AEItems.FLUID_CELL_HOUSING.asItem());
        var c1 = new ItemStack(TekkitCells.COMPONENTS.get(CellTier.SIZE_1M).get());
        var fin = CraftingInput.of(2, 1, List.of(fh, c1));
        var fmatch = recipes.getRecipeFor(RecipeType.CRAFTING, fin, level);
        check(helper, fmatch.isPresent(), "no crafting recipe for fluid housing + 1M component");
        var fout = fmatch.get().value().assemble(fin);
        check(helper, fout.getItem() == TekkitCells.FLUID_CELLS.get(CellTier.SIZE_1M).get(),
                "fluid housing + 1M component made " + fout);
        helper.succeed();
    }
}
