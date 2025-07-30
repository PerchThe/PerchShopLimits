package org.perch;

import org.bukkit.plugin.java.JavaPlugin;

public class PerchShopLimits extends JavaPlugin {

    private SellLimitListener sellLimitListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        sellLimitListener = new SellLimitListener(this);
        getServer().getPluginManager().registerEvents(sellLimitListener, this);
        getCommand("limit").setExecutor(new SellLimitCommand(this));
        getLogger().info("PerchShopLimits enabled!");
    }

    public SellLimitListener getSellLimitListener() {
        return sellLimitListener;
    }
}