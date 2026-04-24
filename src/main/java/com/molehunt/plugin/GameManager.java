package com.molehunt.plugin;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class GameManager {

    private final MoleHuntPlugin plugin;

    private final Set<UUID> moles = new HashSet<>();
    private final Set<UUID> speedrunners = new HashSet<>();
    private boolean gameRunning = false;

    // Scoreboard for glowing teams
    private Scoreboard scoreboard;
    private Team moleTeam;
    private Team speedrunnerTeam;

    public GameManager(MoleHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public boolean isMole(Player player) {
        return moles.contains(player.getUniqueId());
    }

    public Set<UUID> getMoles() {
        return moles;
    }

    public Set<UUID> getSpeedrunners() {
        return speedrunners;
    }

    public void startGame(Player operator) {
        if (gameRunning) {
            operator.sendMessage(ChatColor.RED + "A MoleHunt game is already running!");
            return;
        }

        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (allPlayers.size() < 2) {
            operator.sendMessage(ChatColor.RED + "Not enough players to start MoleHunt!");
            return;
        }

        int moleCount = plugin.getConfig().getInt("mole-count", 2);
        moleCount = Math.min(moleCount, allPlayers.size() - 1);

        gameRunning = true;
        moles.clear();
        speedrunners.clear();

        // Shuffle and assign roles
        Collections.shuffle(allPlayers);
        for (int i = 0; i < allPlayers.size(); i++) {
            UUID uid = allPlayers.get(i).getUniqueId();
            if (i < moleCount) {
                moles.add(uid);
            } else {
                speedrunners.add(uid);
            }
        }

        // Setup scoreboard for glowing
        setupScoreboard();

        // Phase 1: Give steak and broadcast tab-unbind notice
        for (Player p : allPlayers) {
            p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.COOKED_BEEF, 64));
            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== MOLEHUNT STARTING ===");
            p.sendMessage(ChatColor.YELLOW + "You have received " + ChatColor.WHITE + "64 Cooked Steak" + ChatColor.YELLOW + "!");
            p.sendMessage(ChatColor.AQUA + "Please unbind your " + ChatColor.WHITE + "TAB" + ChatColor.AQUA + " key before the game begins.");
            p.sendMessage(ChatColor.GRAY + "Teleporting in " + ChatColor.RED + "10 seconds" + ChatColor.GRAY + "...");
        }

        // Phase 2: TP after 10 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                teleportAllPlayers();

                // Phase 3: Show role reveal after 5 more seconds
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        revealRoles();
                        applyBorderAndRules();
                    }
                }.runTaskLater(plugin, 100L); // 5 seconds (20 ticks/sec)
            }
        }.runTaskLater(plugin, 200L); // 10 seconds
    }

    private void teleportAllPlayers() {
        World world = Bukkit.getWorlds().get(0);
        int minR = plugin.getConfig().getInt("tp-radius-min", 300);
        int maxR = plugin.getConfig().getInt("tp-radius-max", 900);
        Random rand = new Random();

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Pick a random angle and radius
            double angle = rand.nextDouble() * 2 * Math.PI;
            int radius = minR + rand.nextInt(maxR - minR + 1);
            int x = (int) (Math.cos(angle) * radius);
            int z = (int) (Math.sin(angle) * radius);
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location tpLoc = new Location(world, x + 0.5, y, z + 0.5);
            p.teleport(tpLoc);
            p.sendMessage(ChatColor.GRAY + "You have been teleported. Revealing your role in " + ChatColor.RED + "5 seconds" + ChatColor.GRAY + "...");
        }
    }

    private void revealRoles() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean isMole = moles.contains(p.getUniqueId());

            // Flash title: "YOU ARE" then role
            String roleText = isMole
                    ? ChatColor.RED + "" + ChatColor.BOLD + "MOLE"
                    : ChatColor.WHITE + "" + ChatColor.BOLD + "SPEEDRUNNER";

            p.sendTitle(
                    ChatColor.YELLOW + "" + ChatColor.BOLD + "YOU ARE",
                    roleText,
                    10, 60, 20
            );

            if (isMole) {
                // Tell moles who the other moles are
                StringBuilder moleList = new StringBuilder();
                for (UUID uid : moles) {
                    Player molePlayer = Bukkit.getPlayer(uid);
                    if (molePlayer != null && !molePlayer.equals(p)) {
                        if (moleList.length() > 0) moleList.append(", ");
                        moleList.append(ChatColor.RED).append(molePlayer.getName());
                    }
                }
                if (moleList.length() > 0) {
                    p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "[MOLES] " + ChatColor.RED + "Your fellow mole(s): " + moleList);
                } else {
                    p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "[MOLES] " + ChatColor.RED + "You are the only mole!");
                }
                p.sendMessage(ChatColor.DARK_RED + "[MOLES] " + ChatColor.RED + "You can see the locator bar and player deaths.");
                p.sendMessage(ChatColor.DARK_RED + "[MOLES] " + ChatColor.RED + "Hold TAB to glow — Red = Moles, White = Speedrunners.");
            } else {
                p.sendMessage(ChatColor.AQUA + "[SPEEDRUNNERS] " + ChatColor.WHITE + "You can see the locator bar.");
                p.sendMessage(ChatColor.AQUA + "[SPEEDRUNNERS] " + ChatColor.WHITE + "Chat is muted for you.");
                p.sendMessage(ChatColor.AQUA + "[SPEEDRUNNERS] " + ChatColor.WHITE + "Hold TAB to glow — Red = Moles, White = Speedrunners.");
            }
        }
    }

    private void applyBorderAndRules() {
        World world = Bukkit.getWorlds().get(0);
        int borderSize = plugin.getConfig().getInt("border-size", 1200);

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(borderSize);

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== MOLEHUNT HAS BEGUN! ===");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "World border: " + ChatColor.WHITE + borderSize + "x" + borderSize + " blocks.");
    }

    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        scoreboard = manager.getNewScoreboard();

        if (scoreboard.getTeam("moles") != null) scoreboard.getTeam("moles").unregister();
        if (scoreboard.getTeam("speedrunners") != null) scoreboard.getTeam("speedrunners").unregister();

        moleTeam = scoreboard.registerNewTeam("moles");
        moleTeam.setColor(ChatColor.RED);
        moleTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);

        speedrunnerTeam = scoreboard.registerNewTeam("speedrunners");
        speedrunnerTeam.setColor(ChatColor.WHITE);
        speedrunnerTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(scoreboard);
            if (moles.contains(p.getUniqueId())) {
                moleTeam.addEntry(p.getName());
            } else {
                speedrunnerTeam.addEntry(p.getName());
            }
        }
    }

    /**
     * Enable glowing for all players with team colours (called when Tab is held).
     */
    public void enableGlowing(Player viewer) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGlowing(true);
        }
    }

    /**
     * Disable glowing for all players.
     */
    public void disableGlowing() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGlowing(false);
        }
    }

    /**
     * Called when a player dies — only show to moles.
     */
    public void onPlayerDeath(Player dead, String deathMessage) {
        if (!gameRunning) return;
        for (UUID uid : moles) {
            Player mole = Bukkit.getPlayer(uid);
            if (mole != null) {
                mole.sendMessage(ChatColor.DARK_RED + "[DEATH] " + ChatColor.RED + deathMessage);
            }
        }
    }

    /**
     * Block chat for speedrunners (called from listener).
     * Returns true if the message should be cancelled.
     */
    public boolean shouldCancelChat(Player sender) {
        return gameRunning && speedrunners.contains(sender.getUniqueId());
    }

    /**
     * Show the locator bar (action bar) with nearby players for a player.
     * Called periodically from a repeating task.
     */
    public void showLocatorBar(Player viewer) {
        if (!gameRunning) return;

        StringBuilder bar = new StringBuilder();
        boolean isMole = moles.contains(viewer.getUniqueId());

        List<Player> targets = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(viewer)) targets.add(p);
        }

        // Sort by distance
        targets.sort(Comparator.comparingDouble(p -> p.getLocation().distance(viewer.getLocation())));

        for (Player target : targets) {
            double dist = target.getLocation().distance(viewer.getLocation());
            boolean targetIsMole = moles.contains(target.getUniqueId());

            ChatColor color;
            if (isMole) {
                color = targetIsMole ? ChatColor.RED : ChatColor.WHITE;
            } else {
                // Speedrunners see everyone in white (can't tell who is mole)
                color = ChatColor.WHITE;
            }

            bar.append(color).append(target.getName())
               .append(ChatColor.GRAY).append(" [")
               .append((int) dist).append("m] ");
        }

        if (bar.length() > 0) {
            viewer.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(bar.toString())
            );
        }
    }

    public void endGame() {
        gameRunning = false;
        moles.clear();
        speedrunners.clear();

        // Re-enable chat and remove scoreboard
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGlowing(false);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "=== MOLEHUNT HAS ENDED ===");
    }
}
