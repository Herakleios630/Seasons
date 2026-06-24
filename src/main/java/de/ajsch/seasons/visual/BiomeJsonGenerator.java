package de.ajsch.seasons.visual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.ajsch.seasons.SeasonsPlugin;
import de.ajsch.seasons.config.FrostConfig;
import de.ajsch.seasons.season.Season;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates Custom-Biome JSONs for the {@code seasons_biomes} datapack
 * based on {@code season_colors.yml} and vanilla biome reference data.
 * <p>
 * Produces per-season, per-transition-step biome variants with interpolated
 * grass and foliage colors.
 */
public class BiomeJsonGenerator {

    private final SeasonsPlugin plugin;
    private final SeasonColorConfig seasonColorConfig;
    private final VanillaBiomeReference vanillaBiomeReference;
    private final FrostConfig frostConfig;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATAPACK_ROOT = "datapacks/seasons_biomes";
    private static final String DATA_PATH = "data/seasons/worldgen/biome";
    private static final String HASH_FILE = ".seasons_hash";
    private static final int PACK_FORMAT = 34; // 1.21.5

    public BiomeJsonGenerator(SeasonsPlugin plugin,
                              SeasonColorConfig seasonColorConfig,
                              VanillaBiomeReference vanillaBiomeReference,
                              FrostConfig frostConfig) {
        this.plugin = plugin;
        this.seasonColorConfig = seasonColorConfig;
        this.vanillaBiomeReference = vanillaBiomeReference;
        this.frostConfig = frostConfig;
    }

    /**
     * Generates all custom biome JSONs if the {@code season_colors.yml} changed
     * since the last run, or if {@code force} is {@code true}.
     *
     * @param force if true, regenerates even if the config hash hasn't changed
     * @return number of JSON files generated
     */
    public int generate(boolean force) {
        World world = getOverworld();
        if (world == null) {
            plugin.getLogger().warning("[BiomeJsonGenerator] No overworld found, cannot generate biomes.");
            return 0;
        }

        // Datapacks muessen im World-Root-Ordner liegen: <world>/datapacks/...
        // Paper 1.21.5: world.getWorldFolder() liefert den Dimension-Ordner
        // (z.B. world/dimensions/minecraft/overworld), nicht den World-Root.
        // Korrekte Basis: Bukkit.getWorldContainer() / worldName
        Path worldDir = Bukkit.getWorldContainer().toPath().resolve(world.getName());
        Path datapackRoot = worldDir.resolve(DATAPACK_ROOT);
        Path hashFile = datapackRoot.resolve(HASH_FILE);

        // Compute current hash of season_colors.yml
        String currentHash = computeSeasonColorsHash();
        if (currentHash == null) {
            plugin.getLogger().warning("[BiomeJsonGenerator] Cannot compute season_colors.yml hash, aborting.");
            return 0;
        }

        // Check if regeneration is needed
        if (!force && currentHash.equals(readSavedHash(hashFile))) {
            plugin.getLogger().info("[BiomeJsonGenerator] season_colors.yml unchanged, skipping generation.");
            return 0;
        }

        // Prepare output directory
        Path biomeDir = datapackRoot.resolve(DATA_PATH);
        try {
            Files.createDirectories(biomeDir);
        } catch (IOException e) {
            plugin.getLogger().severe("[BiomeJsonGenerator] Failed to create datapack directory: " + e.getMessage());
            return 0;
        }

        List<String> generatedFiles = new ArrayList<>();
        int count = 0;

        for (String biomeEntry : seasonColorConfig.getEnabledBiomes()) {
            Biome biome = resolveBiome(biomeEntry);
            if (biome == null) {
                plugin.getLogger().warning("[BiomeJsonGenerator] Unknown biome: " + biomeEntry + ", skipping.");
                continue;
            }

            String rawJson = vanillaBiomeReference.getRawJson(biome);
            if (rawJson == null) {
                plugin.getLogger().warning(
                        "[BiomeJsonGenerator] No vanilla JSON for biome: " + biomeEntry + ", skipping.");
                continue;
            }

            String biomeKey = biome.getKey().getKey().toLowerCase();

            // Generate variants for each season and each transition step
            count += generateBiomeVariants(biome, biomeKey, rawJson, biomeDir, generatedFiles);

            // Generate frost variant
            count += generateFrostVariant(biome, biomeKey, rawJson, biomeDir);
        }

        // Write pack.mcmeta
        writePackMcmeta(datapackRoot);

        // Save hash
        writeSavedHash(hashFile, currentHash);

        plugin.getLogger().info(String.format(
                "[BiomeJsonGenerator] Generated %d biome JSONs for %d biomes in %s",
                count, generatedFiles.size(), datapackRoot.toString()));
        if (!generatedFiles.isEmpty()) {
            plugin.getLogger().info("[BiomeJsonGenerator] Biomes: " + String.join(", ", generatedFiles));
        }

        return count;
    }

