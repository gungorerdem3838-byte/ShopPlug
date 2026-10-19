package com.simpleshop.shop;

import com.simpleshop.SimpleShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** shop.yml okuyucu: kategoriler, alt kategoriler ve eşyalar. */
public final class ShopManager {

    /** Eski / yanlış yazılmış materyal adları için takma adlar. */
    private static final Map<String, String> ALIASES = Map.of(
            "NAMETAG", "NAME_TAG",
            "WOOL", "WHITE_WOOL",
            "GRASS", "SHORT_GRASS"
    );

    /** Tek bir market eşyası. */
    public record ShopItem(String key,
                           Material material,
                           String name,
                           List<String> lore,
                           double buyPrice,
                           double sellPrice,
                           String currency,
                           List<String> commands,
                           String runAs,
                           String permission,
                           boolean closeOnClick) {

        /** Tıklayınca komut çalıştıran eşya mı? (ör. spawner satın alma) */
        public boolean isCommand() {
            return !commands.isEmpty();
        }

        public boolean canBuy() {
            return !isCommand() && buyPrice > 0;
        }

        public boolean canSell() {
            return !isCommand() && sellPrice > 0;
        }
    }

    /** Kategori veya alt kategori. */
    public static final class Category {
        private final String key;
        private final String name;
        private final List<String> description;
        private final Material icon;
        private final int slot;
        private final String permission;
        private final String currency;
        private final Category parent;
        private final List<ShopItem> items = new ArrayList<>();
        private final List<Category> subcategories = new ArrayList<>();

        Category(String key, String name, List<String> description, Material icon, int slot,
                 String permission, String currency, Category parent) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.icon = icon;
            this.slot = slot;
            this.permission = permission;
            this.currency = currency;
            this.parent = parent;
        }

        public String key() {
            return key;
        }

        public String name() {
            return name;
        }

        public List<String> description() {
            return description;
        }

        public Material icon() {
            return icon;
        }

        public int slot() {
            return slot;
        }

        public String permission() {
            return permission;
        }

        public String currency() {
            return currency;
        }

        public Category parent() {
            return parent;
        }

        public List<ShopItem> items() {
            return items;
        }

        public List<Category> subcategories() {
            return subcategories;
        }
    }

    private final SimpleShopPlugin plugin;
    private final List<Category> categories = new ArrayList<>();
    private final Map<Material, ShopItem> sellIndex = new EnumMap<>(Material.class);
    private int totalItems;

    public ShopManager(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        categories.clear();
        sellIndex.clear();
        totalItems = 0;

        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) {
            plugin.saveResource("shop.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection root = yaml.getConfigurationSection("categories");
        if (root == null) {
            plugin.getLogger().warning("shop.yml: 'categories' bölümü bulunamadı.");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            categories.add(parseCategory(key, section, null, "categories." + key));
        }
        for (Category category : categories) {
            index(category);
        }
        plugin.getLogger().info("shop.yml yüklendi: " + categories.size() + " kategori, " + totalItems + " eşya.");
    }

    // --- Okuma ---

    private Category parseCategory(String key, ConfigurationSection sec, Category parent, String path) {
        String name = sec.getString("name", "&f" + key);
        List<String> description = new ArrayList<>(sec.getStringList("description"));

        Material icon = parseMaterial(sec.getString("icon", "CHEST"));
        if (icon == null) {
            plugin.getLogger().warning("shop.yml: bilinmeyen kategori ikonu '" + sec.getString("icon") + "' (" + path + ") - CHEST kullanıldı.");
            icon = Material.CHEST;
        }

        String currency = blankToNull(sec.getString("currency"));
        currency = currency != null ? currency.toLowerCase(Locale.ROOT) : (parent != null ? parent.currency() : null);

        Category category = new Category(key, name, description, icon,
                sec.getInt("slot", -1), sec.getString("permission", ""), currency, parent);

        ConfigurationSection items = sec.getConfigurationSection("items");
        if (items != null) {
            for (String itemKey : sortedKeys(items)) {
                ConfigurationSection itemSec = items.getConfigurationSection(itemKey);
                if (itemSec == null) {
                    continue;
                }
                ShopItem item = parseItem(itemKey, itemSec, category, path + ".items." + itemKey);
                if (item != null) {
                    category.items().add(item);
                }
            }
        }

        ConfigurationSection subs = sec.getConfigurationSection("subcategories");
        if (subs != null) {
            for (String subKey : subs.getKeys(false)) {
                ConfigurationSection subSec = subs.getConfigurationSection(subKey);
                if (subSec != null) {
                    category.subcategories().add(parseCategory(subKey, subSec, category, path + ".subcategories." + subKey));
                }
            }
            category.subcategories().sort(Comparator.comparingInt(Category::slot));
        }
        return category;
    }

    private ShopItem parseItem(String key, ConfigurationSection sec, Category category, String path) {
        Material material = parseMaterial(sec.getString("material"));
        if (material == null) {
            plugin.getLogger().warning("shop.yml: bilinmeyen materyal '" + sec.getString("material") + "' (" + path + ") - atlandı.");
            return null;
        }

        List<String> commands = new ArrayList<>();
        if (sec.isList("commands")) {
            commands.addAll(sec.getStringList("commands"));
        }
        String single = sec.getString("command");
        if (single != null && !single.isBlank()) {
            commands.add(single);
        }

        String currency = blankToNull(sec.getString("currency"));
        currency = currency != null ? currency.toLowerCase(Locale.ROOT) : category.currency();

        return new ShopItem(
                key,
                material,
                sec.getString("name"),
                new ArrayList<>(sec.getStringList("lore")),
                sec.getDouble("buy-price", -1),
                sec.getDouble("sell-price", -1),
                currency,
                commands,
                blankToNull(sec.getString("run-as")),
                sec.getString("permission", ""),
                sec.getBoolean("close-on-click", false));
    }

    private void index(Category category) {
        for (ShopItem item : category.items()) {
            totalItems++;
            if (item.canSell()) {
                sellIndex.putIfAbsent(item.material(), item);
            }
        }
        for (Category sub : category.subcategories()) {
            index(sub);
        }
    }

    // --- Yardımcılar ---

    static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        name = ALIASES.getOrDefault(name, name);
        Material material = Material.matchMaterial(name);
        if (material == null || material.isAir() || !material.isItem()) {
            return null;
        }
        return material;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> sortedKeys(ConfigurationSection section) {
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((a, b) -> {
            Integer ia = tryInt(a);
            Integer ib = tryInt(b);
            if (ia != null && ib != null) {
                return Integer.compare(ia, ib);
            }
            if (ia != null) {
                return -1;
            }
            if (ib != null) {
                return 1;
            }
            return 0;
        });
        return keys;
    }

    private static Integer tryInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Sorgular ---

    public List<Category> categories() {
        return categories;
    }

    public int totalItems() {
        return totalItems;
    }

    /** Üst seviye kategoriyi anahtara göre bulur (büyük/küçük harf duyarsız). */
    public Category findCategory(String key) {
        for (Category category : categories) {
            if (category.key().equalsIgnoreCase(key)) {
                return category;
            }
        }
        return null;
    }

    /** Bu materyal için satış fiyatı tanımlı ilk eşya (yoksa null). */
    public ShopItem findSellable(Material material) {
        return sellIndex.get(material);
    }

    /** Hızlı tamamlama için kategori anahtarları. */
    public List<String> categoryKeys() {
        List<String> keys = new ArrayList<>();
        for (Category category : categories) {
            keys.add(category.key());
        }
        return keys;
    }
}
