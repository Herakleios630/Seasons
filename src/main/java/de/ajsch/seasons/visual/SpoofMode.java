package de.ajsch.seasons.visual;

/**
 * Betriebsmodi für das Biome-Spoofing-System.
 */
public enum SpoofMode {
    OFF,
    GLOBAL_RING;

    /**
     * Konvertiert einen Config-String in den passenden SpoofMode.
     * Unbekannte Werte fallen auf OFF zurück.
     */
    public static SpoofMode fromString(String value) {
        if (value == null) return OFF;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFF;
        }
    }
}