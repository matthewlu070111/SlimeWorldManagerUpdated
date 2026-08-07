package com.grinderwolf.swm.plugin.config;

import com.google.common.reflect.TypeToken;
import com.grinderwolf.swm.plugin.log.Logging;
import lombok.Getter;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Getter
@ConfigSerializable
public class WorldsConfig {

    @Setting("worlds")
    private Map<String, WorldData> worlds = new HashMap<>();

    public void save() {
        try {
            ConfigManager.getWorldConfigLoader().save(ConfigManager.getWorldConfigLoader().createEmptyNode().setValue(TypeToken.of(WorldsConfig.class), this));
        } catch (IOException | ObjectMappingException ex) {
            Logging.error("Failed to save worlds config file:");
            ex.printStackTrace();
        }
    }

    /**
     * Registers a world in {@code worlds.yml} if it is not already present.
     * Does not overwrite custom settings for an existing entry.
     *
     * @return {@code true} if a new entry was written and saved
     */
    public boolean registerIfAbsent(String worldName, WorldData worldData) {
        if (worlds.containsKey(worldName)) {
            return false;
        }

        worlds.put(worldName, worldData);
        save();
        return true;
    }
}
