package com.simpleshop.shop;

import com.simpleshop.SimpleShopPlugin;
import com.simpleshop.currency.Currency;
import com.simpleshop.shop.ShopManager.Category;
import com.simpleshop.shop.ShopManager.ShopItem;
import com.simpleshop.util.Messages;
import com.simpleshop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Satın alma, satma ve komut eşyaları. Tüm para işlemleri burada yapılır. */
public final class TradeService {

    private final SimpleShopPlugin plugin;

    public TradeService(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------
    // Satın alma
    // ------------------------------------------------------------------

    public void buy(Player player, Category category, ShopItem item, int amount) {
        if (!item.canBuy()) {
            msg().send(player, "buy.not-buyable");
            plugin.sound(player, false);
            return;
        }
        if (!hasPermission(player, item)) {
            msg().send(player, "no-permission");
            plugin.sound(player, false);
            return;
        }
        Currency currency = requireCurrency(player, item.currency());
        if (currency == null) {
            return;
        }

        Material material = item.material();
        int wanted = Math.max(1, amount);
        int space = freeSpace(player.getInventory(), material);
        if (space <= 0) {
            msg().send(player, "buy.inventory-full");
            plugin.sound(player, false);
            return;
        }
        wanted = Math.min(wanted, space);

        double total = currency.round(item.buyPrice() * wanted);
        if (!currency.has(player, total)) {
            msg().send(player, "buy.not-enough-money",
                    "price", currency.format(total),
                    "balance", currency.format(currency.balance(player)));
            plugin.sound(player, false);
            return;
        }
        if (!currency.withdraw(player, total)) {
            msg().send(player, "transaction-failed");
            plugin.sound(player, false);
            return;
        }

        giveItems(player, material, wanted);
        msg().send(player, "buy.success",
                "amount", wanted,
                "item", displayName(item),
                "price", currency.format(total));
        log(player.getName() + " satın aldı: " + wanted + "x " + material + " (-" + total + " " + currency.id() + ")");
        plugin.sound(player, true);
    }

    // ------------------------------------------------------------------
    // Satma
    // ------------------------------------------------------------------

    /** amount &lt; 0 ise envanterdeki hepsini satar. */
    public void sell(Player player, Category category, ShopItem item, int amount) {
        if (!item.canSell()) {
            msg().send(player, "sell.not-sellable");
            plugin.sound(player, false);
            return;
        }
        Currency currency = requireCurrency(player, item.currency());
        if (currency == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        Material material = item.material();
        int have = countPlain(inventory, material);
        if (have <= 0) {
            msg().send(player, "sell.no-items", "item", displayName(item));
            plugin.sound(player, false);
            return;
        }
        int wanted = amount < 0 ? have : Math.min(amount, have);
        int removed = removePlain(inventory, material, wanted);
        if (removed <= 0) {
            msg().send(player, "sell.no-items", "item", displayName(item));
            return;
        }

        double total = currency.round(item.sellPrice() * removed);
        if (!currency.deposit(player, total)) {
            giveItems(player, material, removed);
            msg().send(player, "transaction-failed");
            plugin.sound(player, false);
            return;
        }

        msg().send(player, "sell.success",
                "amount", removed,
                "item", displayName(item),
                "price", currency.format(total));
        log(player.getName() + " sattı: " + removed + "x " + material + " (+" + total + " " + currency.id() + ")");
        plugin.sound(player, true);
    }

    /** /sell hand: elindeki eşya yığınını satar. */
    public void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            msg().send(player, "sell.nothing-in-hand");
            return;
        }
        ShopItem item = plugin.shop().findSellable(hand.getType());
        if (item == null) {
            msg().send(player, "sell.not-sellable");
            return;
        }
        if (!isPlain(hand)) {
            msg().send(player, "sell.not-plain");
            return;
        }
        Currency currency = requireCurrency(player, item.currency());
        if (currency == null) {
            return;
        }

        int amount = hand.getAmount();
        double total = currency.round(item.sellPrice() * amount);
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        if (!currency.deposit(player, total)) {
            player.getInventory().setItemInMainHand(hand);
            msg().send(player, "transaction-failed");
            return;
        }
        msg().send(player, "sell.success",
                "amount", amount,
                "item", displayName(item),
                "price", currency.format(total));
        log(player.getName() + " sattı (el): " + amount + "x " + hand.getType() + " (+" + total + " " + currency.id() + ")");
        plugin.sound(player, true);
    }

    /** /sell all: envanterdeki satılabilir her şeyi satar. */
    public void sellAll(Player player) {
        PlayerInventory inventory = player.getInventory();

        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (plugin.shop().findSellable(stack.getType()) == null || !isPlain(stack)) {
                continue;
            }
            counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        if (counts.isEmpty()) {
            msg().send(player, "sell.nothing-sellable");
            return;
        }

        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, Currency> used = new LinkedHashMap<>();
        int soldItems = 0;

        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            ShopItem item = plugin.shop().findSellable(entry.getKey());
            if (item == null) {
                continue;
            }
            Currency currency = plugin.currencies().resolve(item.currency());
            if (currency == null || !currency.isAvailable()) {
                continue;
            }
            int removed = removePlain(inventory, entry.getKey(), entry.getValue());
            if (removed <= 0) {
                continue;
            }
            double total = currency.round(item.sellPrice() * removed);
            if (!currency.deposit(player, total)) {
                giveItems(player, entry.getKey(), removed);
                continue;
            }
            totals.merge(currency.id(), total, Double::sum);
            used.putIfAbsent(currency.id(), currency);
            soldItems += removed;
        }

