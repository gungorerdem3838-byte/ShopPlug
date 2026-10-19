package com.simpleshop.util;

import com.simpleshop.SimpleShopPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** messages.yml okuyucu. Eksik anahtarlar jar içindeki varsayılandan gelir. */
public final class Messages {

    private final SimpleShopPlugin plugin;
    private YamlConfiguration yaml = new YamlConfiguration();

    public Messages(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                yaml.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // varsayılanlar yüklenemezse dosyadaki değerler kullanılır
        }
    }

    /** Ham (renk kodlu) metin; {anahtar} yer tutucuları kv çiftleriyle doldurulur. */
    public String raw(String key, Object... kv) {
        String value = yaml.getString(key);
        if (value == null) {
            return "&c<" + key + ">";
        }
        return apply(value, kv);
    }

    public Component get(String key, Object... kv) {
        return Text.chat(raw(key, kv));
    }

    /** Önek ekleyerek gönderir. */
    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(Text.chat(raw("prefix") + raw(key, kv)));
    }

    /** Önek olmadan gönderir. */
    public void sendPlain(CommandSender to, String key, Object... kv) {
        to.sendMessage(Text.chat(raw(key, kv)));
    }

    public static String apply(String text, Object... kv) {
        String result = text;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            result = result.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return result;
    }
}
