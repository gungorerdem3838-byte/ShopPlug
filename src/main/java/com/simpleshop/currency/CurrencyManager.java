package com.simpleshop.currency;

import com.simpleshop.SimpleShopPlugin;
import com.simpleshop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tüm para birimlerinin kaydı. config.yml -> currencies bölümünden okunur.
 * <ul>
 *   <li>type: VAULT    -> Vault ekonomisini otomatik tanır</li>
 *   <li>type: INTERNAL -> eklentinin kendi bakiye sistemi (ek para birimi)</li>
 * </ul>
 */
public final class CurrencyManager {

    private final SimpleShopPlugin plugin;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();
    private String defaultId = "vault";

    /** data/balances.yml içeriği; kapalı para birimlerinin verisi de korunsun diye saklanır. */
    private YamlConfiguration raw = new YamlConfiguration();
    private long snapshotVersion;
    private long writtenVersion;

    public CurrencyManager(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        saveNow();
        currencies.clear();

        Locale locale = Locale.forLanguageTag(plugin.getConfig().getString("locale", "tr-TR"));
        defaultId = plugin.getConfig().getString("default-currency", "vault").toLowerCase(Locale.ROOT);

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("currencies");
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                ConfigurationSection cfg = section.getConfigurationSection(rawId);
                if (cfg == null || !cfg.getBoolean("enabled", true)) {
                    continue;
                }
                String id = rawId.toLowerCase(Locale.ROOT);
                String type = cfg.getString("type", "INTERNAL").toUpperCase(Locale.ROOT);
                switch (type) {
                    case "VAULT" -> {
                        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                            currencies.put(id, new VaultCurrency(id, cfg, locale));
                        } else {
                            currencies.put(id, new MissingCurrency(id, cfg, locale));
                        }
                    }
                    case "INTERNAL" -> currencies.put(id, new InternalCurrency(id, cfg, locale));
                    default -> plugin.getLogger().warning(
                            "config.yml: '" + rawId + "' için bilinmeyen type '" + type + "' (VAULT veya INTERNAL olmalı).");
                }
            }
        }

        if (!currencies.containsKey(defaultId)) {
            plugin.getLogger().warning("default-currency '" + defaultId
                    + "' tanımlı/aktif değil! Para birimi belirtilmeyen eşyalar satın alınamaz.");
        }
        loadData();
    }

    // --- Sorgular ---

    public Currency get(String id) {
        return id == null ? null : currencies.get(id.toLowerCase(Locale.ROOT));
    }

    /** id boşsa varsayılan para birimini döndürür. Bulunamazsa null. */
    public Currency resolve(String id) {
        if (id == null || id.isBlank()) {
            return currencies.get(defaultId);
        }
        return currencies.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Currency> all() {
        return currencies.values();
    }

    public List<String> ids() {
        return new ArrayList<>(currencies.keySet());
    }

    public String defaultId() {
        return defaultId;
    }

    /** Para birimi bilinmiyorsa düz sayı yazar. */
    public String format(String id, double amount) {
        Currency currency = resolve(id);
        return currency == null ? Text.number(amount) : currency.format(amount);
    }

    public void logStatus() {
        for (Currency currency : currencies.values()) {
            String tag = currency.id().equals(defaultId) ? " (varsayılan)" : "";
            plugin.getLogger().info("Para birimi '" + currency.id() + "'" + tag + ": " + currency.statusLine());
        }
    }

    // --- Kalıcılık ---

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data/balances.yml");
    }

    private void loadData() {
        File file = dataFile();
        raw = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        for (Currency currency : currencies.values()) {
            if (!(currency instanceof InternalCurrency internal)) {
                continue;
            }
            internal.clear();
            ConfigurationSection s = raw.getConfigurationSection(internal.id());
            if (s == null) {
                continue;
            }
            for (String key : s.getKeys(false)) {
                try {
                    internal.loadBalance(UUID.fromString(key), s.getDouble(key));
                } catch (IllegalArgumentException ignored) {
                    // geçersiz UUID anahtarı
                }
            }
        }
    }

    private boolean anyDirty() {
        for (Currency currency : currencies.values()) {
            if (currency instanceof InternalCurrency internal && internal.isDirty()) {
                return true;
            }
        }
        return false;
    }

    /** Ana thread'de anlık görüntü alır. */
    private String serialize() {
        for (Currency currency : currencies.values()) {
            if (!(currency instanceof InternalCurrency internal)) {
                continue;
            }
            raw.set(internal.id(), null);
            for (Map.Entry<UUID, Double> entry : internal.snapshot().entrySet()) {
                raw.set(internal.id() + "." + entry.getKey(), entry.getValue());
            }
            internal.markClean();
        }
        snapshotVersion++;
        return raw.saveToString();
    }

    /** Değişiklik varsa diske yazar (ana thread'de çağır, yazma asenkron). */
    public void saveAsync() {
        if (!anyDirty()) {
            return;
        }
        final String data = serialize();
        final long version = snapshotVersion;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(version, data));
    }

    /** Değişiklik varsa hemen diske yazar (kapanış / yeniden yükleme). */
    public void saveNow() {
        if (!anyDirty()) {
            return;
        }
        write(snapshotVersion + 1, serialize());
    }

    private synchronized void write(long version, String data) {
        if (version < writtenVersion) {
            return; // daha yeni bir görüntü zaten yazıldı
        }
        try {
            File file = dataFile();
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            writtenVersion = version;
        } catch (IOException e) {
            plugin.getLogger().severe("balances.yml yazılamadı: " + e.getMessage());
        }
    }
}
