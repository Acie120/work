package com.molehunt.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class MoleHuntPlugin extends JavaPlugin {

    private static MoleHuntPlugin instance;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        gameManager = new GameManager(this);

        getCommand("startmolehunt").setExecutor(new StartCommand(this));
        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        getLogger().info("MoleHunt plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MoleHunt plugin disabled.");
    }

    public static MoleHuntPlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
