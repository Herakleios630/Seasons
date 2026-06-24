package de.ajsch.seasons.visual;

import de.ajsch.seasons.config.ConfigManager;
import de.ajsch.seasons.config.FrostConfig;
import de.ajsch.seasons.season.Season;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Klassifiziert Chunks nach Biome-Typ (LAND/OCEAN) und wählt das
 * passende saisonale Ziel-Biome aus – jetzt dynamisch per Original-Biom
 * statt per starrem Per-Family-Mapping.
 *
 * <p>Namenskonvention: {@code seasons:<variant>_<biomeKey>}
 * (z.B. {@code seasons:fall_forest}, {@code seasons:winter_taiga}).
 * Dadurch bleiben alle Original-Eigenschaften (Mob-Spawns, Temperatur,
 * Downfall) des Bioms erhalten – nur Gras-/Laubfarben ändern sich.</p>
 *
 * <p>Bei Frost-Bedingungen (temp &lt; freezeThreshold) wird ein
 * Frost-Biom {@code seasons:frost_<biomeKey>} statt des normalen
 * Saison-Bioms gewählt.</p>
 */
public class SeasonBiomeResolver {

    private final BiomeBackupStore backupStore;
    private final SeasonColorConfig seasonColorConfig;
    private final FrostConfig frostConfig;
    private final Logger logger;

    // --- Config-gesteuerte Felder ---
    private boolean oceanSpoofEnabled;
    private boolean keepDeepVariants;
    private final Set<Biome> excludedBiomes = new HashSet<>();
    private final Set<String> disabledWorlds = new HashSet<>();

    // Alte Per-Family-Maps (werden während der Übergangsphase noch aus
    // biome_spoof.yml geladen, aber chooseTargetBiomeForChunk() verwendet
    // bevorzugt resolveTargetBiome() für dynamisches Per-Biome-Mapping)
    private final Map<Season, Biome> seasonTarget = new EnumMap<>(Season.class);
    private final Map<Season, Biome> oceanTarget = new EnumMap<>(Season.class);

    // --- Caches ---
    private final Map<String, BiomeFamily> familyCache = new HashMap<>();
    private final Set<String> coldChunks = new HashSet<>();

    // --- Per-Biome-Key-Cache (Biome enum → "forest", "birch_forest") ---
    private final Map<Biome, String> biomeKeyCache = new HashMap<>();

    public SeasonBiomeResolver(BiomeBackupStore backupStore,
                               ConfigManager configManager,
                               SeasonColorConfig seasonColorConfig,
                               FrostConfig frostConfig,
                               Logger logger) {
        this.backupStore = backupStore;
        this.seasonColorConfig = seasonColorConfig;
        this.frostConfig = frostConfig;
        this.logger = logger;
        reloadFromConfig(configManager);
    }

    // ---------------------------------------------------------------
    //  Config-Reload
    // ---------------------------------------------------------------

    /**
     * Liest alle Klassifizierungs-relevanten Werte aus den Configs
     * und leert die Caches.
     */
    public void reloadFromConfig(ConfigManager config) {
        oceanSpoofEnabled = config.isOceanSpoofEnabled();
        keepDeepVariants = config.isKeepDeepOceanVariants();

        excludedBiomes.clear();
        excludedBiomes.addAll(config.getExcludedBiomes());

        disabledWorlds.clear();
        disabledWorlds.addAll(config.getDisabledFxWorlds());

        // Alte Per-Family-Maps aus biome_spoof.yml laden
        // (Übergangsphase – wird später durch TransitionManager ersetzt)
        seasonTarget.clear();
        for (Season season : Season.values()) {
            Biome biome = config.getSeasonTargetBiome(season);
            if (biome != null) {
                seasonTarget.put(season, biome);
            }
        }
        oceanTarget.clear();
        for (Season season : Season.values()) {
            Biome biome = config.getOceanTargetBiome(season);
            if (biome != null) {
                oceanTarget.put(season, biome);
            }
        }

        familyCache.clear();
        coldChunks.clear();
        biomeKeyCache.clear();

        logger.info(String.format(
            "[SeasonBiomeResolver] Config geladen: oceanSpoof=%s deepVariants=%s excluded=%d seasonTargets=%d oceanTargets=%d enabledBiomes=%d",
            oceanSpoofEnabled, keepDeepVariants, excludedBiomes.size(),
            seasonTarget.size(), oceanTarget.size(),
            seasonColorConfig.getEnabledBiomes().size()));
    }

    // ---------------------------------------------------------------
    //  Klassifizierung
    // ---------------------------------------------------------------

