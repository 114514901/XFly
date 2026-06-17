package com.xfly.command;

import com.xfly.XFly;
import com.xfly.config.ConfigManager;
import com.xfly.gui.FlyGUI;
import com.xfly.manager.FlightManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class XFlyCommand implements CommandExecutor, TabCompleter {

    private final XFly plugin;
    private final FlightManager flightManager;
    private final ConfigManager configManager;
    private final FlyGUI flyGUI;

    public XFlyCommand(XFly plugin, FlightManager flightManager, ConfigManager configManager, FlyGUI flyGUI) {
        this.plugin = plugin;
        this.flightManager = flightManager;
        this.configManager = configManager;
        this.flyGUI = flyGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // 重载命令 - 控制台和玩家都可以
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xfly.reload")) {
                sender.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                        configManager.getNoPermissionMessage()));
                return true;
            }
            configManager.reload();
            sender.sendMessage(net.md_5.bungee.api.ChatColor.GREEN + "配置文件已重载");
            return true;
        }

        // GUI 命令
        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("只有玩家可以打开GUI");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("xfly.use")) {
                player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                        configManager.getNoPermissionMessage()));
                return true;
            }
            flyGUI.open(player);
            return true;
        }

        // 玩家执行切换飞行
        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以切换飞行模式");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("xfly.use")) {
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                    configManager.getNoPermissionMessage()));
            return true;
        }

        flightManager.toggleFlight(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("xfly.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("xfly.use")) {
                completions.add("gui");
            }
        }

        return completions;
    }
}
