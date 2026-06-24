package de.ajsch.seasons;

import de.ajsch.seasons.commands.SeasonAdminCommand;
import de.ajsch.seasons.commands.MainSeasonCommand;
import de.ajsch.seasons.commands.SeasonCommand;
import de.ajsch.seasons.config.ConfigManager;
import de.ajsch.seasons.config.FrostConfig;
import de.ajsch.seasons.config.ResourceCopier;
import de.ajsch.seasons.effects.FrostEffectManager;
import de.ajsch.seasons.listener.BlockEventListener;
import de.ajsch.seasons.listener.PlayerJoinListener;
import de.ajsch.seasons.listener.PlayerMoveListener;
import de.ajsch.seasons.listener.SeasonChangeListener;
import de.ajsch.seasons.listener.SnowListener;
import de.ajsch.seasons.persistence.ChunkCacheStore;
import de.ajsch.seasons.persistence.SeasonsDataStore;
import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonChangeEvent;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.season.SeasonConfig;
import de.ajsch.seasons.temperature.BiomeTemperature;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import de.ajsch.seasons.temperature.TemperatureConfig;
import de.ajsch.seasons.visual.BiomeBackupStore;
import de.ajsch.seasons.visual.BiomeJsonGenerator;
import de.ajsch.seasons.visual.BiomeSpoofCoordinator;
import de.ajsch.seasons.visual.BiomeSpoofListener;
import de.ajsch.seasons.visual.ChunkBiomeApplier;
import de.ajsch.seasons.visual.SeasonBiomeResolver;
import de.ajsch.seasons.visual.SeasonColorConfig;
import de.ajsch.seasons.visual.TransitionManager;
import de.ajsch.seasons.visual.VanillaBiomeReference;
import de.ajsch.seasons.weather.ChunkCacheManager;
import de.ajsch.seasons.weather.SnowAccumulator;
import de.ajsch.seasons.weather.WeatherConfig;
import de.ajsch.seasons.weather.WeatherInterceptor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SeasonsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private SeasonsDataStore dataStore;
    private ChunkCacheStore chunkCacheStore;
    private SeasonClock clock;
    private BiomeTemperature biomeTemp;
    private ChunkCacheManager cacheManager;
    private SnowAccumulator snowAccumulator;
    private BiomeBackupStore biomeBackupStore;
    private SeasonBiomeResolver biomeResolver;
    private BiomeSpoofCoordinator biomeSpoofCoordinator;
    private TransitionManager transitionManager;
    private FrostConfig frostConfig;
    private FrostEffectManager frostEffectManager;
    private World overworld;

    @Override
    public void onEnable() {
        getLogger().info("Seasons Plugin enabled.");

        ResourceCopier copier = new ResourceCopier(this);
        copier.copyDefaults();

        configManager = new ConfigManager(this);
        configManager.load();

        dataStore = new SeasonsDataStore(
            this,
            configManager.getPersistenceFile(),
            configManager.getSaveIntervalMinutes()
        );
        dataStore.load();

        biomeTemp = new BiomeTemperature(getDataFolder());
        biomeTemp.load();

        List<World> worlds = getServer().getWorlds();
        if (worlds.isEmpty()) {
            getLogger().warning("No worlds found.");
            return;
        }
        this.overworld = worlds.get(0);

        SeasonConfig seasonConfig = new SeasonConfig(configManager);
        clock = new SeasonClock(this.overworld, dataStore, seasonConfig);

        TemperatureConfig tempConfig = new TemperatureConfig(configManager);
        TemperatureCalculator tempCalc = new TemperatureCalculator(seasonConfig, tempConfig, biomeTemp);

        WeatherConfig weatherConfig = new WeatherConfig(configManager);

        WeatherInterceptor weatherInterceptor = new WeatherInterceptor(this, clock, tempCalc, biomeTemp, weatherConfig);
        getServer().getPluginManager().registerEvents(weatherInterceptor, this);


        

        if (clock.getCurrentSeason() == Season.WINTER) {
            weatherInterceptor.startSnowParticleScheduler();
        }

        new org.bukkit.scheduler.BukkitRunnable() {
            private Season lastSeason = clock.getCurrentSeason();

            @Override
            public void run() {
                Season current = clock.getCurrentSeason();
                if (current != lastSeason) {
                    SeasonChangeEvent event = new SeasonChangeEvent(lastSeason, current,
                        clock.calculateDayOfYear(), clock.getYear());
                    getServer().getPluginManager().callEvent(event);
                    lastSeason = current;
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        cacheManager = new ChunkCacheManager(this, clock, tempCalc, weatherConfig, null);

        chunkCacheStore = new ChunkCacheStore(this, configManager, cacheManager.getCacheMap());
        cacheManager.setChunkCacheStore(chunkCacheStore);
        chunkCacheStore.load();

        snowAccumulator = new SnowAccumulator(
            this, clock, tempCalc, weatherConfig,
            configManager.getChunkScanIntervalTicks(),
            configManager.getMaxSnowChunksPerTick(),
            cacheManager
        );
        snowAccumulator.start();

        SeasonChangeListener seasonChangeListener = new SeasonChangeListener(this, weatherInterceptor, weatherConfig);
        getServer().getPluginManager().registerEvents(seasonChangeListener, this);

        chunkCacheStore.startAsyncSaveTask();

        // Phase 2: BiomeBackupStore initialisieren und vorhandene Backups laden
        biomeBackupStore = new BiomeBackupStore(getDataFolder().toPath(), getLogger());
        biomeBackupStore.loadAll();
        getLogger().info("BiomeBackupStore: " + biomeBackupStore.size() + " Backups geladen.");
        // Phase 2.6d2: FrostConfig früh laden, dann SeasonBiomeResolver +
        // ChunkBiomeApplier + TransitionManager + BiomeSpoofCoordinator
        FrostConfig frostConfig = new FrostConfig(this);
        frostConfig.load();
        this.frostConfig = frostConfig;

        // Phase 2b: FrostEffectManager – SNOWFLAKE particles when temperature < freeze-threshold
        frostEffectManager = new FrostEffectManager(this, frostConfig, tempCalc, clock);
        if (frostConfig.isEnabled()) {
            frostEffectManager.start();
            getLogger().info("FrostEffectManager started.");
        }

        SeasonColorConfig seasonColorConfig = new SeasonColorConfig(this);
        seasonColorConfig.reloadFromConfig();
        biomeResolver = new SeasonBiomeResolver(biomeBackupStore, configManager, seasonColorConfig,
                frostConfig, getLogger());
        ChunkBiomeApplier chunkBiomeApplier = new ChunkBiomeApplier(biomeBackupStore, getLogger());
        transitionManager = new TransitionManager(seasonColorConfig, configManager, getLogger());
        biomeSpoofCoordinator = new BiomeSpoofCoordinator(this, clock, configManager, tempCalc,
                biomeBackupStore, biomeResolver, chunkBiomeApplier, transitionManager);
        biomeSpoofCoordinator.register();

        // Phase 2.4: BiomeSpoofListener registrieren
        BiomeSpoofListener biomeSpoofListener = new BiomeSpoofListener(biomeSpoofCoordinator, biomeBackupStore, getLogger());
        getServer().getPluginManager().registerEvents(biomeSpoofListener, this);










        SnowListener snowListener = new SnowListener(this, clock, tempCalc, snowAccumulator, weatherConfig);
        getServer().getPluginManager().registerEvents(snowListener, this);

        BlockEventListener blockEventListener = new BlockEventListener(snowAccumulator, cacheManager);
        getServer().getPluginManager().registerEvents(blockEventListener, this);

        PlayerJoinListener joinListener = new PlayerJoinListener(clock, tempCalc, biomeTemp, weatherConfig);
        getServer().getPluginManager().registerEvents(joinListener, this);

        PlayerMoveListener moveListener = new PlayerMoveListener(clock, tempCalc, biomeTemp, weatherConfig);
        getServer().getPluginManager().registerEvents(moveListener, this);

        // Phase 2.6c: BiomeJsonGenerator (nutzt das bereits geladene frostConfig)
        VanillaBiomeReference vanillaBiomeReference = new VanillaBiomeReference();
        vanillaBiomeReference.loadFromResources(this);
        BiomeJsonGenerator biomeJsonGenerator = new BiomeJsonGenerator(this, seasonColorConfig, vanillaBiomeReference, frostConfig);

        SeasonCommand infoCommand = new SeasonCommand(clock, tempCalc, biomeTemp);
        SeasonAdminCommand adminCommand = new SeasonAdminCommand(clock, tempCalc, configManager, biomeTemp, biomeJsonGenerator);
        MainSeasonCommand mainCommand = new MainSeasonCommand(infoCommand, adminCommand);
        getCommand("season").setExecutor(mainCommand);

        getLogger().info("Seasons ready. Season: " + clock.getCurrentSeason().getDisplayName());
    }

    public void onDisable() {
        if (frostEffectManager != null) {
            frostEffectManager.stop();
        }
        if (chunkCacheStore != null) {
            chunkCacheStore.save();
        }
        if (dataStore != null) {
            dataStore.save();
        }
        if (biomeSpoofCoordinator != null) {
            biomeSpoofCoordinator.unregister();
        }
        if (biomeBackupStore != null && overworld != null) {
            biomeBackupStore.saveAll(overworld);
            getLogger().info("BiomeBackupStore: " + biomeBackupStore.size() + " Backups gespeichert.");
        }
        getLogger().info("Seasons Plugin disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public SeasonsDataStore getDataStore() { return dataStore; }
}