/*
 * Copyright (c) 2015 CubeXMC. All Rights Reserved.
 * Created by PantherMan594.
 */

package net.cubexmc.rate;

import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by David on 7/9/2015.
 *
 * @author David
 */
public class Main extends JavaPlugin implements Listener {

    private static Connection conn;
    public HashMap<UUID, Integer> timeMap = new HashMap<>();
    public HashMap<UUID, Location> lastMove = new HashMap<>();
    private PlayerPoints playerPoints;

    @Override
    public void onEnable() {
        if (hookPlayerPoints()) {
            getLogger().info("Successfully hooked into PlayerPoints!");
        } else {
            getLogger().warning("Could not hook into PlayerPoints, disabling...");
            setEnabled(false);
        }
        File f = new File(this.getDataFolder() + File.separator + "players.yml");
        FileConfiguration con = YamlConfiguration.loadConfiguration(f);
        boolean cont = true;
        for (int i = 0; cont; i++) {
            if (con.contains(i + ".uuid") && con.contains(i + ".time")) {
                timeMap.put(UUID.fromString(con.getString(i + ".uuid")), con.getInt(i + ".time"));
            } else {
                cont = false;
            }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (lastMove.containsKey(p.getUniqueId())) {
                        Location lastLoc = lastMove.get(p.getUniqueId());
                        Location currLoc = p.getLocation();
                        if (Math.abs(lastLoc.getX() - currLoc.getX()) >= 5 || Math.abs(lastLoc.getZ() - currLoc.getZ()) >= 5) {
                            if (timeMap.containsKey(p.getUniqueId())) {
                                timeMap.put(p.getUniqueId(), timeMap.get(p.getUniqueId()) + 1);
                            } else {
                                timeMap.put(p.getUniqueId(), 1);
                            }
                        }
                        if (timeMap.get(p.getUniqueId()) >= 60) {
                            timeMap.put(p.getUniqueId(), timeMap.get(p.getUniqueId()) - 60);
                            playerPoints.getAPI().give(p.getUniqueId(), 50);
                        }
                    }
                }
            }
        }, 0, 1200);
    }

    @Override
    public void onDisable() {
        if (!this.getDataFolder().exists()) {
            this.getDataFolder().mkdir();
        }
        File f = new File(this.getDataFolder() + File.separator + "players.yml");
        FileConfiguration con = YamlConfiguration.loadConfiguration(f);
        int i = 0;
        for (UUID uuid : timeMap.keySet()) {
            con.set(i + ".uuid", uuid.toString());
            con.set(i + ".time", timeMap.get(uuid));
            i++;
        }
        try {
            con.save(f);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean hookPlayerPoints() {
        final Plugin plugin = this.getServer().getPluginManager().getPlugin("PlayerPoints");
        playerPoints = PlayerPoints.class.cast(plugin);
        return playerPoints != null;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        String msg = e.getFormat().replace("{rating}", "" + Math.floor(playerPoints.getAPI().look(e.getPlayer().getUniqueId()) / 1000));
        e.setFormat(msg);
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.RED + "This command may only be run from the console.");
            return false;
        }
        if (commandLabel.equalsIgnoreCase("rate")) {
            if (args.length == 2 && (args[0].equals("negative") || args[0].equals("positive"))) {
                String name = args[1];
                playerPoints.getAPI().give(name, 1000);
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid args. Usage: /rate <negative|positive> <playername>");
            }
        }
        return false;
    }
}
