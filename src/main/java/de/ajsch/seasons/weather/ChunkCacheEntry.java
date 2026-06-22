package de.ajsch.seasons.weather;

/**
 * Per-chunk cache entry holding snow heights for both plugin-managed and
 * natural snow, plus metadata collected during the last column scan.
 *
 * Array layout: idx = x * 16 + z  (0 .. 255)
 */
public class ChunkCacheEntry {

    /** Side length of a chunk (columns per axis). */
    public static final int CHUNK_SIZE = 16;

    /** Total columns in a chunk: 16 × 16 = 256. */
    public static final int TOTAL_COLUMNS = CHUNK_SIZE * CHUNK_SIZE;

    /* ---------- snow-height arrays ---------- */
    /** Plugin-placed snow layers per column (0–8). */
    public final byte[] pluginSnowHeight;

    /** Naturally generated snow layers per column (0–8). */
    public final byte[] naturalSnowHeight;

    /* ---------- metadata ---------- */
    /** Columns whose ground is solid and can carry snow. */
    public int snowCapable;

    /** Bitmask of columns that are permanently blocked (e.g. torch, non-replaceable block above ground).
     *  These are excluded from eligibility in processChunk() and are NOT counted in snowCapable. */
    public final java.util.BitSet blockedColumns;

    /** Material ordinal for each column where a plant was displaced by snow placement.
     *  0 means no plant displaced. Non-zero is the Material.ordinal() of the removed plant. */
    public final short[] displacedPlantTypes;

    /** Columns that currently have at least 1 layer of snow. */
    public int snowCovered;

    /** Columns where snow height is below the temperature-limited max. */
    public int snowBelowMax;

    /** Total number of columns where plugin has placed snow. */
    public int totalPluginSnowColumns;

    /** Lowest temperature level observed in this chunk (0..N). */
    public int tempLevelMin;

    /** Highest temperature level observed in this chunk (0..N). */
    public int tempLevelMax;

    /** System-nanos timestamp of the last full column scan. */
    public long lastUpdated;

    public ChunkCacheEntry() {
        this.pluginSnowHeight = new byte[TOTAL_COLUMNS];
        this.naturalSnowHeight = new byte[TOTAL_COLUMNS];
        this.blockedColumns = new java.util.BitSet(TOTAL_COLUMNS);
        this.displacedPlantTypes = new short[TOTAL_COLUMNS];
    }

    /* ---------- helper methods ---------- */

    /** Total plugin snow layers across all columns. */
    public int totalPluginSnow() {
        int sum = 0;
        for (byte h : pluginSnowHeight) sum += h;
        return sum;
    }

    /** Whether this chunk has any plugin-placed snow. */
    public boolean hasPluginSnow() {
        return totalPluginSnow() > 0;
    }

    /** Whether every snow-capable column has at least one snow layer (natural or plugin) – 100% threshold. */
    public boolean isSaturated() {
        return isSaturated(1.0);
    }

    /** Whether snowCovered meets the given threshold percentage of snowCapable. */
    public boolean isSaturated(double threshold) {
        return snowCapable > 0 && snowCovered >= (int)(snowCapable * threshold);
    }

    /** Whether every snow-capable column has at least 1 snow layer AND no column can grow further. */
    public boolean isFullyGrown() {
        return snowCapable > 0 && snowCovered >= snowCapable && snowBelowMax == 0;
    }

    /** Linear index for column (x, z), both 0–15. */
    public static int columnIndex(int x, int z) {
        return x * CHUNK_SIZE + z;
    }

    /* ---------- displaced plant helpers ---------- */

    public void markDisplacedPlant(int idx, org.bukkit.Material type) {
        this.displacedPlantTypes[idx] = (short) type.ordinal();
    }

    public org.bukkit.Material getDisplacedPlantType(int idx) {
        short ord = this.displacedPlantTypes[idx];
        if (ord == 0) return null;
        org.bukkit.Material[] values = org.bukkit.Material.values();
        if (ord > 0 && ord < (short) values.length) {
            return values[ord];
        }
        return null;
    }

    public void clearDisplacedPlant(int idx) {
        this.displacedPlantTypes[idx] = 0;
    }

    /* ---------- metadata reset ---------- */

    /** Reset metadata counters (does NOT clear the height arrays). */
    public void resetMetadata() {
        snowCapable = 0;
        snowCovered = 0;
        snowBelowMax = 0;
        totalPluginSnowColumns = 0;
        tempLevelMin = Integer.MAX_VALUE;
        tempLevelMax = Integer.MIN_VALUE;
    }
}