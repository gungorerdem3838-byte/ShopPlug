package com.simpleshop.command;

import com.simpleshop.SimpleShopPlugin;
import com.simpleshop.shop.ShopManager.Category;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /shop [kategori] */
public final class ShopCommand implements TabExecutor {

    private final SimpleShopPlugin plugin;

    public ShopCommand(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (plugin.shop().categories().isEmpty()) {
            plugin.messages().send(player, "no-categories");
            return true;
        }
        if (args.length == 0) {
            plugin.gui().openMain(player);
            return true;
        }

        Category category = plugin.shop().findCategory(args[0]);
        if (category == null) {
            plugin.messages().send(player, "unknown-category", "category", args[0]);
            return true;
        }
        String permission = category.permission();
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        plugin.gui().openCategory(player, category, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String key : plugin.shop().categoryKeys()) {
                if (key.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(key);
                }
            }
        }
        return result;
    }
}
