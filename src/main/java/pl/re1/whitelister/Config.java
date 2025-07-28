package pl.re1.whitelister;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

public class Config {

    private static FileConfiguration main_config;
    private static FileConfiguration message_config;

    private static @Nullable FileConfiguration createCustomConfig(String file_name) {
        File config_file = new File(Whitelister.instance.getDataFolder(), file_name);

        if (!config_file.exists()) {
            config_file.getParentFile().mkdirs();

            Whitelister.instance.saveResource(file_name, false);
        }

        FileConfiguration config = new YamlConfiguration();

        try {
            config.load(config_file);

            return config;
        } catch (IOException | InvalidConfigurationException e) {
            Whitelister.instance.getLogger().severe("Loading config `" + file_name + "` failed.");
        }

        return null;
    }

    public static boolean loadConfigs() {
        main_config = createCustomConfig("config.yml");
        message_config = createCustomConfig("messages.yml");

        return message_config != null;
    }

    public static FileConfiguration getConfig() {
        return main_config;
    }

    public static FileConfiguration getMessageConfig() {
        return message_config;
    }
}
