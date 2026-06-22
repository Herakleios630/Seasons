package de.ajsch.seasons.visual;

import de.ajsch.seasons.season.Season;
import org.bukkit.block.Biome;

/**
 * Performs linear interpolation between seasonal foliage colors.
 * Includes biome-specific multipliers for future extensibility.
 */
public class ColorCalculator {

    private static final int CHANNEL_RED   = 0xFF0000;
    private static final int CHANNEL_GREEN = 0x00FF00;
    private static final int CHANNEL_BLUE  = 0x0000FF;
    private static final int CHANNEL_SHIFT_RED   = 16;
    private static final int CHANNEL_SHIFT_GREEN = 8;
    private static final int CHANNEL_SHIFT_BLUE  = 0;

    /**
     * Linearly interpolate between two RGB colors channel by channel.
     *
     * @param colorA start color (0xRRGGBB)
     * @param colorB end color (0xRRGGBB)
     * @param factor 0.0 = pure colorA, 1.0 = pure colorB
     * @return interpolated color (0xRRGGBB)
     */
    public static int interpolate(int colorA, int colorB, double factor) {
        if (factor <= 0.0) return colorA;
        if (factor >= 1.0) return colorB;

        int rA = (colorA & CHANNEL_RED)   >> CHANNEL_SHIFT_RED;
        int gA = (colorA & CHANNEL_GREEN) >> CHANNEL_SHIFT_GREEN;
        int bA = (colorA & CHANNEL_BLUE);

        int rB = (colorB & CHANNEL_RED)   >> CHANNEL_SHIFT_RED;
        int gB = (colorB & CHANNEL_GREEN) >> CHANNEL_SHIFT_GREEN;
        int bB = (colorB & CHANNEL_BLUE);

        int r = rA + (int) Math.round((rB - rA) * factor);
        int g = gA + (int) Math.round((gB - gA) * factor);
        int b = bA + (int) Math.round((bB - bA) * factor);

        return (clamp(r) << CHANNEL_SHIFT_RED)
             | (clamp(g) << CHANNEL_SHIFT_GREEN)
             | (clamp(b) << CHANNEL_SHIFT_BLUE);
    }

    /**
     * Calculate the final foliage color for a biome during a season transition.
     * <p>
     * If the biome has a per-biome override for the target season, that override
     * is used. Otherwise, the season's default tint is used. Then a biome-specific
     * multiplier is applied.
     *
     * @param currentSeason       the season that is ending
     * @param targetSeason        the season that is starting
     * @param transitionProgress  value 0.0–1.0 indicating how far into the transition we are
     * @param biome               the target biome
     * @param config              the visual configuration
     * @return the final foliage color (0xRRGGBB)
     */
    public static int calculateSeasonalColor(
        Season currentSeason,
        Season targetSeason,
        double transitionProgress,
        Biome biome,
        VisualConfig config
    ) {
        // If no config loaded, return vanilla-like green
        if (config == null || !config.hasConfig()) {
            return 0x7C9E4F;
        }

        int colorCurrent = resolveColor(currentSeason, biome, config);
        int colorTarget  = resolveColor(targetSeason, biome, config);

        int interpolated = interpolate(colorCurrent, colorTarget, transitionProgress);

        double multiplier = getBiomeMultiplier(biome);
        return applyMultiplier(interpolated, multiplier);
    }

    /**
     * Resolve the effective tint for a season: override first, then default.
     */
    private static int resolveColor(Season season, Biome biome, VisualConfig config) {
        int override = config.getOverrideTint(season, biome);
        return override != -1 ? override : config.getDefaultTint(season);
    }

    /**
     * Get a biome-specific multiplier coefficient.
     * Currently returns 1.0 for all biomes; will be configurable in Phase 2.2+.
     */
    public static double getBiomeMultiplier(Biome biome) {
        // Phase 2.2: load from foliage_tints.yml multipliers section
        return 1.0;
    }

    /**
     * Multiply each RGB channel by a coefficient and clamp to 0–255.
     */
    private static int applyMultiplier(int color, double factor) {
        if (factor == 1.0) return color;

        int r = (int) Math.round(((color & CHANNEL_RED)   >> CHANNEL_SHIFT_RED)   * factor);
        int g = (int) Math.round(((color & CHANNEL_GREEN) >> CHANNEL_SHIFT_GREEN) * factor);
        int b = (int) Math.round(((color & CHANNEL_BLUE))                          * factor);

        return (clamp(r) << CHANNEL_SHIFT_RED)
             | (clamp(g) << CHANNEL_SHIFT_GREEN)
             | (clamp(b) << CHANNEL_SHIFT_BLUE);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}