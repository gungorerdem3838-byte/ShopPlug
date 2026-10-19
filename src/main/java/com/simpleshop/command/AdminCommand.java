package com.simpleshop.command;

import com.simpleshop.SimpleShopPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /simpleshop reload | info */
public final class AdminCommand implements TabExecutor {

    private final SimpleShopPlugin plugin;

    public AdminCommand(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simpleshop.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload", "yenile" -> {
                plugin.reloadAll();
                plugin.messages().send(sender, "reloaded",
                        "categories", plugin.shop().categories().size(),
                        "items", plugin.shop().totalItems(),
                        "currencies", plugin.currencies().all().size());
            }
            default -> plugin.messages().send(sender, "info",
                    "version", plugin.getPluginMeta().getVersion(),
                    "categories", plugin.shop().categories().size(),
                    "items", plugin.shop().totalItems(),
                    "currencies", plugin.currencies().all().size());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("simpleshop.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("reload", "info")) {
                if (option.startsWith(prefix)) {
                    result.add(option);
                }
            }
        }
        return result;
    }
}
