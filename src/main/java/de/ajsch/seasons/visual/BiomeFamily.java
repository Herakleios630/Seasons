package de.ajsch.seasons.visual;

/**
 * Klassifiziert einen Chunk als LAND oder OCEAN, um
 * das passende Season-Spoofing-Target-Biome auszuwählen.
 */
public enum BiomeFamily {
    LAND,
    OCEAN;

    /**
     * Konvertiert einen Config-String in die passende BiomeFamily.
     * Unbekannte Werte fallen auf LAND zurück.
     */
    public static BiomeFamily fromString(String value) {
        if (value == null) return LAND;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LAND;
        }
    }
}