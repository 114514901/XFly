package com.xfly.listener;

import com.xfly.gui.FlyGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GUIListener implements Listener {

    private static final String GUI_TITLE = "§8✈ 飞行控制";

    private final FlyGUI flyGUI;

    public GUIListener(FlyGUI flyGUI) {
        this.flyGUI = flyGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getSlot() < 0) return;

        Player player = (Player) event.getWhoClicked();
        flyGUI.handleClick(player, event.getSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);
    }
}
