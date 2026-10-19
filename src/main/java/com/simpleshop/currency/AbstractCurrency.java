package com.simpleshop.currency;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Ortak ad / sembol / biçim / yuvarlama mantığı. */
public abstract class AbstractCurrency implements Currency {

    private final String id;
    private final String name;
    private final String symbol;
    private final String pattern;
    private final int decimals;
    private final Locale locale;

    protected AbstractCurrency(String id, ConfigurationSection cfg, Locale locale) {
        this.id = id;
        this.name = cfg.getString("name", id);
        this.symbol = cfg.getString("symbol", "");
        this.pattern = cfg.getString("format", "{symbol}{amount}");
        this.decimals = Math.max(0, Math.min(8, cfg.getInt("decimals", 2)));
        this.locale = locale;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    public String symbol() {
        return symbol;
    }

    @Override
    public int decimals() {
        return decimals;
    }

    @Override
    public String format(double amount) {
        String numberPattern = decimals > 0 ? "#,##0." + "#".repeat(decimals) : "#,##0";
        String number = new DecimalFormat(numberPattern, DecimalFormatSymbols.getInstance(locale)).format(amount);
        return pattern
                .replace("{symbol}", symbol)
                .replace("{name}", name)
                .replace("{amount}", number);
    }

    @Override
    public double round(double amount) {
        if (!Double.isFinite(amount)) {
            return 0;
        }
        return BigDecimal.valueOf(amount).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    protected static boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount >= 0;
    }
}
