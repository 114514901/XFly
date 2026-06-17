package com.xfly.listener;

import com.xfly.manager.FlightManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final FlightManager flightManager;

    public PlayerListener(FlightManager flightManager) {
        this.flightManager = flightManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!flightManager.isFlying(player)) return;

        // 记录移动，用于检测悬停
        flightManager.recordMovement(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家退出时自动清理飞行状态（已由 FlightManager tick 处理）
        // 但为了立即清理，直接移除
        Player player = event.getPlayer();
        player.setAllowFlight(false);
        player.setFlying(false);
    }
}