    /**
     * Bestimmt die {@link BiomeFamily} (LAND / OCEAN) für einen Chunk.
     */
    public BiomeFamily classifyOriginalFamily(Chunk chunk, String chunkKey) {
        BiomeFamily cached = familyCache.get(chunkKey);
        if (cached != null) return cached;

        Biome sample;
        if (backupStore.hasBackup(chunkKey)) {
            Biome[] backup = backupStore.getBackup(chunkKey);
            sample = (backup != null && backup.length > 0) ? backup[0] : null;
        } else {
            sample = getSampleBiome(chunk);
        }

        BiomeFamily family = isOceanBiome(sample) ? BiomeFamily.OCEAN : BiomeFamily.LAND;
        familyCache.put(chunkKey, family);
        return family;
    }

    /**
     * Prüft, ob ein Chunk durch die Config vom Spoofing ausgeschlossen ist.
     */
    public boolean isChunkExcludedByConfig(Chunk chunk) {
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return true;
        if (disabledWorlds.contains(world.getName())) return true;

        Biome sample = getSampleBiome(chunk);
        if (sample != null && excludedBiomes.contains(sample)) return true;

        // Prüfe, ob das Biom in der enabled_biomes-Liste der season_colors.yml steht
        if (sample != null && !seasonColorConfig.isBiomeEnabled(sample)) return true;

        return false;
    }

