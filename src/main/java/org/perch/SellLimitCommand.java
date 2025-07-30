package org.perch;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellLimitCommand implements CommandExecutor {

    private final PerchShopLimits plugin;

    public SellLimitCommand(PerchShopLimits plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        SellLimitListener listener = plugin.getSellLimitListener();

        if (args.length == 0) {
            // /limit (self)
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command without arguments.");
                return true;
            }
            Player player = (Player) sender;
            double sold = listener.getSoldAmount(player.getUniqueId());
            double limit = listener.getDailyLimit();
            player.sendMessage(ChatColor.GRAY + "You have earned $" + String.format("%.2f", sold) + " using /shop today. Your daily limit is $" + String.format("%.2f", limit) + ".");
            return true;
        } else {
            // /limit <player>
            if (!sender.hasPermission("perchshoplimits.check.others")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to check others' limits.");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online.");
                return true;
            }
            double sold = listener.getSoldAmount(target.getUniqueId());
            double limit = listener.getDailyLimit();
            sender.sendMessage(ChatColor.GREEN + target.getName() + " has earned $" + String.format("%.2f", sold) + " today. Their daily limit is $" + String.format("%.2f", limit) + ".");
            return true;
        }
    }
}