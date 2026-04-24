package com.molehunt.plugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GameListener implements Listener {

    private final MoleHuntPlugin plugin;
    private final Set<UUID> tabHolders = new HashSet<>();

    // Locator bar ticker
    private BukkitRunnable locatorTask;

    public GameListener(MoleHuntPlugin plugin) {
        this.plugin = plugin;
        startLocatorTask();
    }

    private void startLocatorTask() {
        locatorTask = new BukkitRunnable() {
            @Override
            public void run() {
                GameManager gm = plugin.getGameManager();
                if (!gm.isGameRunning()) return;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    gm.showLocatorBar(p);
                }
            }
        };
        // Run every second (20 ticks)
        locatorTask.runTaskTimer(plugin, 20L, 20L);
    }

    // ─────────────────────────────────────────────
    // Chat muting for speedrunners
    // ─────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        GameManager gm = plugin.getGameManager();
        if (gm.shouldCancelChat(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Chat is muted for Speedrunners during MoleHunt!");
        }
    }

    // ─────────────────────────────────────────────
    // Death messages: only visible to moles
    // ─────────────────────────────────────────────
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameRunning()) return;

        String deathMessage = event.getDeathMessage();
        if (deathMessage == null) deathMessage = event.getEntity().getName() + " died.";

        // Suppress the global death message
        event.setDeathMessage(null);

        // Relay to moles only
        gm.onPlayerDeath(event.getEntity(), deathMessage);
    }

    // ─────────────────────────────────────────────
    // TAB key detection — via PlayerToggleSneakEvent workaround
    // Minecraft does not fire a TAB event natively.
    // We use PlayerSwapHandItemsEvent (F key default), but
    // for TAB we detect via the tab-list packet toggle using
    // a repeating sneak-state check. Instead, we use the
    // PlayerItemHeldEvent trick: when the player presses tab,
    // their tab-list opens — we approximate with a custom
    // approach using PlayerToggleSprintEvent as a proxy.
    //
    // REAL approach: detect tab hold via a repeating task that
    // checks if the player has an open inventory with the
    // player list. Since Bukkit cannot intercept the Tab key
    // directly, we map TAB glow to SHIFT (sneak) as the
    // recommended in-game keybind alternative.
    //
    // Players are instructed to rebind Tab -> Sneak or use
    // a custom key. Here we use Sneak (Shift) as the glow trigger.
    // ─────────────────────────────────────────────
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        GameManager gm = plugin.getGameManager();
        if (!gm.isGameRunning()) return;

        Player p = event.getPlayer();

        if (event.isSneaking()) {
            // Player started holding Shift (Tab proxy) — enable glowing
            tabHolders.add(p.getUniqueId());
            gm.enableGlowing(p);
        } else {
            // Player released Shift — disable glowing if no one else is holding
            tabHolders.remove(p.getUniqueId());
            if (tabHolders.isEmpty()) {
                gm.disableGlowing();
            }
        }
    }

    // ─────────────────────────────────────────────
    // Prevent disconnected players from leaving roles
    // ─────────────────────────────────────────────
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tabHolders.remove(event.getPlayer().getUniqueId());
    }
}
