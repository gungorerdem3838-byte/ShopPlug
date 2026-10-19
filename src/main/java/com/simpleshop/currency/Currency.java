package com.simpleshop.currency;

import org.bukkit.OfflinePlayer;

/**
 * Bir para birimi. Vault ekonomisi veya eklentinin kendi (ek) para birimleri
 * bu arayüzü uygular; market hepsini aynı şekilde kullanır.
 */
public interface Currency {

    String id();

    String name();

    int decimals();

    /** Kullanılabilir mi? (ör. Vault yüklü ama ekonomi eklentisi yoksa false) */
    boolean isAvailable();

    double balance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double amount);

    boolean withdraw(OfflinePlayer player, double amount);

    boolean deposit(OfflinePlayer player, double amount);

    /** Config'teki biçime göre yazar, ör. "$1.500". */
    String format(double amount);

    /** Para biriminin ondalık hassasiyetine yuvarlar. */
    double round(double amount);

    /** Konsol logu için kısa durum açıklaması. */
    default String statusLine() {
        return isAvailable() ? "aktif" : "kullanılamıyor";
    }
}
