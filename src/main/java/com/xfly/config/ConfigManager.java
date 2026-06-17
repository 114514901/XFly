package com.xfly.config;

import com.xfly.XFly;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final XFly plugin;
    private FileConfiguration config;

    private double pricePerMinute;
    private String currencyName;
    private String flyOnMessage;
    private String flyOffMessage;
    private String noMoneyMessage;
    private String noPermissionMessage;
    private String warningMessage;
    private int warningSeconds;
    private int hoverFreezeSeconds;

    public ConfigManager(XFly plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // 检查配置版本
        int configVersion = config.getInt("ScriptIrc-config-version", 0);
        if (configVersion < 1) {
            plugin.getLogger().warning("配置文件版本过旧，请删除后重新生成");
        }

        pricePerMinute = config.getDouble("price-per-minute", 1.0);
        currencyName = config.getString("currency-name", "货币");
        flyOnMessage = config.getString("fly-on-message", "&a已开启飞行，每分钟消耗 %price% %currency%");
        flyOffMessage = config.getString("fly-off-message", "&e飞行已关闭");
        noMoneyMessage = config.getString("no-money-message", "&c余额不足，飞行已自动关闭");
        noPermissionMessage = config.getString("no-permission-message", "&c你没有权限使用此命令");
        warningMessage = config.getString("warning-message", "&c⚠ 余额不足！将在 %seconds% 秒后自动关闭飞行");
        warningSeconds = config.getInt("warning-seconds", 10);
        hoverFreezeSeconds = config.getInt("hover-freeze-seconds", 300);
    }

    public void reload() {
        load();
    }

    public double getPricePerMinute() { return pricePerMinute; }
    public String getCurrencyName() { return currencyName; }
    public String getFlyOnMessage() { return flyOnMessage; }
    public String getFlyOffMessage() { return flyOffMessage; }
    public String getNoMoneyMessage() { return noMoneyMessage; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public String getWarningMessage() { return warningMessage; }
    public int getWarningSeconds() { return warningSeconds; }
    public int getHoverFreezeSeconds() { return hoverFreezeSeconds; }
}
