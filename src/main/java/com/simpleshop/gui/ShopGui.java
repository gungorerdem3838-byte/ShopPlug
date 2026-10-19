package com.simpleshop.gui;

import com.simpleshop.SimpleShopPlugin;
import com.simpleshop.currency.Currency;
import com.simpleshop.shop.ShopManager.Category;
import com.simpleshop.shop.ShopManager.ShopItem;
import com.simpleshop.util.Messages;
import com.simpleshop.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ana menü ve kategori menüleri. */
public final class ShopGui implements Listener {

    private static final int PER_PAGE = 45;

    private final SimpleShopPlugin plugin;

    public ShopGui(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Tutucu
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface SlotAction {
        void run(Player player, ClickType click);
    }

    /** Market envanterlerini tanımak için kullanılan tutucu. */
    public static final class ShopHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, SlotAction> actions = new HashMap<>();
        private int balanceSlot = -1;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record Entry(Category sub, ShopItem item) {
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------
    // Menüleri aç
    // ------------------------------------------------------------------

    public void openMain(Player player) {
        int rows = Math.max(3, Math.min(6, plugin.getConfig().getInt("gui.main-rows", 5)));
        ShopHolder holder = new ShopHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, msg().get("gui.main-title"));
        holder.inventory = inventory;

        for (Category category : plugin.shop().categories()) {
            if (!canAccess(player, category)) {
                continue;
            }
            int slot = category.slot();
            if (slot < 0 || slot >= inventory.getSize() || inventory.getItem(slot) != null) {
                slot = firstFree(inventory);
            }
            if (slot < 0) {
                break;
            }
            inventory.setItem(slot, categoryIcon(category));
            holder.actions.put(slot, (p, click) -> openCategory(p, category, 0));
        }

        placeBalance(holder, player, inventory.getSize() - 5);
        fillEmpty(inventory);
        player.openInventory(inventory);
    }

    public void openCategory(Player player, Category category, int requestedPage) {
        List<Entry> entries = new ArrayList<>();
        for (Category sub : category.subcategories()) {
            if (canAccess(player, sub)) {
                entries.add(new Entry(sub, null));
            }
        }
        for (ShopItem item : category.items()) {
            entries.add(new Entry(null, item));
        }

        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) PER_PAGE));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        ShopHolder holder = new ShopHolder();
        Component title = msg().get("gui.category-title",
                "name", category.name(), "page", page + 1, "pages", pages);
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inventory;

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            if (entry.sub() != null) {
                Category sub = entry.sub();
                inventory.setItem(i, categoryIcon(sub));
                holder.actions.put(i, (p, click) -> openCategory(p, sub, 0));
            } else {
                ShopItem item = entry.item();
                inventory.setItem(i, itemIcon(player, category, item));
                holder.actions.put(i, (p, click) -> handleItem(p, holder, category, item, click));
            }
        }

        // Alt sıra: geri (45), önceki (48), bakiye (49), sonraki (50), kapat (53)
        inventory.setItem(45, simple(Material.ARROW, msg().raw("gui.back")));
        holder.actions.put(45, (p, click) -> {
            if (category.parent() != null) {
                openCategory(p, category.parent(), 0);
            } else {
                openMain(p);
            }
        });

        if (page > 0) {
            inventory.setItem(48, simple(Material.ARROW, msg().raw("gui.previous"),
                    msg().raw("gui.page-info", "page", page + 1, "pages", pages)));
            final int target = page - 1;
            holder.actions.put(48, (p, click) -> openCategory(p, category, target));
        }
        if (page < pages - 1) {
            inventory.setItem(50, simple(Material.ARROW, msg().raw("gui.next"),
                    msg().raw("gui.page-info", "page", page + 1, "pages", pages)));
            final int target = page + 1;
            holder.actions.put(50, (p, click) -> openCategory(p, category, target));
        }

        placeBalance(holder, player, 49);

        inventory.setItem(53, simple(Material.BARRIER, msg().raw("gui.close")));
        holder.actions.put(53, (p, click) -> p.closeInventory());

        fillEmpty(inventory);
        player.openInventory(inventory);
    }

    /** Açık market menülerini kapatır (yeniden yükleme / kapanış). */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof ShopHolder) {
                player.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------------
    // Tıklama
    // ------------------------------------------------------------------

    private void handleItem(Player player, ShopHolder holder, Category category, ShopItem item, ClickType click) {
        if (item.isCommand()) {
            if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
                plugin.trade().runCommandItem(player, category, item);
                refreshBalance(holder, player);
            }
            return;
        }
        switch (click) {
            case LEFT -> plugin.trade().buy(player, category, item, 1);
            case SHIFT_LEFT -> plugin.trade().buy(player, category, item, item.material().getMaxStackSize());
            case RIGHT -> plugin.trade().sell(player, category, item, 1);
            case SHIFT_RIGHT -> plugin.trade().sell(player, category, item, -1);
            default -> {
                return;
            }
        }
        refreshBalance(holder, player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder(false) instanceof ShopHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        SlotAction action = holder.actions.get(event.getSlot());
        if (action != null) {
            action.run(player, event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Eşya görünümleri
    // ------------------------------------------------------------------

    private ItemStack categoryIcon(Category category) {
        ItemStack stack = new ItemStack(category.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Text.item(category.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : category.description()) {
            lore.add(Text.item(line));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack itemIcon(Player player, Category category, ShopItem item) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String name = item.name() != null && !item.name().isBlank()
                ? item.name()
                : "&f" + Text.pretty(item.material());
        meta.displayName(Text.item(name));

        List<Component> lore = new ArrayList<>();
        for (String line : item.lore()) {
            lore.add(Text.item(line));
        }

        boolean priced = item.buyPrice() > 0 || item.canSell();
        if (priced && !lore.isEmpty()) {
            lore.add(Component.empty());
        }

        String currencyId = item.currency();
        if (item.isCommand()) {
            if (item.buyPrice() > 0) {
                lore.add(Text.item(msg().raw("gui.command-price",
                        "price", plugin.currencies().format(currencyId, item.buyPrice()))));
            }
        } else {
            if (item.canBuy()) {
                lore.add(Text.item(msg().raw("gui.buy-price",
                        "price", plugin.currencies().format(currencyId, item.buyPrice()))));
            }
            if (item.canSell()) {
                lore.add(Text.item(msg().raw("gui.sell-price",
                        "price", plugin.currencies().format(currencyId, item.sellPrice()))));
            }
            lore.add(Component.empty());
            if (item.canBuy()) {
                lore.add(Text.item(msg().raw("gui.hint-buy")));
                int stack1 = item.material().getMaxStackSize();
                if (stack1 > 1) {
                    lore.add(Text.item(msg().raw("gui.hint-buy-stack", "amount", stack1)));
                }
            }
            if (item.canSell()) {
                lore.add(Text.item(msg().raw("gui.hint-sell")));
                lore.add(Text.item(msg().raw("gui.hint-sell-all")));
            }
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack simple(Material material, String name, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Text.item(name));
        if (loreLines.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Text.item(line));
            }
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------
    // Bakiye göstergesi ve dolgu
    // ------------------------------------------------------------------

    private void placeBalance(ShopHolder holder, Player player, int slot) {
        if (!plugin.getConfig().getBoolean("gui.show-balance", true)) {
            return;
        }
        Inventory inventory = holder.inventory;
        if (slot < 0 || slot >= inventory.getSize() || inventory.getItem(slot) != null) {
            return;
        }
        holder.balanceSlot = slot;
        inventory.setItem(slot, balanceItem(player));
    }

    private void refreshBalance(ShopHolder holder, Player player) {
        if (holder.balanceSlot >= 0 && holder.inventory != null) {
            holder.inventory.setItem(holder.balanceSlot, balanceItem(player));
        }
    }

    private ItemStack balanceItem(Player player) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("gui.balance-material", "GOLD_INGOT"));
        if (material == null || material.isAir() || !material.isItem()) {
            material = Material.GOLD_INGOT;
        }
        List<String> lines = new ArrayList<>();
        for (Currency currency : plugin.currencies().all()) {
            if (!currency.isAvailable()) {
                continue;
            }
            lines.add(msg().raw("gui.balance-line",
                    "currency", currency.name(),
                    "balance", currency.format(currency.balance(player))));
        }
        return simple(material, msg().raw("gui.balance-title"), lines.toArray(new String[0]));
    }

    private void fillEmpty(Inventory inventory) {
        String name = plugin.getConfig().getString("gui.filler-material", "GRAY_STAINED_GLASS_PANE");
        if (name == null || name.isBlank() || name.equalsIgnoreCase("NONE") || name.equalsIgnoreCase("AIR")) {
            return;
        }
        Material material = Material.matchMaterial(name);
        if (material == null || material.isAir() || !material.isItem()) {
            return;
        }
        ItemStack filler = simple(material, "&r");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private int firstFree(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private boolean canAccess(Player player, Category category) {
        String permission = category.permission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }
}
