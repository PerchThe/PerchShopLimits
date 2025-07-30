package org.perch;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import net.brcdev.shopgui.event.ShopPreTransactionEvent;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SellLimitListener implements Listener {
    private final PerchShopLimits plugin;
    private final Map<UUID, Double> soldToday = new HashMap<>();
    private final double DAILY_LIMIT;
    private File dataFile;
    private FileConfiguration dataConfig;
    private LocalDate lastResetDate;

    // Set to track which players have been notified this tick
    private final Set<UUID> notifiedThisTick = new HashSet<>();

    public SellLimitListener(PerchShopLimits plugin) {
        this.plugin = plugin;
        this.DAILY_LIMIT = plugin.getConfig().getDouble("daily_limit", 10000.0); // Load from config.yml
        setupDataFile();
        loadData();
        checkAndResetIfNeeded();
    }

    private void setupDataFile() {
        dataFile = new File(plugin.getDataFolder(), "limits.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadData() {
        soldToday.clear();
        if (dataConfig.contains("lastReset")) {
            lastResetDate = LocalDate.parse(dataConfig.getString("lastReset"));
        } else {
            lastResetDate = LocalDate.now();
            dataConfig.set("lastReset", lastResetDate.toString());
            saveData();
        }
        if (dataConfig.contains("sold")) {
            for (String uuidStr : dataConfig.getConfigurationSection("sold").getKeys(false)) {
                double amount = dataConfig.getDouble("sold." + uuidStr);
                soldToday.put(UUID.fromString(uuidStr), amount);
            }
        }
    }

    private void saveData() {
        dataConfig.set("lastReset", lastResetDate.toString());
        for (Map.Entry<UUID, Double> entry : soldToday.entrySet()) {
            dataConfig.set("sold." + entry.getKey().toString(), entry.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void resetData() {
        soldToday.clear();
        lastResetDate = LocalDate.now();
        dataConfig.set("sold", null); // Clear all sold data
        saveData();
    }

    private void checkAndResetIfNeeded() {
        if (!lastResetDate.equals(LocalDate.now())) {
            resetData();
        }
    }

    public double getSoldAmount(UUID uuid) {
        return soldToday.getOrDefault(uuid, 0.0);
    }

    public double getDailyLimit() {
        return DAILY_LIMIT;
    }

    /**
     * Adds to the sold amount for a player and saves.
     */
    public void addSoldAmount(UUID uuid, double amount) {
        soldToday.put(uuid, getSoldAmount(uuid) + amount);
        saveData();
    }

    /**
     * Sets the sold amount for a player and saves.
     */
    public void setSoldAmount(UUID uuid, double amount) {
        soldToday.put(uuid, amount);
        saveData();
    }

    /**
     * Reloads the data from disk.
     */
    public void reloadData() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    @EventHandler
    public void onShopPreTransaction(ShopPreTransactionEvent event) {
        Player player = event.getPlayer();
        ShopAction action = event.getShopAction();

        checkAndResetIfNeeded(); // In case server stays up past midnight

        if (action != ShopAction.SELL && action != ShopAction.SELL_ALL) {
            return;
        }

        double moneyToEarn = event.getPrice();
        double earned = soldToday.getOrDefault(player.getUniqueId(), 0.0);

        if (earned + moneyToEarn > DAILY_LIMIT) {
            // Only send the message for real sales, and only once per command/tick
            if (event.getAmount() > 0 && event.getPrice() > 0.0 && !notifiedThisTick.contains(player.getUniqueId())) {
                if (action == ShopAction.SELL_ALL) {
                    double remaining = DAILY_LIMIT - earned;
                    double pricePerItem = moneyToEarn / event.getAmount();
                    int maxSellable = (int) Math.floor(remaining / pricePerItem);

                    if (maxSellable > 0) {
                        player.sendMessage(ChatColor.RED + "You can only sell " + maxSellable + " of this item, selling all would exceed your /limit.");
                    } else {
                        player.sendMessage(ChatColor.RED + "Selling this item would exceed your daily /limit for selling to the admin shop.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Selling this item would exceed your daily /limit for selling to the admin shop.");
                }
                notifiedThisTick.add(player.getUniqueId());
                // Remove from set after 1 tick so the player can be notified again on the next command
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        notifiedThisTick.remove(player.getUniqueId());
                    }
                }.runTaskLater(plugin, 1L);
            }
            event.setCancelled(true);
        } else {
            soldToday.put(player.getUniqueId(), earned + moneyToEarn);
            saveData();
        }
    }
}