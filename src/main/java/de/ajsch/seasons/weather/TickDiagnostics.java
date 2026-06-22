package de.ajsch.seasons.weather;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tick diagnostic collector for SnowAccumulator.
 * Tracks detailed counters and state transitions for one accumulateSnow() invocation.
 * All methods are thread-safe for use within the Bukkit scheduler task.
 */
public class TickDiagnostics {

    /** Per-chunk diagnostics collected during this tick */
    public static class ChunkDiag {
        public final String chunkKey;
        public final String mode; // placed, grown, saturated, fullyGrown, ocean
        public int eligibleCount;
        public int placed;
        public int grown;
        public int noGround;
        public int notCapable;
        public int tooWarm;
        public int blocked;
        public int alreadySnow;
        public int staleResets;
        public int growableBefore;
        public int skippedTempLimit;
        public int skippedAlreadyMax;
        public int skippedSnowBlock;
        public int skippedTotalZero;
        public final List<String> staleCoordSamples = new ArrayList<>();

        public ChunkDiag(String chunkKey, String mode) {
            this.chunkKey = chunkKey;
            this.mode = mode;
        }
    }

    public final long tickStart = System.currentTimeMillis();
    public final List<ChunkDiag> chunks = new ArrayList<>();
    public int totalChunksVisited;
    public int totalPlaced;
    public int totalGrown;
    public int totalStaleResets;
    public int oceansSkipped;
    public int fullyGrownSkipped;
    public int saturatedChunks;
    public int placementChunks;
    public int totalEligibleSum;
    public int totalGrowableSum;

    private final ConcurrentHashMap<String, ChunkDiag> byKey = new ConcurrentHashMap<>();

    public ChunkDiag getOrCreate(String chunkKey, String mode) {
        return byKey.computeIfAbsent(chunkKey, k -> {
            ChunkDiag d = new ChunkDiag(k, mode);
            synchronized (chunks) {
                chunks.add(d);
            }
            return d;
        });
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "[SnowAcc] TICK placed=%d grown=%d staleResets=%d chunks=%d (place=%d sat=%d ocean=%d fullyGrown=%d) eligibleAvg=%.1f growableAvg=%.1f",
            totalPlaced, totalGrown, totalStaleResets, totalChunksVisited,
            placementChunks, saturatedChunks, oceansSkipped, fullyGrownSkipped,
            placementChunks > 0 ? (double) totalEligibleSum / placementChunks : 0.0,
            saturatedChunks > 0 ? (double) totalGrowableSum / saturatedChunks : 0.0
        ));

        // Append per-chunk details when anything unusual occurred
        int anomalyCount = 0;
        for (ChunkDiag d : chunks) {
            if (d.staleResets > 0 || d.blocked > 0 || d.alreadySnow > 0
                || (d.mode.equals("saturated") && d.grown == 0 && d.growableBefore > 0)) {
                if (anomalyCount < 8) {
                    sb.append("\n  ");
                    sb.append(String.format("%s mode=%s eligible=%d placed=%d grown=%d stale=%d blocked=%d alreadySnow=%d growableB4=%d skipTemp=%d skipMax=%d skipSnowBlock=%d skipZero=%d",
                        d.chunkKey, d.mode, d.eligibleCount, d.placed, d.grown, d.staleResets,
                        d.blocked, d.alreadySnow, d.growableBefore,
                        d.skippedTempLimit, d.skippedAlreadyMax, d.skippedSnowBlock, d.skippedTotalZero));
                    if (!d.staleCoordSamples.isEmpty()) {
                        sb.append(" staleAt=").append(String.join(",", d.staleCoordSamples.subList(0, Math.min(3, d.staleCoordSamples.size()))));
                    }
                }
                anomalyCount++;
            }
        }
        if (anomalyCount > 8) {
            sb.append("\n  ... and ").append(anomalyCount - 8).append(" more anomalous chunks");
        }
        return sb.toString();
    }
}