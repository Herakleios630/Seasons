package de.ajsch.seasons.season;

public enum Season {
    SPRING,
    SUMMER,
    FALL,
    WINTER;

    public String getDisplayName() {
        return switch (this) {
            case SPRING -> "Frühling";
            case SUMMER -> "Sommer";
            case FALL -> "Herbst";
            case WINTER -> "Winter";
        };
    }
}