    /**
     * Entscheidet, ob ein Chunk vom Spoofing übersprungen werden soll.
     * Außerhalb des Winters werden kalte Biome nicht überschrieben.
     */
    public boolean shouldSkipSpoofForChunk(Chunk chunk, Season season, String chunkKey) {
        if (season == Season.WINTER) return false;

        if (coldChunks.contains(chunkKey)) return true;

        Biome sample = getSampleBiome(chunk);
        if (sample != null && isColdBiome(sample)) {
            coldChunks.add(chunkKey);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------
    //  Target-Auswahl (NEU: dynamisches Per-Biome-Mapping)
    // ---------------------------------------------------------------

    /**
     * Wählt das Ziel-Biom für einen Chunk. Verwendet bevorzugt
     * {@link #resolveTargetBiome(Chunk, Season, String)} für
     * dynamisches Per-Biome-Mapping, mit Fallback auf die alten
     * Per-Family-Maps aus biome_spoof.yml.
     *
     * @return das Ziel-Biom oder {@code null} falls kein Spoof gewünscht
     */
    public Biome chooseTargetBiomeForChunk(String chunkKey, BiomeFamily family,
                                            Season season, Chunk chunk) {
        String variant = season.name().toLowerCase(); // "spring", "summer", "fall", "winter"

        if (family == BiomeFamily.OCEAN) {
            if (!oceanSpoofEnabled) return null;

            // Dynamisches Ozean-Mapping: seasons:<variant>_<biomeKey>
            Biome resolved = resolveTargetBiome(chunk, season, variant);
            if (resolved != null) {
                // Deep-Ocean-Handling
                if (keepDeepVariants) {
                    Biome originalSample = getSampleBiome(chunk);
                    if (originalSample != null && isDeepOcean(originalSample)) {
                        Biome deepResolved = resolveTargetBiome(chunk, season, "deep_" + variant);
                        if (deepResolved != null) return deepResolved;
                        // Fallback: deep-Variante des aufgelösten Biomes
                        Biome deepVariant = getDeepVariant(resolved);
                        if (deepVariant != null) return deepVariant;
                    }
                }
                return resolved;
            }

            // Fallback: alte Per-Family-Ozean-Map
            Biome target = oceanTarget.get(season);
            if (target == null) return null;

            if (keepDeepVariants) {
                Biome originalSample = getSampleBiome(chunk);
                if (originalSample != null && isDeepOcean(originalSample)) {
                    Biome deepTarget = getDeepVariant(target);
                    if (deepTarget != null) return deepTarget;
                }
            }
            return target;
        }

        // LAND: Dynamisches Per-Biome-Mapping
        Biome resolved = resolveTargetBiome(chunk, season, variant);
        if (resolved != null) return resolved;

        // Fallback: alte Per-Family-Land-Map
        return seasonTarget.get(season);
    }

    /**
     * Löst das Ziel-Biom dynamisch aus dem Original-Biom des Chunks auf.
     *
     * <p>Schema: {@code seasons:<variant>_<biomeKey>}<br>
     * Beispiele: {@code seasons:fall_forest}, {@code seasons:winter_taiga},
     * {@code seasons:early_fall_dark_forest}</p>
     *
     * @param chunk   der betroffene Chunk
     * @param season  die aktuelle Saison
     * @param variant der Varianten-Name (z.B. "fall", "early_fall", "winter")
     * @return das aufgelöste Biome oder {@code null} wenn nicht gefunden
     */
    public Biome resolveTargetBiome(Chunk chunk, Season season, String variant) {
        return resolveTargetBiome(chunk, season, variant, Double.NaN);
    }

    public Biome resolveTargetBiome(Chunk chunk, Season season, String variant, double temperature) {
        Biome original = getSampleBiome(chunk);
        if (original == null) return null;

        String biomeKey = getBiomeKey(original);
        if (biomeKey == null) return null;

        // Prüfe, ob das Original-Biom in der enabled_biomes-Liste steht
        if (!seasonColorConfig.isBiomeEnabled(original)) return null;

        if (!Double.isNaN(temperature)
                && frostConfig.isEnabled()
                && temperature < frostConfig.getFreezeThreshold()
                && frostConfig.isFrostAllowedInBiome(original)) {
            Biome frostBiome = resolveFrostBiome(biomeKey);
            if (frostBiome != null) {
                return frostBiome;
            }
        }

        // Baue den NamespacedKey: seasons:<variant>_<biomeKey>
        String customName = variant + "_" + biomeKey;
        NamespacedKey key = NamespacedKey.fromString("seasons:" + customName.toLowerCase());
        if (key == null) {
            logger.warning("[SeasonBiomeResolver] Ungültiger NamespacedKey: seasons:" + customName);
            return null;
        }

        Biome resolved = Registry.BIOME.get(key);
        if (resolved == null) {
            logger.fine("[SeasonBiomeResolver] Custom-Biome nicht in Registry: " + key);
            return null;
        }

        return resolved;
    }

    private Biome resolveFrostBiome(String biomeKey) {
        String frostName = "frost_" + biomeKey;
        NamespacedKey key = NamespacedKey.fromString("seasons:" + frostName.toLowerCase());
        if (key == null) {
            logger.fine("[SeasonBiomeResolver] Ungueltiger Frost-NamespacedKey: seasons:" + frostName);
            return null;
        }
        Biome frostBiome = Registry.BIOME.get(key);
        if (frostBiome == null) {
            logger.fine("[SeasonBiomeResolver] Frost-Biom nicht in Registry: " + key);
        }
        return frostBiome;
    }

    /**
     * Liefert den Kurznamen eines Biomes (z.B. {@code "swamp"} aus
     * {@code minecraft:swamp}, {@code "birch_forest"} aus
     * {@code minecraft:birch_forest}).
     */
    public String getBiomeKey(Biome biome) {
        return biomeKeyCache.computeIfAbsent(biome, b -> {
            NamespacedKey key = b.getKey();
            if (key != null) return key.getKey(); // nur der Key-Teil, z.B. "forest"
            return b.name().toLowerCase();
        });
    }

    // ---------------------------------------------------------------
    //  Biome-Hilfsmethoden
    // ---------------------------------------------------------------

    /**
     * String-basierte Prüfung auf kalte Biome.
     */
    public boolean isColdBiome(Biome biome) {
        if (biome == null) return false;
        String name = biome.name();

        if (name.equals("WARM_OCEAN") || name.equals("LUKEWARM_OCEAN")) {
            return false;
        }

        return name.contains("SNOWY")
            || name.contains("FROZEN")
            || name.contains("ICE")
            || name.contains("GROVE");
    }

    /**
     * Liefert ein einzelnes Sample-Biom aus der Chunk-Mitte (y=64).
     */
    public Biome getSampleBiome(Chunk chunk) {
        World world = chunk.getWorld();
        int blockX = chunk.getX() << 4;
        int blockZ = chunk.getZ() << 4;
        return world.getBiome(blockX + 8, 64, blockZ + 8);
    }

    public boolean isOceanBiome(Biome biome) {
        if (biome == null) return false;
        return biome.name().contains("OCEAN");
    }

    public boolean isDeepOcean(Biome biome) {
        if (biome == null) return false;
        return biome.name().startsWith("DEEP_");
    }

    /**
     * Versucht, die Deep-Variante eines Ozean-Bioms zu finden.
     */
    public Biome getDeepVariant(Biome oceanBiome) {
        try {
            return Biome.valueOf("DEEP_" + oceanBiome.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------
    //  Getter (für Debug / Tests / Coordinator)
    // ---------------------------------------------------------------

    public int getFamilyCacheSize() { return familyCache.size(); }
    public int getColdChunksSize() { return coldChunks.size(); }
    public Set<String> getColdChunksSet() { return coldChunks; }
    public Map<String, BiomeFamily> getFamilyCacheMap() { return familyCache; }

    /** Entfernt einen Chunk aus allen Caches (bei ChunkLoad/Unload). */
    public void evictChunk(String chunkKey) {
        familyCache.remove(chunkKey);
        coldChunks.remove(chunkKey);
    }

    /** Leert alle Caches. */
    public void clearCaches() {
        familyCache.clear();
        coldChunks.clear();
        biomeKeyCache.clear();
    }
}