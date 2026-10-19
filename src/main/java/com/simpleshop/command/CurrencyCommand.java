package com.simpleshop.command;

import com.simpleshop.SimpleShopPlugin;
import com.simpleshop.currency.Currency;
import com.simpleshop.currency.InternalCurrency;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /currency [balance|list|pay|give|take|set]
 * Hem Vault hem ek (INTERNAL) para birimleri için çalışır.
 */
public final class CurrencyCommand implements TabExecutor {

    private static final String ADMIN = "simpleshop.currency.admin";

    private final SimpleShopPlugin plugin;

    public CurrencyCommand(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                showBalances(sender, player, true);
            } else {
                plugin.messages().send(sender, "currency.usage", "label", label);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "balance", "bal", "bakiye" -> balance(sender, args);
            case "list", "liste" -> list(sender);
            case "pay", "gonder" -> pay(sender, args);
            case "give", "take", "set" -> admin(sender, args);
            default -> plugin.messages().send(sender, "currency.usage", "label", label);
        }
        return true;
    }

    // ------------------------------------------------------------------

    private void balance(CommandSender sender, String[] args) {
        Currency currency = null;
        if (args.length >= 2) {
            currency = plugin.currencies().get(args[1]);
            if (currency == null) {
                plugin.messages().send(sender, "unknown-currency", "currency", args[1]);
                return;
            }
        }

        OfflinePlayer target;
        boolean self;
        if (args.length >= 3) {
            if (!sender.hasPermission(ADMIN)) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            target = findPlayer(args[2]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", "player", args[2]);
                return;
            }
            self = false;
        } else if (sender instanceof Player player) {
            target = player;
            self = true;
        } else {
            plugin.messages().send(sender, "player-only");
            return;
        }

        if (currency == null) {
            showBalances(sender, target, self);
        } else {
            plugin.messages().send(sender, "currency.balance-single",
                    "player", name(target),
                    "currency", currency.name(),
                    "balance", currency.format(currency.balance(target)));
        }
    }

    private void showBalances(CommandSender sender, OfflinePlayer target, boolean self) {
        if (self) {
            plugin.messages().sendPlain(sender, "currency.balance-header");
        } else {
            plugin.messages().sendPlain(sender, "currency.balance-header-other", "player", name(target));
        }
        for (Currency currency : plugin.currencies().all()) {
            if (!currency.isAvailable()) {
                continue;
            }
            plugin.messages().sendPlain(sender, "currency.balance-line",
                    "currency", currency.name(),
                    "balance", currency.format(currency.balance(target)));
        }
    }

    private void list(CommandSender sender) {
        plugin.messages().sendPlain(sender, "currency.list-header");
        for (Currency currency : plugin.currencies().all()) {
            plugin.messages().sendPlain(sender, "currency.list-line",
                    "id", currency.id(),
                    "name", currency.name(),
                    "status", currency.isAvailable() ? "aktif" : "kullanılamıyor",
                    "default", currency.id().equals(plugin.currencies().defaultId()) ? " &e(varsayılan)" : "");
        }
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player payer)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!payer.hasPermission("simpleshop.currency.pay")) {
            plugin.messages().send(payer, "no-permission");
            return;
        }
        if (args.length < 4) {
            plugin.messages().send(payer, "currency.usage-pay");
            return;
        }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            plugin.messages().send(payer, "player-not-found", "player", args[1]);
            return;
        }
        if (target.getUniqueId().equals(payer.getUniqueId())) {
            plugin.messages().send(payer, "currency.pay-self");
            return;
        }
        Currency currency = plugin.currencies().get(args[2]);
        if (currency == null) {
            plugin.messages().send(payer, "unknown-currency", "currency", args[2]);
            return;
        }
        if (!currency.isAvailable()) {
            plugin.messages().send(payer, "currency-unavailable", "currency", currency.id());
            return;
        }
        Double parsed = parseAmount(args[3]);
        if (parsed == null || parsed <= 0) {
            plugin.messages().send(payer, "invalid-amount", "amount", args[3]);
            return;
        }
        double amount = currency.round(parsed);
        if (amount <= 0) {
            plugin.messages().send(payer, "invalid-amount", "amount", args[3]);
            return;
        }
        if (!currency.has(payer, amount)) {
            plugin.messages().send(payer, "currency.not-enough",
                    "currency", currency.name(),
                    "balance", currency.format(currency.balance(payer)));
            return;
        }
        if (!currency.withdraw(payer, amount)) {
            plugin.messages().send(payer, "transaction-failed");
            return;
        }
        if (!currency.deposit(target, amount)) {
            currency.deposit(payer, amount); // iade
            plugin.messages().send(payer, "transaction-failed");
            return;
        }
        plugin.messages().send(payer, "currency.pay-sent",
                "player", name(target), "amount", currency.format(amount));
        Player online = target.getPlayer();
        if (online != null) {
            plugin.messages().send(online, "currency.pay-received",
                    "player", payer.getName(), "amount", currency.format(amount));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 4) {
            plugin.messages().send(sender, "currency.usage-admin", "action", action);
            return;
        }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[1]);
            return;
        }
        Currency currency = plugin.currencies().get(args[2]);
        if (currency == null) {
            plugin.messages().send(sender, "unknown-currency", "currency", args[2]);
            return;
        }
        if (!currency.isAvailable()) {
            plugin.messages().send(sender, "currency-unavailable", "currency", currency.id());
            return;
        }
        Double parsed = parseAmount(args[3]);
        if (parsed == null || parsed < 0 || (parsed == 0 && !action.equals("set"))) {
            plugin.messages().send(sender, "invalid-amount", "amount", args[3]);
            return;
        }
        double amount = currency.round(parsed);

        switch (action) {
            case "give" -> {
                if (!currency.deposit(target, amount)) {
                    plugin.messages().send(sender, "transaction-failed");
                    return;
                }
                plugin.messages().send(sender, "currency.give-success",
                        "player", name(target), "amount", currency.format(amount));
            }
            case "take" -> {
                if (!currency.withdraw(target, amount)) {
                    plugin.messages().send(sender, "currency.take-failed",
                            "player", name(target),
                            "balance", currency.format(currency.balance(target)));
                    return;
                }
                plugin.messages().send(sender, "currency.take-success",
                        "player", name(target), "amount", currency.format(amount));
            }
            default -> {
                if (!(currency instanceof InternalCurrency internal)) {
                    plugin.messages().send(sender, "currency.set-unsupported", "currency", currency.name());
                    return;
                }
                internal.set(target, amount);
                plugin.messages().send(sender, "currency.set-success",
                        "player", name(target), "amount", currency.format(amount));
            }
        }
    }

    // ------------------------------------------------------------------

    private static Double parseAmount(String raw) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.'));
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private static String name(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    // ------------------------------------------------------------------
    // Tab tamamlama
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("balance", "list"));
            if (sender.hasPermission("simpleshop.currency.pay")) {
                options.add("pay");
            }
            if (admin) {
                options.addAll(List.of("give", "take", "set"));
            }
            return filter(options, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "balance", "bal", "bakiye" -> filter(plugin.currencies().ids(), args[1]);
                case "pay", "give", "take", "set" -> filter(onlineNames(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "balance", "bal", "bakiye" -> admin ? filter(onlineNames(), args[2]) : List.of();
                case "pay", "give", "take", "set" -> filter(plugin.currencies().ids(), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