        if (soldItems == 0) {
            msg().send(player, "sell.nothing-sellable");
            plugin.sound(player, false);
            return;
        }

        String earned = totals.entrySet().stream()
                .map(e -> used.get(e.getKey()).format(e.getValue()))
                .collect(Collectors.joining("&7, &f"));
        msg().send(player, "sell.all-success", "amount", soldItems, "total", earned);
        log(player.getName() + " hepsini sattı: " + soldItems + " eşya (" + totals + ")");
        plugin.sound(player, true);
    }

    // ------------------------------------------------------------------
    // Komut eşyaları (ör. spawner satın alma)
    // ------------------------------------------------------------------

    public void runCommandItem(Player player, Category category, ShopItem item) {
        if (!hasPermission(player, item)) {
            msg().send(player, "no-permission");
            plugin.sound(player, false);
            return;
        }

        Currency currency = null;
        double price = 0;
        if (item.buyPrice() > 0) {
            currency = requireCurrency(player, item.currency());
            if (currency == null) {
                return;
            }
            price = currency.round(item.buyPrice());
            if (!currency.has(player, price)) {
                msg().send(player, "buy.not-enough-money",
                        "price", currency.format(price),
                        "balance", currency.format(currency.balance(player)));
                plugin.sound(player, false);
                return;
            }
            if (!currency.withdraw(player, price)) {
                msg().send(player, "transaction-failed");
                plugin.sound(player, false);
                return;
            }
        }

        String executor = item.runAs() != null
                ? item.runAs()
                : plugin.getConfig().getString("trade.command-executor", "console");
        boolean asPlayer = "player".equalsIgnoreCase(executor);

        boolean anySucceeded = false;
        for (String template : item.commands()) {
            String command = template
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            boolean ok = asPlayer
                    ? player.performCommand(command)
                    : Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            anySucceeded |= ok;
            if (!ok) {
                plugin.getLogger().warning("Komut çalıştırılamadı (" + (asPlayer ? "oyuncu" : "konsol") + "): /" + command);
            }
        }

        if (!anySucceeded) {
            if (currency != null) {
                currency.deposit(player, price);
            }
            msg().send(player, "command-failed");
            plugin.sound(player, false);
            return;
        }

        log(player.getName() + " komut eşyası kullandı: " + category.key() + "/" + item.key());
        plugin.sound(player, true);
        if (item.closeOnClick()) {
            player.closeInventory();
        }
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------

    private Currency requireCurrency(Player player, String id) {
        Currency currency = plugin.currencies().resolve(id);
        if (currency == null || !currency.isAvailable()) {
            String shown = (id == null || id.isBlank()) ? plugin.currencies().defaultId() : id;
            msg().send(player, "currency-unavailable", "currency", shown);
            plugin.sound(player, false);
            return null;
        }
        return currency;
    }

    private boolean hasPermission(Player player, ShopItem item) {
        return item.permission() == null || item.permission().isBlank() || player.hasPermission(item.permission());
    }

    static String displayName(ShopItem item) {
        if (item.name() != null && !item.name().isBlank()) {
            return Text.plain(item.name());
        }
        return Text.pretty(item.material());
    }

    private void log(String line) {
        if (plugin.getConfig().getBoolean("trade.log-transactions", false)) {
            plugin.getLogger().info("[Ticaret] " + line);
        }
    }

    /** Büyü, isim, hasar vb. olmayan düz eşya mı? Sadece bunlar satılır. */
    public static boolean isPlain(ItemStack stack) {
        return stack.isSimilar(new ItemStack(stack.getType()));
    }

    /** Envanterde bu materyalden kaç adet daha taşınabilir. */
    static int freeSpace(PlayerInventory inventory, Material material) {
        int max = material.getMaxStackSize();
        ItemStack plain = new ItemStack(material);
        int free = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                free += max;
            } else if (stack.getType() == material && stack.isSimilar(plain)) {
                free += Math.max(0, max - stack.getAmount());
            }
        }
        return free;
    }

    static int countPlain(PlayerInventory inventory, Material material) {
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material && isPlain(stack)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /** Düz eşyalardan en fazla amount adet siler; gerçekte silinen miktarı döndürür. */
    static int removePlain(PlayerInventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material || !isPlain(stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            if (take >= stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            remaining -= take;
        }
        inventory.setStorageContents(contents);
        return amount - remaining;
    }

    private void giveItems(Player player, Material material, int amount) {
        int max = material.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int n = Math.min(max, left);
            Map<Integer, ItemStack> rest = player.getInventory().addItem(new ItemStack(material, n));
            for (ItemStack extra : rest.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
            left -= n;
        }
    }
}
