package net.moderntekkit.cells.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InitializeClientRegistriesEvent;

import appeng.api.client.StorageCellModels;

import net.moderntekkit.cells.CellTier;
import net.moderntekkit.cells.TekkitCells;

/**
 * Registers the model each cell shows inside an ME drive / ME chest (the drive draws the status LED on top), the same
 * way AE2 registers its own cells in AppEngClient#initCustomClientRegistries.
 */
@Mod(value = TekkitCells.MOD_ID, dist = Dist.CLIENT)
public final class TekkitCellsClient {
    public TekkitCellsClient(IEventBus modBus) {
        modBus.addListener(this::initClientRegistries);
    }

    private void initClientRegistries(InitializeClientRegistriesEvent event) {
        for (var tier : CellTier.values()) {
            StorageCellModels.registerModel(TekkitCells.CELLS.get(tier).get(),
                    TekkitCells.id("block/drive_" + tier.id + "_item_cell"));
        }
    }
}