    /**
     * Generates all season and transition variants for a single biome.
     */
    private int generateBiomeVariants(Biome biome, String biomeKey, String rawBaseJson,
                                      Path biomeDir, List<String> generatedFiles) {
        int count = 0;
        generatedFiles.add(biomeKey);

        // Parse base JSON once
        JsonObject baseJson = JsonParser.parseString(rawBaseJson).getAsJsonObject();

        // Season order for transitions: SUMMER -> FALL -> WINTER -> SPRING -> SUMMER
        Season[] seasons = Season.values();
        // We use the order: SPRING(0), SUMMER(1), FALL(2), WINTER(3)
        // Transitions: SUMMER->FALL, FALL->WINTER, WINTER->SPRING, SPRING->SUMMER

        // Generate the four base season variants
        for (Season season : seasons) {
            int grassTarget = seasonColorConfig.getGrassColorTarget(biome, season);
            int foliageTarget = seasonColorConfig.getFoliageColorTarget(biome, season);

            String variantName = season.name().toLowerCase();
            JsonObject variantJson = deepCopy(baseJson);
            applyColors(variantJson, grassTarget, foliageTarget);
            applyAtmosphere(variantJson, season);
            writeVariantJson(biomeDir, biomeKey, variantName, variantJson);
            count++;
        }

        // Generate transition sub-variants
        // Transition pairs with ordering
        Season[] fromSeasons = {Season.SUMMER, Season.FALL, Season.WINTER, Season.SPRING};
        Season[] toSeasons   = {Season.FALL,   Season.WINTER, Season.SPRING, Season.SUMMER};

        for (int i = 0; i < fromSeasons.length; i++) {
            Season from = fromSeasons[i];
            Season to = toSeasons[i];
            int steps = seasonColorConfig.getTransitionSteps(from, to);

            int grassFrom = seasonColorConfig.getGrassColorTarget(biome, from);
            int foliageFrom = seasonColorConfig.getFoliageColorTarget(biome, from);
            int grassTo   = seasonColorConfig.getGrassColorTarget(biome, to);
            int foliageTo = seasonColorConfig.getFoliageColorTarget(biome, to);

            String toName = to.name().toLowerCase();

            for (int step = 1; step < steps; step++) {
                double factor = (double) step / (double) steps;
                int grass = lerpColor(grassFrom, grassTo, factor);
                int foliage = lerpColor(foliageFrom, foliageTo, factor);

                String prefix = getTransitionPrefix(step, steps);
                String variantName = prefix + toName;
                JsonObject variantJson = deepCopy(baseJson);
                applyColors(variantJson, grass, foliage);
                applyAtmosphere(variantJson, to);
                writeVariantJson(biomeDir, biomeKey, variantName, variantJson);
                count++;
            }
        }

        return count;
    }

    /**
     * Generates a frost variant for a biome using FrostConfig color targets.
     */
    private int generateFrostVariant(Biome biome, String biomeKey, String rawBaseJson,
                                     Path biomeDir) {
        if (!frostConfig.isEnabled()) {
            return 0;
        }
        if (!frostConfig.isFrostAllowedInBiome(biome)) {
            return 0;
        }

        JsonObject baseJson = JsonParser.parseString(rawBaseJson).getAsJsonObject();
        int grassColor = frostConfig.getTargetGrassColor();
        int foliageColor = frostConfig.getTargetFoliageColor();

        JsonObject variantJson = deepCopy(baseJson);
        applyColors(variantJson, grassColor, foliageColor);
        variantJson.addProperty("temperature", frostConfig.getFrostBiomeTemperature());
        writeVariantJson(biomeDir, biomeKey, "frost", variantJson);

        return 1;
    }

    /**
     * Returns the variant name prefix for a given transition step.
     * Steps are 1..(totalSteps-1).
     */
    private String getTransitionPrefix(int step, int totalSteps) {
        // We only have steps 1..totalSteps-1 (step 0 = start season, step N = end season)
        // Name based on position:
        // For 2 sub-variants: "early_", "late_"
        // For 3 sub-variants: "early_", "mid_", "late_"
        // For 1 sub-variant: "early_"
        int numSubVariants = totalSteps - 1;
        if (numSubVariants == 1) return "early_";
        if (numSubVariants == 2) {
            return step == 1 ? "early_" : "late_";
        }
        // numSubVariants >= 3
        if (step == 1) return "early_";
        if (step == numSubVariants) return "late_";
        return "mid_";
    }

