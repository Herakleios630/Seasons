package de.ajsch.seasons.visual.nms;

import io.netty.channel.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NmsAdapter implementation for Paper 1.21.x (26.1.x).
 * Uses a Netty ChannelOutboundHandler to intercept chunk packets and patch
 * BiomeSpecialEffects colors at the byte level before they reach the client.
 */
public class NmsAdapter_v1_21_5 extends NmsAdapter implements Listener {

    /** Name under which our handler is registered in each player's pipeline. */
    private static final String HANDLER_NAME = "seasons_biome_tint";

    private final Map<String, int[]> pendingTints = new ConcurrentHashMap<>();
    private final Set<String> registeredPlayers = ConcurrentHashMap.newKeySet();
    private boolean enabled = false;

    public NmsAdapter_v1_21_5(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        enabled = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[NmsAdapter] Netty diagnostic interceptor registered (PlayerJoinEvent).");
        plugin.getLogger().info("[NmsAdapter] Waiting for player join to inspect outgoing packet classes...");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        injectDiagnosticHandler(event.getPlayer());
    }

    /**
     * Injects a temporary ChannelOutboundHandlerAdapter into the player's Netty pipeline
     * to log the class names of ALL outgoing packets. Once a chunk-related packet is
     * identified, it will be printed in detail for further analysis.
     */
    private void injectDiagnosticHandler(Player player) {
        try {
            ChannelPipeline pipeline = getPlayerPipeline(player);
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }

            pipeline.addAfter("encoder", HANDLER_NAME, new ChannelOutboundHandlerAdapter() {
                private boolean chunkInspected = false;
                private int pktCount = 0;

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    pktCount++;

                    if (!chunkInspected && msg.getClass().getName()
                            .endsWith("ClientboundLevelChunkWithLightPacket")) {
                        chunkInspected = true;
                        plugin.getLogger().info("[NmsAdapter] [DIAG] === Chunk packet #" + pktCount + " ===");
                        inspectFullPacket(msg);
                    }

                    super.write(ctx, msg, promise);
                }

                private void inspectFullPacket(Object packet) {
                    try {
                        // 1. ALL fields of the packet itself
                        logAllFieldsRecursive(packet);

                        // 2. chunkData -> buffer hex, readBuffer index, writeBuffer index
                        Field fdChunk = findField(packet.getClass(), "chunkData");
                        if (fdChunk != null) {
                            fdChunk.setAccessible(true);
                            Object cd = fdChunk.get(packet);
                            if (cd != null) {
                                plugin.getLogger().info("[NmsAdapter] [DIAG] --- chunkData ("
                                    + cd.getClass().getSimpleName() + ") ---");
                                // hex dump first 300 bytes
                                Field fdBuf = findField(cd.getClass(), "buffer");
                                if (fdBuf != null) {
                                    fdBuf.setAccessible(true);
                                    byte[] buf = (byte[]) fdBuf.get(cd);
                                    if (buf != null) {
                                        int len = Math.min(300, buf.length);
                                        StringBuilder hex = new StringBuilder();
                                        for (int i = 0; i < len; i++) {
                                            hex.append(String.format("%02X", buf[i] & 0xFF));
                                            if ((i + 1) % 20 == 0) hex.append("|");
                                        }
                                        plugin.getLogger().info("[NmsAdapter] [DIAG]   buffer[" + buf.length
                                            + "], first " + len + "b: " + hex);
                                    }
                                }
                                // readBuffer / writeBuffer indices
                                try {
                                    Object readBuf = cd.getClass().getMethod("getReadBuffer").invoke(cd);
                                    if (readBuf != null) {
                                        int ri = (int) readBuf.getClass().getMethod("readerIndex").invoke(readBuf);
                                        int wi = (int) readBuf.getClass().getMethod("writerIndex").invoke(readBuf);
                                        plugin.getLogger().info("[NmsAdapter] [DIAG]   readBuffer idx r=" + ri + " w=" + wi);
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().info("[NmsAdapter] [DIAG]   readBuffer: " + e.getMessage());
                                }
                                // log all methods and fields of chunkData
                                logAllFieldsRecursive(cd);
                                logAllPublicMethods(cd, "chunkData");
                            }
                        }

                        // 3. lightData -> fields and methods
                        Field fdLight = findField(packet.getClass(), "lightData");
                        if (fdLight != null) {
                            fdLight.setAccessible(true);
                            Object ld = fdLight.get(packet);
                            if (ld != null) {
                                plugin.getLogger().info("[NmsAdapter] [DIAG] --- lightData ("
                                    + ld.getClass().getSimpleName() + ") ---");
                                logAllFieldsRecursive(ld);
                                logAllPublicMethods(ld, "lightData");
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[NmsAdapter] [DIAG] inspectFullPacket error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                private void logAllPublicMethods(Object obj, String label) {
                    for (java.lang.reflect.Method m : obj.getClass().getDeclaredMethods()) {
                        if (m.getParameterCount() == 0 && !m.getName().startsWith("lambda")
                            && !m.getName().equals("toString") && !m.getName().equals("hashCode")
                            && !m.getName().equals("clone")) {
                            m.setAccessible(true);
                            try {
                                Object result = m.invoke(obj);
                                String valStr = result == null ? "null"
                                    : result.getClass().getSimpleName() + " / " + result.getClass().getName();
                                plugin.getLogger().info("[NmsAdapter] [DIAG]   " + label + "." + m.getName()
                                    + "() -> " + valStr);
                            } catch (Exception ignored) {}
                        }
                    }
                }

                private Field findField(Class<?> clazz, String name) {
                    while (clazz != null && clazz != Object.class) {
                        try { return clazz.getDeclaredField(name); }
                        catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
                    }
                    return null;
                }
            });
            registeredPlayers.add(player.getName());
            plugin.getLogger().info("[NmsAdapter] Diagnostic handler injected for " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("[NmsAdapter] Failed to inject diagnostic handler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logAllFieldsRecursive(Object obj) {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object value = f.get(obj);
                    String valStr = value == null ? "null" : value.getClass().getSimpleName();
                    plugin.getLogger().info("[NmsAdapter] [DIAG]     "
                        + (clazz == obj.getClass() ? "" : "[super] ")
                        + f.getName() + " : " + f.getType().getSimpleName() + " = " + valStr);
                } catch (Exception ignored) {
                    plugin.getLogger().info("[NmsAdapter] [DIAG]     "
                        + (clazz == obj.getClass() ? "" : "[super] ")
                        + f.getName() + " : " + f.getType().getSimpleName() + " = <error>");
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    @Override
    public void onDisable() {
        if (!enabled) return;
        removeAllHandlers();
        enabled = false;
        pendingTints.clear();
        registeredPlayers.clear();
    }

    private void removeAllHandlers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                ChannelPipeline pipeline = getPlayerPipeline(player);
                if (pipeline.get(HANDLER_NAME) != null) {
                    pipeline.remove(HANDLER_NAME);
                }
            } catch (Exception ignored) {}
        }
    }

    private ChannelPipeline getPlayerPipeline(Player player) throws Exception {
        Object craftPlayer = player;
        Object entityPlayer = craftPlayer.getClass().getMethod("getHandle").invoke(craftPlayer);
        Object playerConnection = entityPlayer.getClass().getField("connection").get(entityPlayer);
        Object networkManager = playerConnection.getClass().getField("connection").get(playerConnection);
        Channel channel = (Channel) networkManager.getClass().getField("channel").get(networkManager);
        return channel.pipeline();
    }

    @Override
    public void sendBiomeTint(Player player, String biomeKey, int foliageColor, int grassColor) {
        if (!enabled) return;
        plugin.getLogger().info("[NmsAdapter] sendBiomeTint: " + biomeKey + " -> "
            + Integer.toHexString(foliageColor) + "/" + Integer.toHexString(grassColor));
        pendingTints.put(biomeKey, new int[]{foliageColor, grassColor});
    }

    @Override
    public void flushTints(Player player) {
        if (!enabled || pendingTints.isEmpty()) {
            plugin.getLogger().info("[NmsAdapter] flushTints skip: enabled=" + enabled
                + ", pending=" + pendingTints.size());
            return;
        }
        plugin.getLogger().info("[NmsAdapter] flushTints: flushing " + pendingTints.size()
            + " pending tints for player " + (player != null ? player.getName() : "all"));
        pendingTints.clear();
        refreshAllChunks();
    }

    private void refreshAllChunks() {
        int totalChunks = 0;
        for (World world : Bukkit.getWorlds()) {
            Chunk[] loaded = world.getLoadedChunks();
            plugin.getLogger().info("[NmsAdapter] refreshAllChunks: world=" + world.getName()
                + ", loadedChunks=" + loaded.length);
            totalChunks += loaded.length;
            for (Chunk chunk : loaded) {
                chunk.unload(false);
                chunk.load(true);
            }
        }
        plugin.getLogger().info("[NmsAdapter] refreshAllChunks: reloaded " + totalChunks
            + " chunks across all worlds");
    }
}