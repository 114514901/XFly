package com.xfly.manager;

import com.xfly.XFly;
import com.xfly.config.ConfigManager;
import com.xfly.gui.FlyGUI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlightManager {

    private final XFly plugin;
    private final Economy economy;
    private final ConfigManager configManager;
    private FlyGUI flyGUI;

    // 飞行中的玩家
    private final Map<UUID, FlightSession> flyingPlayers = new HashMap<>();

    // 悬停计时：玩家UUID -> 悬停秒数
    private final Map<UUID, Integer> hoverTimer = new HashMap<>();

    // 欠费警告：玩家UUID -> 剩余警告秒数
    private final Map<UUID, Integer> warningTimers = new HashMap<>();

    private BukkitTask checkTask;

    public FlightManager(XFly plugin, Economy economy, ConfigManager configManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.configManager = configManager;
    }

    public void setFlyGUI(FlyGUI flyGUI) {
        this.flyGUI = flyGUI;
    }

    public void start() {
        // 每秒检测一次
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        // 清理所有飞行状态
        for (UUID uuid : flyingPlayers.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
        flyingPlayers.clear();
        hoverTimer.clear();
        warningTimers.clear();
    }

    /**
     * 切换玩家飞行状态
     */
    public boolean toggleFlight(Player player) {
        UUID uuid = player.getUniqueId();

        if (flyingPlayers.containsKey(uuid)) {
            disableFlight(player);
            return false; // 已关闭
        } else {
            return enableFlight(player);
        }
    }

    /**
     * 开启飞行（预付1分钟费用）
     */
    public boolean enableFlight(Player player) {
        UUID uuid = player.getUniqueId();

        // 检查 bypass
        if (player.hasPermission("xfly.bypass")) {
            player.setAllowFlight(true);
            player.setFlying(true);
            flyingPlayers.put(uuid, new FlightSession(player, true));
            String msg = configManager.getFlyOnMessage()
                    .replace("%price%", String.valueOf(configManager.getPricePerMinute()))
                    .replace("%currency%", configManager.getCurrencyName());
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', msg));
            return true;
        }

        double price = configManager.getPricePerMinute();
        if (!economy.has(player, price)) {
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                    configManager.getNoMoneyMessage()));
            return false;
        }

        // 预付1分钟
        economy.withdrawPlayer(player, price);

        player.setAllowFlight(true);
        player.setFlying(true);
        flyingPlayers.put(uuid, new FlightSession(player, false));

        String msg = configManager.getFlyOnMessage()
                .replace("%price%", String.valueOf(configManager.getPricePerMinute()))
                .replace("%currency%", configManager.getCurrencyName());
        player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', msg));
        return true;
    }

    /**
     * 关闭飞行
     */
    public void disableFlight(Player player) {
        UUID uuid = player.getUniqueId();
        player.setAllowFlight(false);
        player.setFlying(false);
        flyingPlayers.remove(uuid);
        hoverTimer.remove(uuid);
        warningTimers.remove(uuid);

        // 重置 GUI 累计消耗
        if (flyGUI != null) {
            flyGUI.resetSessionCost(player);
        }

        player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                configManager.getFlyOffMessage()));
    }

    /**
     * 强制关闭飞行（余额不足等情况）
     */
    public void forceDisableFlight(Player player, String reasonMessage) {
        UUID uuid = player.getUniqueId();
        player.setAllowFlight(false);
        player.setFlying(false);
        flyingPlayers.remove(uuid);
        hoverTimer.remove(uuid);
        warningTimers.remove(uuid);

        // 重置 GUI 累计消耗
        if (flyGUI != null) {
            flyGUI.resetSessionCost(player);
        }

        if (reasonMessage != null) {
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', reasonMessage));
        }
    }

    public boolean isFlying(Player player) {
        return flyingPlayers.containsKey(player.getUniqueId());
    }

    public FlightSession getSession(Player player) {
        return flyingPlayers.get(player.getUniqueId());
    }

    public Map<UUID, FlightSession> getFlyingPlayers() {
        return flyingPlayers;
    }

    /**
     * 每秒 tick 检测
     */
    private void tick() {
        for (Map.Entry<UUID, FlightSession> entry : new HashMap<>(flyingPlayers).entrySet()) {
            UUID uuid = entry.getKey();
            FlightSession session = entry.getValue();
            Player player = plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                flyingPlayers.remove(uuid);
                hoverTimer.remove(uuid);
                warningTimers.remove(uuid);
                continue;
            }

            // bypass 玩家不扣费
            if (session.isBypass()) {
                continue;
            }

            // 检查悬停状态
            if (isHovering(player)) {
                int hovered = hoverTimer.getOrDefault(uuid, 0) + 1;
                hoverTimer.put(uuid, hovered);

                // 悬停超过设定秒数，暂停计费
                if (hovered >= configManager.getHoverFreezeSeconds()) {
                    // 重置累计时间，不扣费
                    session.resetAccumulatedSeconds();
                    continue;
                }
            } else {
                hoverTimer.remove(uuid);
            }

            // 累计飞行秒数
            session.addSecond();

            // 每60秒（1分钟）扣费一次
            if (session.getAccumulatedSeconds() >= 60) {
                session.resetAccumulatedSeconds();
                double price = configManager.getPricePerMinute();

                if (!economy.has(player, price)) {
                    // 进入警告状态
                    handleInsufficientBalance(player, uuid);
                } else {
                    // 清除警告状态
                    warningTimers.remove(uuid);
                    economy.withdrawPlayer(player, price);
                    // 通知 GUI 记录累计消耗
                    if (flyGUI != null) {
                        flyGUI.addSessionCost(player, price);
                    }
                }
            }
        }
    }

    /**
     * 处理余额不足
     */
    private void handleInsufficientBalance(Player player, UUID uuid) {
        int warnLeft = warningTimers.getOrDefault(uuid, configManager.getWarningSeconds());

        if (warnLeft <= 0) {
            // 10秒到了，强制关飞
            forceDisableFlight(player, configManager.getNoMoneyMessage());
            warningTimers.remove(uuid);
            return;
        }

        // 更新警告计时
        warningTimers.put(uuid, warnLeft - 1);

        // 发送警告（动作栏 + 消息）
        String warnMsg = configManager.getWarningMessage()
                .replace("%seconds%", String.valueOf(warnLeft));
        player.sendActionBar(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', warnMsg));

        // 每5秒发一次聊天消息，避免刷屏
        if (warnLeft % 5 == 0 || warnLeft <= 3) {
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', warnMsg));
        }
    }

    /**
     * 判断玩家是否在悬停（空中不动）
     */
    private boolean isHovering(Player player) {
        if (!player.isFlying()) return false;
        // 速度趋近于0 且 在空中
        return player.getVelocity().lengthSquared() < 0.001
                && !player.isOnGround();
    }

    /**
     * 记录玩家位置变化，用于检测悬停
     */
    public void recordMovement(Player player) {
        UUID uuid = player.getUniqueId();
        if (!flyingPlayers.containsKey(uuid)) return;

        FlightSession session = flyingPlayers.get(uuid);
        Location loc = player.getLocation();

        if (session.getLastLocation() != null) {
            double dist = loc.distanceSquared(session.getLastLocation());
            if (dist > 0.01) {
                // 玩家移动了，重置悬停计时
                hoverTimer.remove(uuid);
            }
        }
        session.setLastLocation(loc.clone());
    }

    /**
     * 飞行会话数据
     */
    public static class FlightSession {
        private final boolean bypass;
        private int accumulatedSeconds = 0;
        private Location lastLocation;

        public FlightSession(Player player, boolean bypass) {
            this.bypass = bypass;
            this.lastLocation = player.getLocation().clone();
        }

        public boolean isBypass() { return bypass; }
        public int getAccumulatedSeconds() { return accumulatedSeconds; }
        public void addSecond() { accumulatedSeconds++; }
        public void resetAccumulatedSeconds() { accumulatedSeconds = 0; }
        public Location getLastLocation() { return lastLocation; }
        public void setLastLocation(Location loc) { this.lastLocation = loc; }
    }
}
