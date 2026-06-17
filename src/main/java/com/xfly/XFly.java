package com.xfly;

import com.xfly.command.XFlyCommand;
import com.xfly.config.ConfigManager;
import com.xfly.gui.FlyGUI;
import com.xfly.listener.GUIListener;
import com.xfly.listener.PlayerListener;
import com.xfly.manager.FlightManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class XFly extends JavaPlugin {

    private Economy economy;
    private ConfigManager configManager;
    private FlightManager flightManager;
    private FlyGUI flyGUI;

    @Override
    public void onEnable() {
        // 加载配置
        configManager = new ConfigManager(this);
        configManager.load();

        // 检查 Vault
        if (!setupEconomy()) {
            getLogger().severe("未找到 Vault 或经济实现，插件已禁用");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化管理器
        flightManager = new FlightManager(this, economy, configManager);
        flightManager.start();

        // 初始化 GUI
        flyGUI = new FlyGUI(this, flightManager, configManager, economy);

        // 将 GUI 注入 FlightManager（用于联动累计消耗）
        flightManager.setFlyGUI(flyGUI);

        // 注册命令
        XFlyCommand command = new XFlyCommand(this, flightManager, configManager, flyGUI);
        getCommand("xfly").setExecutor(command);
        getCommand("xfly").setTabCompleter(command);

        // 注册监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(flightManager), this);
        getServer().getPluginManager().registerEvents(new GUIListener(flyGUI), this);

        getLogger().info("XFly 已启用 - 消耗飞行系统 v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        if (flightManager != null) {
            flightManager.stop();
        }
        getLogger().info("XFly 已禁用");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() { return economy; }
    public ConfigManager getConfigManager() { return configManager; }
    public FlightManager getFlightManager() { return flightManager; }
    public FlyGUI getFlyGUI() { return flyGUI; }
}
