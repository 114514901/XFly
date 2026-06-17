package com.xfly.gui;

import com.xfly.XFly;
import com.xfly.config.ConfigManager;
import com.xfly.manager.FlightManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class FlyGUI {

    private final XFly plugin;
    private final FlightManager flightManager;
    private final ConfigManager configManager;
    private final Economy economy;

    // 玩家阈值设置：UUID -> 阈值金额（0=关闭）
    private final Map<UUID, Double> playerThresholds = new HashMap<>();

    // 玩家本次飞行累计消耗：UUID -> 累计金额
    private final Map<UUID, Double> sessionCost = new HashMap<>();

    // 阈值已触发标记：UUID -> 是否已触发警告
    private final Map<UUID, Boolean> thresholdTriggered = new HashMap<>();

    private static final String GUI_TITLE = "§8✈ 飞行控制";

    public FlyGUI(XFly plugin, FlightManager flightManager, ConfigManager configManager, Economy economy) {
        this.plugin = plugin;
        this.flightManager = flightManager;
        this.configManager = configManager;
        this.economy = economy;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // 边框
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Slot 10: 飞行开关
        boolean flying = flightManager.isFlying(player);
        Material toggleMat = flying ? Material.FEATHER : Material.FEATHER;
        String toggleName = flying ? "§a✔ 飞行中" : "§c✘ 已关闭";
        List<String> toggleLore = new ArrayList<>();
        toggleLore.add("§7点击切换飞行模式");
        if (flying) {
            FlightManager.FlightSession session = flightManager.getSession(player);
            if (session != null && session.isBypass()) {
                toggleLore.add("§e⚡ 无限飞行（已绕过消耗）");
            }
        }
        inv.setItem(10, createItem(toggleMat, toggleName, toggleLore));

        // Slot 11: 余额显示
        double balance = economy.getBalance(player);
        inv.setItem(11, createItem(Material.GOLD_NUGGET,
                "§6我的余额",
                Arrays.asList(
                        "§7当前余额: §e" + String.format("%.2f", balance) + " §7" + configManager.getCurrencyName(),
                        "§7每分钟消耗: §c" + String.format("%.2f", configManager.getPricePerMinute()) + " §7" + configManager.getCurrencyName()
                )));

        // Slot 12: 费用说明
        inv.setItem(12, createItem(Material.CLOCK,
                "§b费用说明",
                Arrays.asList(
                        "§7• 开启时预付 §e1 §7分钟费用",
                        "§7• 之后每满 §e1 §7分钟扣费一次",
                        "§7• 悬停 §e" + configManager.getHoverFreezeSeconds() + "§7秒暂停计费",
                        "§7• 余额不足 §e" + configManager.getWarningSeconds() + "§7秒后自动关飞"
                )));

        // Slot 13: 消耗阈值设置
        double threshold = playerThresholds.getOrDefault(player.getUniqueId(), 0.0);
        Material thresholdMat = threshold > 0 ? Material.REDSTONE_TORCH : Material.TRIPWIRE_HOOK;
        String thresholdName = threshold > 0 ? "§c⚠ 消耗阈值: " + String.format("%.0f", threshold) : "§7消耗阈值: 未设置";
        List<String> thresholdLore = new ArrayList<>();
        thresholdLore.add("§7点击切换阈值选项");
        thresholdLore.add("§7可选: §c50 §7| §c100 §7| §c150 §7| §c200");
        thresholdLore.add("§7当前: " + (threshold > 0 ? "§c" + String.format("%.0f", threshold) : "§7关闭"));
        if (threshold > 0) {
            thresholdLore.add("§e达到阈值后将提醒并自动关飞");
        }
        inv.setItem(13, createItem(thresholdMat, thresholdName, thresholdLore));

        // Slot 14: 关闭按钮
        inv.setItem(14, createItem(Material.BARRIER, "§c关闭", Collections.singletonList("§7点击关闭界面")));

        player.openInventory(inv);
    }

    /**
     * 处理 GUI 点击
     */
    public void handleClick(Player player, int slot) {
        switch (slot) {
            case 10: // 飞行开关
                player.closeInventory();
                flightManager.toggleFlight(player);
                break;
            case 13: // 阈值设置
                cycleThreshold(player);
                open(player); // 刷新 GUI
                break;
            case 14: // 关闭
                player.closeInventory();
                break;
        }
    }

    /**
     * 循环切换阈值选项
     */
    private void cycleThreshold(Player player) {
        UUID uuid = player.getUniqueId();
        double current = playerThresholds.getOrDefault(uuid, 0.0);

        double[] options = {0, 50, 100, 150, 200};
        int nextIndex = 0;
        for (int i = 0; i < options.length; i++) {
            if (Math.abs(options[i] - current) < 0.01) {
                nextIndex = (i + 1) % options.length;
                break;
            }
        }
        playerThresholds.put(uuid, options[nextIndex]);

        if (options[nextIndex] > 0) {
            player.sendMessage("§a已设置消耗阈值: §c" + String.format("%.0f", options[nextIndex]) + " §7" + configManager.getCurrencyName());
        } else {
            player.sendMessage("§7已关闭消耗阈值提醒");
        }
    }

    /**
     * 记录本次飞行消耗（由 FlightManager 扣费时调用）
     */
    public void addSessionCost(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        double cost = sessionCost.getOrDefault(uuid, 0.0) + amount;
        sessionCost.put(uuid, cost);

        // 检查阈值
        double threshold = playerThresholds.getOrDefault(uuid, 0.0);
        if (threshold > 0 && cost >= threshold && !thresholdTriggered.getOrDefault(uuid, false)) {
            thresholdTriggered.put(uuid, true);
            // 触发阈值警告
            player.sendMessage("§c⚠ 本次飞行已消耗 §e" + String.format("%.2f", cost) + " §7" + configManager.getCurrencyName() + "§c，达到阈值！");
            player.sendMessage("§c将在10秒后自动关闭飞行...");

            // 10秒后关飞
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (flightManager.isFlying(player)) {
                    flightManager.forceDisableFlight(player, "§c已到达消耗阈值，飞行自动关闭");
                    sessionCost.remove(uuid);
                    thresholdTriggered.remove(uuid);
                }
            }, 200L); // 10秒
        }
    }

    /**
     * 重置本次飞行消耗（关飞时调用）
     */
    public void resetSessionCost(Player player) {
        UUID uuid = player.getUniqueId();
        sessionCost.remove(uuid);
        thresholdTriggered.remove(uuid);
    }

    public double getThreshold(Player player) {
        return playerThresholds.getOrDefault(player.getUniqueId(), 0.0);
    }

    private ItemStack createItem(Material material, String name) {
        return createItem(material, name, null);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
