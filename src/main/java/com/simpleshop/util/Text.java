package com.simpleshop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Renk kodu (&amp; ve &amp;#rrggbb) ve metin yardımcıları. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private Text() {
    }

    /** Sohbet / başlık için bileşen. */
    public static Component chat(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /** Eşya adı / lore için bileşen (varsayılan italik kapalı). */
    public static Component item(String text) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(chat(text));
    }

    /** Renk kodlarını atıp düz metin döndürür. */
    public static String plain(String legacyText) {
        return PlainTextComponentSerializer.plainText().serialize(chat(legacyText));
    }

    /** OAK_LOG -> Oak Log */
    public static String pretty(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** Para birimi bilinmediğinde kullanılan basit sayı biçimi. */
    public static String number(double value) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(TR)).format(value);
    }
}
