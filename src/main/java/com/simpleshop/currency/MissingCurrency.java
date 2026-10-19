package com.simpleshop.currency;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Vault eklentisi sunucuda yokken kullanılan yer tutucu. Hiçbir işlem yapmaz. */
public final class MissingCurrency extends AbstractCurrency {

    public MissingCurrency(String id, ConfigurationSection cfg, Locale locale) {
        super(id, cfg, locale);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public double balance(OfflinePlayer player) {
        return 0;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public String statusLine() {
        return "Vault eklentisi bulunamadı";
    }
}