    /**
     * Writes a single variant JSON to the datapack folder.
     */
    /**
     * Writes a single variant JSON as {@code {variantName}_{biomeKey}.json}
     * directly into the biome directory (flat structure).
     * Registry key becomes {@code seasons:{variantName}_{biomeKey}}.
     */
    private void writeVariantJson(Path biomeDir, String biomeKey, String variantName, JsonObject json) {
        String fileName = variantName + "_" + biomeKey + ".json";
        File outputFile = biomeDir.resolve(fileName).toFile();
        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "[BiomeJsonGenerator] Failed to write " + outputFile.getPath() + ": " + e.getMessage());
        }
    }

    /**
     * Applies grass and foliage color overrides to the effects section of the biome JSON.
     */
    private void applyColors(JsonObject root, int grassColor, int foliageColor) {
        JsonObject effects = root.getAsJsonObject("effects");
        if (effects == null) {
            effects = new JsonObject();
            root.add("effects", effects);
        }
        effects.addProperty("grass_color", String.format("#%06X", grassColor));
        effects.addProperty("foliage_color", String.format("#%06X", foliageColor));
    }

    /**
     * Applies optional fog_color, sky_color, water_fog_color from season_colors.yml.
     * Only sets values if the config provides them (non-null).
     */
    private void applyAtmosphere(JsonObject root, Season season) {
        JsonObject effects = root.getAsJsonObject("effects");
        if (effects == null) {
            effects = new JsonObject();
            root.add("effects", effects);
        }
        Integer fogColor = seasonColorConfig.getFogColor(season);
        if (fogColor != null) {
            effects.addProperty("fog_color", String.format("#%06X", fogColor));
        }
        Integer skyColor = seasonColorConfig.getSkyColor(season);
        if (skyColor != null) {
            effects.addProperty("sky_color", String.format("#%06X", skyColor));
        }
        Integer waterFogColor = seasonColorConfig.getWaterFogColor(season);
        if (waterFogColor != null) {
            effects.addProperty("water_fog_color", String.format("#%06X", waterFogColor));
        }
    }

    /**
     * Deep-copies a Gson JsonObject.
     */
    private JsonObject deepCopy(JsonObject original) {
        return JsonParser.parseString(original.toString()).getAsJsonObject();
    }

    /**
     * Linear interpolation between two RGB colors.
     */
    private int lerpColor(int colorA, int colorB, double factor) {
        int rA = (colorA >> 16) & 0xFF;
        int gA = (colorA >> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int rB = (colorB >> 16) & 0xFF;
        int gB = (colorB >> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int r = (int) Math.round(rA + (rB - rA) * factor);
        int g = (int) Math.round(gA + (gB - gA) * factor);
        int b = (int) Math.round(bA + (bB - bA) * factor);

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Writes pack.mcmeta for the seasons_biomes datapack.
     */
    private void writePackMcmeta(Path datapackRoot) {
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "Seasons plugin – seasonal biome color variants");
        root.add("pack", pack);

        File mcmetaFile = datapackRoot.resolve("pack.mcmeta").toFile();
        try (FileWriter writer = new FileWriter(mcmetaFile, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            plugin.getLogger().severe(
                    "[BiomeJsonGenerator] Failed to write pack.mcmeta: " + e.getMessage());
        }
    }

    /**
     * Computes an MD5 hash of the season_colors.yml file content.
     */
    private String computeSeasonColorsHash() {
        File configFile = new File(plugin.getDataFolder(), "season_colors.yml");
        if (!configFile.exists()) return null;
        try {
            byte[] bytes = Files.readAllBytes(configFile.toPath());
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().severe("[BiomeJsonGenerator] Hash computation failed: " + e.getMessage());
            return null;
        }
    }

    private String readSavedHash(Path hashFile) {
        try {
            if (Files.exists(hashFile)) {
                return new String(Files.readAllBytes(hashFile), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            // ignore
        }
        return "";
    }

    private void writeSavedHash(Path hashFile, String hash) {
        try {
            Files.write(hashFile, hash.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("[BiomeJsonGenerator] Failed to write hash file: " + e.getMessage());
        }
    }

    private World getOverworld() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) return null;
        return worlds.get(0);
    }

    private Biome resolveBiome(String biomeKey) {
        NamespacedKey key = NamespacedKey.fromString(biomeKey);
        if (key == null) return null;
        return Registry.BIOME.get(key);
    }
}