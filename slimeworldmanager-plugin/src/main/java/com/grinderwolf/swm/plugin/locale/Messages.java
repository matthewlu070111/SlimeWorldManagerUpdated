package com.grinderwolf.swm.plugin.locale;

import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.log.Logging;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Simple YAML-based message provider with English, Simplified Chinese, and Traditional Chinese.
 * Language files are always read as UTF-8 so Chinese text is not mojibake on Windows (GBK) JVMs.
 */
public final class Messages {

    private static final String[] SUPPORTED = {"en", "zh_CN", "zh_TW"};

    private static FileConfiguration messages;
    private static String activeLanguage = "en";

    private Messages() {
    }

    public static void initialize(String configuredLanguage) {
        activeLanguage = resolveLanguage(configuredLanguage);
        File langDir = new File(SWMPlugin.getInstance().getDataFolder(), "lang");
        langDir.mkdirs();

        for (String lang : SUPPORTED) {
            File out = new File(langDir, lang + ".yml");
            if (!out.exists()) {
                try (InputStream in = SWMPlugin.getInstance().getResource("lang/" + lang + ".yml")) {
                    if (in != null) {
                        Files.copy(in, out.toPath());
                    }
                } catch (IOException ex) {
                    SWMPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to copy lang/" + lang + ".yml", ex);
                }
            }
        }

        File langFile = new File(langDir, activeLanguage + ".yml");
        if (!langFile.exists()) {
            activeLanguage = "en";
            langFile = new File(langDir, "en.yml");
        }

        // Always load with UTF-8. YamlConfiguration.loadConfiguration(File) uses the platform
        // default charset on older Bukkit (e.g. GBK on Chinese Windows), which garbles zh_CN/zh_TW.
        messages = loadYamlUtf8(langFile);

        // Merge missing keys from the jar defaults so upgrades stay complete
        try (InputStream in = SWMPlugin.getInstance().getResource("lang/" + activeLanguage + ".yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                messages.setDefaults(defaults);
                messages.options().copyDefaults(true);
            }
        } catch (IOException ignored) {
        }

        // Fall back to English for any remaining missing keys
        if (!"en".equals(activeLanguage)) {
            try (InputStream in = SWMPlugin.getInstance().getResource("lang/en.yml")) {
                if (in != null) {
                    YamlConfiguration en = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                    messages.addDefaults(en);
                }
            } catch (IOException ignored) {
            }
        }

        Logging.info("Language set to " + activeLanguage + ".");
    }

    /** Load a YAML file from disk using UTF-8 (required for Simplified/Traditional Chinese). */
    private static FileConfiguration loadYamlUtf8(File file) {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            SWMPlugin.getInstance().getLogger().log(Level.WARNING,
                    "Failed to load language file " + file.getName() + " as UTF-8", ex);
            return new YamlConfiguration();
        }
    }

    public static String getActiveLanguage() {
        return activeLanguage;
    }

    public static String get(String key) {
        if (messages == null) {
            return key;
        }

        String raw = messages.getString(key);
        if (raw == null) {
            return key;
        }

        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public static String get(String key, Object... args) {
        String msg = get(key);
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }

    /** Message with command prefix. */
    public static String prefixed(String key, Object... args) {
        return Logging.COMMAND_PREFIX + get(key, args);
    }

    public static void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(prefixed(key, args));
    }

    private static String resolveLanguage(String configured) {
        if (configured == null || configured.trim().isEmpty() || "auto".equalsIgnoreCase(configured.trim())) {
            Locale locale = Locale.getDefault();
            String lang = locale.getLanguage();
            String country = locale.getCountry();

            if ("zh".equalsIgnoreCase(lang)) {
                // Taiwan / Hong Kong / Macau -> Traditional; everything else Chinese -> Simplified
                if ("TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country) || "MO".equalsIgnoreCase(country)) {
                    return "zh_TW";
                }
                return "zh_CN";
            }

            return "en";
        }

        String normalized = configured.trim().replace('-', '_');
        if ("zh".equalsIgnoreCase(normalized) || "zh_cn".equalsIgnoreCase(normalized) || "zh_hans".equalsIgnoreCase(normalized)) {
            return "zh_CN";
        }
        if ("zh_tw".equalsIgnoreCase(normalized) || "zh_hk".equalsIgnoreCase(normalized) || "zh_hant".equalsIgnoreCase(normalized)) {
            return "zh_TW";
        }
        if ("en".equalsIgnoreCase(normalized) || "en_us".equalsIgnoreCase(normalized) || "en_gb".equalsIgnoreCase(normalized)) {
            return "en";
        }

        // Accept exact file names without .yml
        for (String supported : SUPPORTED) {
            if (supported.equalsIgnoreCase(normalized)) {
                return supported;
            }
        }

        Logging.warning("Unknown language '" + configured + "', falling back to English.");
        return "en";
    }
}
