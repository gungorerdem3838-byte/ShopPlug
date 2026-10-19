package com.simpleshop.command;

import com.simpleshop.SimpleShopPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /sell hand | /sell all */
public final class SellCommand implements TabExecutor {

    private final SimpleShopPlugin plugin;

    public SellCommand(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(player, "sell.usage", "label", label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "hand", "el" -> plugin.trade().sellHand(player);
            case "all", "hepsi" -> plugin.trade().sellAll(player);
            default -> plugin.messages().send(player, "sell.usage", "label", label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("hand", "all")) {
                if (option.startsWith(prefix)) {
                    result.add(option);
                }
            }
        }
        return result;
    }
}
