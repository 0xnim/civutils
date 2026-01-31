package xyz.nim.civutils.utils;

import xyz.nim.lib.mc121.compat.McCompat;

/**
 * Helper class for GameProfile operations that have version-specific APIs.
 * Used by mixins to handle API differences.
 */
public class GameProfileHelper {

    /**
     * Get the name from a GameProfile in a version-agnostic way.
     * @param gameProfile The GameProfile object
     * @return The player name, or null if not available
     */
    public static String getName(Object gameProfile) {
        return McCompat.get().getGameProfileName(gameProfile);
    }
}
