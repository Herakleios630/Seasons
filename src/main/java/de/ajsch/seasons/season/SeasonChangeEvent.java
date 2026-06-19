package de.ajsch.seasons.season;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SeasonChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Season oldSeason;
    private final Season newSeason;
    private final int currentDay;
    private final int year;

    public SeasonChangeEvent(Season oldSeason, Season newSeason, int currentDay, int year) {
        this.oldSeason = oldSeason;
        this.newSeason = newSeason;
        this.currentDay = currentDay;
        this.year = year;
    }

    public Season getOldSeason() { return oldSeason; }
    public Season getNewSeason() { return newSeason; }
    public int getCurrentDay() { return currentDay; }
    public int getYear() { return year; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}