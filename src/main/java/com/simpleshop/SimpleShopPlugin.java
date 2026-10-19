package com.simpleshop;

import com.simpleshop.command.AdminCommand;
import com.simpleshop.command.CurrencyCommand;
import com.simpleshop.command.SellCommand;
import com.simpleshop.command.ShopCommand;
import com.simpleshop.currency.CurrencyManager;
import com.simpleshop.gui.ShopGui;
import com.simpleshop.shop.ShopManager;
import com.simpleshop.shop.TradeService;
import com.simpleshop.util.Messages;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SimpleShopPlugin extends JavaPlugin {

    private Messages messages;
    private CurrencyManager currencies;
    private ShopManager shop;
    private TradeService trade;
    private ShopGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "shop.yml").exists()) {
            saveResource("shop.yml", false);
        }

        messages = new Messages(this);
        currencies = new CurrencyManager(this);
        shop = new ShopManager(this);
        trade = new TradeService(this);
        gui = new ShopGui(this);

        messages.load();
        currencies.load();
        shop.load();

        getServer().getPluginManager().registerEvents(gui, this);
        bind("shop", new ShopCommand(this));
        bind("sell", new SellCommand(this));
        bind("currency", new CurrencyCommand(this));
        bind("simpleshop", new AdminCommand(this));

        // Bakiyeleri periyodik kaydet
        long period = Math.max(1, getConfig().getInt("autosave-minutes", 5)) * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, currencies::saveAsync, period, period);

        // Tüm eklentiler yüklendikten sonra Vault / ekonomi durumunu yaz
        getServer().getScheduler().runTask(this, currencies::logStatus);
    }

    @Override
    public void onDisable() {
        if (gui != null) {
            gui.closeAll();
        }
        if (currencies != null) {
            currencies.saveNow();
        }
    }

    /** config.yml, messages.yml, shop.yml ve para birimlerini yeniden yükler. */
    public void reloadAll() {
        gui.closeAll();
        reloadConfig();
        messages.load();
        currencies.load();
        shop.load();
        currencies.logStatus();
    }

    private void bind(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("plugin.yml içinde '" + name + "' komutu tanımlı değil!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** İşlem sonucuna göre kısa ses çalar (config: sounds). */
    public void sound(Player player, boolean success) {
        if (!getConfig().getBoolean("sounds", true)) {
            return;
        }
        player.playSound(player.getLocation(),
                success ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_VILLAGER_NO,
                0.7f, 1.0f);
    }

    public Messages messages() {
        return messages;
    }

    public CurrencyManager currencies() {
        return currencies;
    }

    public ShopManager shop() {
        return shop;
    }

    public TradeService trade() {
        return trade;
    }

    public ShopGui gui() {
        return gui;
    }
}
