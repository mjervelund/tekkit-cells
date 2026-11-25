package net.moderntekkit.cells;

/**
 * The cell tiers this mod adds on top of AE2's 1k..256k, with AE2's own scaling rules:
 * <ul>
 * <li>bytes per type = total bytes / 128 (AE2: 1k -> 8, 256k -> 2048);</li>
 * <li>idle drain = 0.5 + 0.25 AE/t per doubling of capacity (AE2: 1k 0.5 ... 256k 2.5, +0.5 per 4x);</li>
 * <li>63 types, like every AE2 item cell; 18 for the fluid cells, like every AE2 fluid cell.</li>
 * </ul>
 * Item and fluid cells of a tier share its storage component, as AE2's do.
 */
public enum CellTier {
    SIZE_1M("1m", "1M", 1024),
    SIZE_4M("4m", "4M", 4 * 1024),
    SIZE_16M("16m", "16M", 16 * 1024),
    SIZE_32M("32m", "32M", 32 * 1024);

    public static final int TYPES = 63;
    public static final int FLUID_TYPES = 18;

    /** Lower-case id suffix: item_storage_cell_1m, cell_component_1m. */
    public final String id;
    public final String label;
    /** Capacity in kilobytes, as AE2's BasicStorageCell and StorageComponentItem take it. */
    public final int kilobytes;

    CellTier(String id, String label, int kilobytes) {
        this.id = id;
        this.label = label;
        this.kilobytes = kilobytes;
    }

    public int totalBytes() {
        return kilobytes * 1024;
    }

    public int bytesPerType() {
        return totalBytes() / 128;
    }

    public double idleDrain() {
        return 0.5 + 0.25 * (Integer.numberOfTrailingZeros(kilobytes));
    }

    public String cellId() {
        return "item_storage_cell_" + id;
    }

    public String fluidCellId() {
        return "fluid_storage_cell_" + id;
    }

    public String componentId() {
        return "cell_component_" + id;
    }
}
