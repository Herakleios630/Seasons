package de.ajsch.seasons;

import de.ajsch.seasons.commands.SeasonAdminCommand;
import de.ajsch.seasons.commands.MainSeasonCommand;
import de.ajsch.seasons.commands.SeasonCommand;
import de.ajsch.seasons.config.ConfigManager;
import de.ajsch.seasons.config.ResourceCopier;
import de.ajsch.seasons.listener.PlayerJoinListener;
import de.ajsch.seasons.listener.PlayerMoveListener;
import de.ajsch.seasons.listener.SeasonChangeListener;
import de.ajsch.seasons.listener.SnowListener;
import de.ajsch.seasons.persistence.SeasonsDataStore;
import de.ajsch.seasons.season.Season;
import de.ajsch.seasons.season.SeasonChangeEvent;
import de.ajsch.seasons.season.SeasonClock;
import de.ajsch.seasons.season.SeasonConfig;
import de.ajsch.seasons.temperature.BiomeTemperature;
import de.ajsch.seasons.temperature.TemperatureCalculator;
import de.ajsch.seasons.temperature.TemperatureConfig;
import de.ajsch.seasons.weather.SnowAccumulator;
import de.ajsch.seasons.weather.WeatherConfig;
import de.ajsch.seasons.weather.WeatherInterceptor;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SeasonsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private SeasonsDataStore dataStore;
    private SeasonClock clock;
    private BiomeTemperature biomeTemp;
    private SnowAccumulator snowAccumulator;

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
        World overworld = worlds.get(0);

        SeasonConfig seasonConfig = new SeasonConfig(configManager);
        clock = new SeasonClock(overworld, dataStore, seasonConfig);

        TemperatureConfig tempConfig = new TemperatureConfig(configManager);
        TemperatureCalculator tempCalc = new TemperatureCalculator(seasonConfig, tempConfig, biomeTemp);

        WeatherConfig weatherConfig = new WeatherConfig(configManager);

        WeatherInterceptor weatherInterceptor = new WeatherInterceptor(this, clock, tempCalc, biomeTemp, weatherConfig);
        getServer().getPluginManager().registerEvents(weatherInterceptor, this);

        SeasonChangeListener seasonChangeListener = new SeasonChangeListener(this, weatherInterceptor, weatherConfig);
        getServer().getPluginManager().registerEvents(seasonChangeListener, this);

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

        snowAccumulator = new SnowAccumulator(
            this, clock, tempCalc, weatherConfig,
            configManager.getChunkScanIntervalTicks(),
            configManager.getMaxSnowChunksPerTick()
        );
        snowAccumulator.start();

        SnowListener snowListener = new SnowListener(this, clock, tempCalc, snowAccumulator, weatherConfig);
        getServer().getPluginManager().registerEvents(snowListener, this);

        PlayerJoinListener joinListener = new PlayerJoinListener(clock, tempCalc, biomeTemp, weatherConfig);
        getServer().getPluginManager().registerEvents(joinListener, this);

        PlayerMoveListener moveListener = new PlayerMoveListener(clock, tempCalc, biomeTemp, weatherConfig);
        getServer().getPluginManager().registerEvents(moveListener, this);

        SeasonCommand infoCommand = new SeasonCommand(clock, tempCalc, biomeTemp);
        SeasonAdminCommand adminCommand = new SeasonAdminCommand(clock, tempCalc, configManager, biomeTemp);
        MainSeasonCommand mainCommand = new MainSeasonCommand(infoCommand, adminCommand);
        getCommand("season").setExecutor(mainCommand);

        getLogger().info("Seasons ready. Season: " + clock.getCurrentSeason().getDisplayName());
    }

    @Override
    public void onDisable() {
        if (dataStore != null) {
            dataStore.save();
        }
        getLogger().info("Seasons Plugin disabled.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public SeasonsDataStore getDataStore() { return dataStore; }
}