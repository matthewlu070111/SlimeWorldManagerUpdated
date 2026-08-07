package com.grinderwolf.swm.clsm;

public interface CLSMBridge {

    default Object getChunk(Object world, int x, int z) {
        return null;
    }

    default boolean saveChunk(Object world, Object chunkAccess) {
        return false;
    }

    // Array containing the normal world, the nether and the end
    Object[] getDefaultWorlds();

    boolean isCustomWorld(Object world);

    default boolean skipWorldAdd(Object world) {
        return false; // If true, the world won't be added to the bukkit world list
    }

    /**
     * 1.16+ WorldServer construction may query the server default gamemode before
     * save data exists. Returning a non-null value avoids an NPE when overriding
     * the default world.
     */
    default Object getDefaultGamemode() {
        return null;
    }
}
