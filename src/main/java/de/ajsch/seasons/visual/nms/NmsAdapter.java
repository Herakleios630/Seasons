package de.ajsch.seasons.visual.nms;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Abstract base class for version-specific NMS packet manipulation.
 * Encapsulates all direct Minecraft server internals access behind a clean interface.
 * <p>
 * Each supported Minecraft server version gets its own concrete implementation.
 * If no matching implementation is found, visual features are gracefully disabled.
 */
public abstract class NmsAdapter {

    protected final JavaPlugin plugin;

    protected NmsAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called during plugin enable. Register packet interceptors and channel handlers.
     */
    public abstract void onEnable();

    /**
     * Called during plugin disable. Unregister all handlers and clean up resources.
     */
    public abstract void onDisable();

    /**
     * Update the biome tint colors that will be applied to outgoing chunk packets.
     * <p>
     * After calling this method, any subsequent chunk packets sent to the player
     * will have the specified biome's foliage and grass colors overridden.
     *
     * @param player       the target player (use {@code null} for all players)
     * @param biomeKey     the Minecraft biome key string (e.g. "minecraft:plains")
     * @param foliageColor RGB color as 0xRRGGBB integer
     * @param grassColor   RGB color as 0xRRGGBB integer (may equal foliageColor)
     */
    public abstract void sendBiomeTint(Player player, String biomeKey, int foliageColor, int grassColor);

    /**
     * Flush all pending biome tints for a player. This should be called ONCE after
     * all {@link #sendBiomeTint} calls for a given update cycle, not per-biome.
     * <p>
     * Implementations must apply all accumulated overrides in a single registry-patch
     * operation and trigger exactly one chunk refresh.
     *
     * @param player the target player (use {@code null} for all players)
     */
    public abstract void flushTints(Player player);

    /**
     * Factory method: detects the running server version and returns the appropriate
     * {@code NmsAdapter} implementation. Throws {@link UnsupportedOperationException}
     * if no adapter is available for this version.
     *
     * @param plugin the plugin instance
     * @return a matching NmsAdapter
     */
    public static NmsAdapter create(JavaPlugin plugin) {
        String rawVersion = plugin.getServer().getMinecraftVersion();
        String bukkitVersion = plugin.getServer().getBukkitVersion();
        plugin.getLogger().info("[NmsAdapter] Server version: " + rawVersion + " (bukkit: " + bukkitVersion + ")");

        // Version-independent detection: check if the expected NMS class exists.
        // Paper 1.21.x (including 1.21.4/1.21.5) ships BiomeSpecialEffects.
        try {
            Class.forName("net.minecraft.world.level.biome.BiomeSpecialEffects");
            plugin.getLogger().info("[NmsAdapter] Detected 1.21.x NMS structure (BiomeSpecialEffects found).");
            return new NmsAdapter_v1_21_5(plugin);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning(
                "[NmsAdapter] Class not found: " + e.getMessage()
                + " – NMS adapter not available for this server version. Visual features disabled.");
            throw new UnsupportedOperationException("Unsupported Minecraft version: " + rawVersion, e);
        }
    }
}