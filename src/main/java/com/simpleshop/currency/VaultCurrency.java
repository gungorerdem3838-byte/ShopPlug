package com.simpleshop.currency;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;

/**
 * Vault üzerinden çalışan para birimi. Ekonomi sağlayıcısı (EssentialsX, CMI vb.)
 * her çağrıda ServicesManager'dan çözülür; böylece eklenti yükleme sırası önemli değildir.
 */
public final class VaultCurrency extends AbstractCurrency {

    private final boolean useEconomyFormat;

    public VaultCurrency(String id, ConfigurationSection cfg, Locale locale) {
        super(id, cfg, locale);
        this.useEconomyFormat = cfg.getBoolean("use-economy-format", false);
    }

    private RegisteredServiceProvider<Economy> registration() {
        return Bukkit.getServicesManager().getRegistration(Economy.class);
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> rsp = registration();
        return rsp == null ? null : rsp.getProvider();
    }

    @Override
    public boolean isAvailable() {
        return economy() != null;
    }

    @Override
    public double balance(OfflinePlayer player) {
        Economy eco = economy();
        return eco == null ? 0 : eco.getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Economy eco = economy();
        return eco != null && validAmount(amount) && eco.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null || !validAmount(amount)) {
            return false;
        }
        EconomyResponse response = eco.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null || !validAmount(amount)) {
            return false;
        }
        EconomyResponse response = eco.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public String format(double amount) {
        if (useEconomyFormat) {
            Economy eco = economy();
            if (eco != null) {
                return eco.format(amount);
            }
        }
        return super.format(amount);
    }

    @Override
    public String statusLine() {
        RegisteredServiceProvider<Economy> rsp = registration();
        if (rsp == null) {
            return "Vault var ama ekonomi eklentisi (EssentialsX vb.) yok";
        }
        return "Vault ekonomisi: " + rsp.getPlugin().getName();
    }
}
