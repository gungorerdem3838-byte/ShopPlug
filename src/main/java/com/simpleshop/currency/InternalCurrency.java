package com.simpleshop.currency;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Eklentinin kendi (ek) para birimi. Bakiyeler data/balances.yml içinde saklanır.
 * Vault'tan bağımsızdır; istenen sayıda tanımlanabilir (kristal, token, puan...).
 */
public final class InternalCurrency extends AbstractCurrency {

    private static final double EPSILON = 1e-9;

    private final Map<UUID, Double> balances = new HashMap<>();
    private final double startingBalance;
    private boolean dirty;

    public InternalCurrency(String id, ConfigurationSection cfg, Locale locale) {
        super(id, cfg, locale);
        this.startingBalance = Math.max(0, cfg.getDouble("starting-balance", 0));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public synchronized double balance(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), startingBalance);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return validAmount(amount) && balance(player) + EPSILON >= amount;
    }

    @Override
    public synchronized boolean withdraw(OfflinePlayer player, double amount) {
        if (!validAmount(amount)) {
            return false;
        }
        double current = balance(player);
        if (current + EPSILON < amount) {
            return false;
        }
        put(player.getUniqueId(), Math.max(0, round(current - amount)));
        return true;
    }

    @Override
    public synchronized boolean deposit(OfflinePlayer player, double amount) {
        if (!validAmount(amount)) {
            return false;
        }
        put(player.getUniqueId(), round(balance(player) + amount));
        return true;
    }

    /** Bakiyeyi doğrudan ayarlar. */
    public synchronized boolean set(OfflinePlayer player, double amount) {
        if (!validAmount(amount)) {
            return false;
        }
        put(player.getUniqueId(), round(amount));
        return true;
    }

    private void put(UUID uuid, double value) {
        balances.put(uuid, value);
        dirty = true;
    }

    // --- Kalıcılık (CurrencyManager kullanır) ---

    synchronized void loadBalance(UUID uuid, double value) {
        balances.put(uuid, value);
    }

    synchronized Map<UUID, Double> snapshot() {
        return new HashMap<>(balances);
    }

    synchronized boolean isDirty() {
        return dirty;
    }

    synchronized void markClean() {
        dirty = false;
    }

    synchronized void clear() {
        balances.clear();
        dirty = false;
    }
}
