package net.moderntekkit.cells;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.ids.AECreativeTabIds;
import appeng.api.stacks.AEKeyType;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.items.materials.StorageComponentItem;
import appeng.items.storage.BasicStorageCell;

/**
 * Bigger AE2 item and fluid storage cells (1M, 4M, 16M, 32M) and their storage components. The cells are AE2's own
 * {@link BasicStorageCell}, so AE2's cell handler, drives, ME chests, tooltips, cell workbench and upgrade cards treat
 * them exactly like its 1k..256k cells.
 */
@Mod(TekkitCells.MOD_ID)
public final class TekkitCells {
    public static final String MOD_ID = "tekkitcells";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final Map<CellTier, DeferredItem<StorageComponentItem>> COMPONENTS = new EnumMap<>(CellTier.class);
    public static final Map<CellTier, DeferredItem<BasicStorageCell>> CELLS = new EnumMap<>(CellTier.class);
    public static final Map<CellTier, DeferredItem<BasicStorageCell>> FLUID_CELLS = new EnumMap<>(CellTier.class);

    static {
        for (var tier : CellTier.values()) {
            COMPONENTS.put(tier, ITEMS.registerItem(tier.componentId(),
                    p -> new StorageComponentItem(p, tier.kilobytes)));
            CELLS.put(tier, ITEMS.registerItem(tier.cellId(),
                    p -> new BasicStorageCell(p.stacksTo(1).rarity(tier.ordinal() >= 2 ? Rarity.RARE : Rarity.UNCOMMON),
                            tier.idleDrain(), tier.kilobytes, tier.bytesPerType(), CellTier.TYPES,
                            AEKeyType.items())));
            FLUID_CELLS.put(tier, ITEMS.registerItem(tier.fluidCellId(),
                    p -> new BasicStorageCell(p.stacksTo(1).rarity(tier.ordinal() >= 2 ? Rarity.RARE : Rarity.UNCOMMON),
                            tier.idleDrain(), tier.kilobytes, tier.bytesPerType(), CellTier.FLUID_TYPES,
                            AEKeyType.fluids())));
        }
    }

    public TekkitCells(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addToCreativeTab);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Same upgrade cards as AE2's item and fluid cells (appeng.init.internal.InitUpgrades).
        event.enqueueWork(() -> {
            var group = GuiText.StorageCells.getTranslationKey();
            for (var cell : CELLS.values()) {
                for (var card : List.of(AEItems.FUZZY_CARD, AEItems.INVERTER_CARD, AEItems.EQUAL_DISTRIBUTION_CARD,
                        AEItems.VOID_CARD)) {
                    Upgrades.add(card, cell.get(), 1, group);
                }
            }
            // Fluid cells: AE2's fluid cell cards (no fuzzy card).
            for (var cell : FLUID_CELLS.values()) {
                for (var card : List.of(AEItems.INVERTER_CARD, AEItems.EQUAL_DISTRIBUTION_CARD, AEItems.VOID_CARD)) {
                    Upgrades.add(card, cell.get(), 1, group);
                }
            }
        });
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == AECreativeTabIds.MAIN) {
            for (var tier : CellTier.values()) {
                event.accept(COMPONENTS.get(tier).get());
            }
            for (var tier : CellTier.values()) {
                event.accept(CELLS.get(tier).get());
            }
            for (var tier : CellTier.values()) {
                event.accept(FLUID_CELLS.get(tier).get());
            }
        }
    }
}
