package com.grinderwolf.swm.api.exceptions;

/**
 * Exception thrown when SWM is loaded
 * on a non-supported Spigot version.
 */
public class InvalidVersionException extends SlimeException {

    public InvalidVersionException(String version) {
        super("SlimeWorldManager does not support Spigot " + version
                + "! Supported range: 1.8.8–1.17.1 (Spigot/Paper). "
                + "For 1.18+ use AdvancedSlimeWorldManager / AdvancedSlimePaper.");
    }
}
