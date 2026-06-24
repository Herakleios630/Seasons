package de.ajsch.seasons.visual;

import de.ajsch.seasons.config.ConfigManager;
import de.ajsch.seasons.season.Season;
import org.bukkit.World;

import java.util.logging.Logger;

/**
 * Zustandsmaschine für schrittweise saisonale Biome-Übergänge über mehrere Nächte.
 *
 * <p>Wird durch ein {@link de.ajsch.seasons.season.SeasonChangeEvent} initialisiert
 * und schaltet nach jeder konfigurierten Anzahl von Nächten eine Sub-Variante weiter.
 * Der {@link BiomeSpoofCoordinator} fragt {@link #getCurrentVariant()} ab und übergibt
 * den Variant-Namen (z.B. {@code "early_fall"}) an den {@link SeasonBiomeResolver},
 * der daraus den vollen Custom-Biome-Key {@code seasons:early_fall_forest} baut.</p>
 */
public class TransitionManager {

    private final SeasonColorConfig seasonColorConfig;
    private final ConfigManager configManager;
    private final Logger logger;

    // --- Zustand ---
    private Season fromSeason;
    private Season toSeason;
    private int totalSteps;
    private int currentStep;       // 0 = vor dem ersten Nacht-Schritt
    private long nextTransitionTick;
    private String[] variantSequence;
    private boolean active;

    public TransitionManager(SeasonColorConfig seasonColorConfig, ConfigManager configManager, Logger logger) {
        this.seasonColorConfig = seasonColorConfig;
        this.configManager = configManager;
        this.logger = logger;
    }

    // ---------------------------------------------------------------
    //  Public API
    // ---------------------------------------------------------------

    /**
     * Startet eine neue Transition von {@code from} nach {@code to}.
     *
     * @param from  Quell-Saison
     * @param to    Ziel-Saison
     * @param world die Overworld (für Zeitberechnung)
     * @return {@code true} wenn eine Transition gestartet wurde (steps > 0)
     */
    public boolean startTransition(Season from, Season to, World world) {
        totalSteps = seasonColorConfig.getTransitionSteps(from, to);
        if (totalSteps <= 0) {
            logger.fine("[TransitionManager] Keine Transition für " + from + " → " + to + " (steps=" + totalSteps + ")");
            active = false;
            return false;
        }

        this.fromSeason = from;
        this.toSeason = to;
        this.currentStep = 0;
        this.active = true;

        variantSequence = buildVariantSequence(from, to, totalSteps);
        nextTransitionTick = calculateNextNight(world.getTime());

        // Nächste Nacht + (nights_per_step - 1) Tage, da der erste Schritt
        // bei der nächsten Nacht erfolgt, dann jeder weitere nach nights_per_step Nächten.
        int nightsPerStep = configManager.getTransitionNightsPerStep();
        if (nightsPerStep > 1) {
            nextTransitionTick += (long) (nightsPerStep - 1) * 24000L;
        }

        logger.info(String.format(
            "[TransitionManager] Transition gestartet: %s → %s (%d Schritte, %d Nacht/Tick pro Schritt, nächster Tick: %d, Varianten: %s)",
            from, to, totalSteps, nightsPerStep, nextTransitionTick,
            String.join(", ", variantSequence)));
        return true;
    }

    /**
     * Wird alle 40 Ticks vom {@link BiomeSpoofCoordinator} aufgerufen.
     * Prüft, ob die nächste Nacht erreicht wurde, und schaltet ggf. einen Schritt weiter.
     *
     * @param currentTick aktuelle Server-Tick-Nummer
     * @param world       die Overworld
     */
    public void tick(long currentTick, World world) {
        if (!active) return;

        if (currentTick >= nextTransitionTick) {
            currentStep++;

            if (currentStep >= totalSteps) {
                active = false;
                logger.info(String.format(
                    "[TransitionManager] Transition abgeschlossen: %s → %s (letzter Schritt erreicht)",
                    fromSeason, toSeason));
                return;
            }

            // Nächste Nacht berechnen
            int nightsPerStep = configManager.getTransitionNightsPerStep();
            nextTransitionTick += (long) nightsPerStep * 24000L;

            logger.info(String.format(
                "[TransitionManager] Schritt %d/%d: Variant='%s', nächster Tick=%d",
                currentStep, totalSteps, getCurrentVariant(), nextTransitionTick));
        }
    }

    /**
     * Liefert den aktuellen Variant-Namen OHNE Biome-Key.
     *
     * <p>Vor dem ersten Schritt (currentStep==0) wird der Name der Quell-Saison
     * zurückgegeben (z.B. {@code "summer"}). Nach jedem Schritt der entsprechende
     * Eintrag aus der VariantSequence (z.B. {@code "late_summer"}, {@code "early_fall"}).</p>
     *
     * @return Variant-Name oder {@code null} wenn inaktiv
     */
    public String getCurrentVariant() {
        if (!active) return null;
        if (currentStep == 0) {
            return fromSeason.name().toLowerCase();
        }
        return variantSequence[currentStep - 1];
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Bricht die laufende Transition ab.
     */
    public void cancel() {
        if (active) {
            logger.info("[TransitionManager] Transition abgebrochen: " + fromSeason + " → " + toSeason);
            active = false;
        }
    }

    // --- Getter für Tests/Debug ---
    public Season getFromSeason() { return fromSeason; }
    public Season getToSeason() { return toSeason; }
    public int getCurrentStep() { return currentStep; }
    public int getTotalSteps() { return totalSteps; }

    // ---------------------------------------------------------------
    //  Hilfsmethoden
    // ---------------------------------------------------------------

    /**
     * Erzeugt die Variant-Namen für jeden Schritt (OHNE Biome-Key).
     *
     * <p>Benennungsschema:
     * <ul>
     *   <li>1 Schritt: nur Ziel-Saison-Name (z.B. {@code "fall"})</li>
     *   <li>2 Schritte: {@code "late_<from>", "<to>"}</li>
     *   <li>≥3 Schritte: erster = {@code "late_<from>"}, letzter = {@code "<to>"},
     *       mittlere je nach Position {@code "mid_<from>"} oder {@code "early_<to>"}</li>
     * </ul>
     */
    static String[] buildVariantSequence(Season from, Season to, int totalSteps) {
        String[] seq = new String[totalSteps];
        String fromName = from.name().toLowerCase();
        String toName = to.name().toLowerCase();

        if (totalSteps == 1) {
            seq[0] = toName;
            return seq;
        }

        seq[0] = "late_" + fromName;
        seq[totalSteps - 1] = toName;

        for (int i = 1; i < totalSteps - 1; i++) {
            double progress = (double) i / (totalSteps - 1);
            if (progress < 0.5) {
                seq[i] = "mid_" + fromName;
            } else {
                seq[i] = "early_" + toName;
            }
        }

        return seq;
    }

    /**
     * Berechnet den Tick der nächsten Nacht (13000) ab {@code currentTime}.
     * Wenn die aktuelle Zeit bereits nach 13000 liegt, wird die Nacht des
     * nächsten Tages verwendet.
     */
    static long calculateNextNight(long currentTime) {
        long dayStart = (currentTime / 24000L) * 24000L;
        long nightStart = dayStart + 13000L;
        if (nightStart <= currentTime) {
            nightStart += 24000L;
        }
        return nightStart;
    }
}