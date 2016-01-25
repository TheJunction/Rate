/*
 * Copyright (c) 2016 CubeXMC. All Rights Reserved.
 * Created by PantherMan594.
 */

package net.cubexmc.rate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.edge209.OnTime.DataIO;
import me.edge209.OnTime.OnTimeAPI;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.models.SortedPlayer;
import org.black_ixx.playerpoints.storage.StorageHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.net.URL;
import java.util.*;

/**
 * Created by David on 7/9/2015.
 *
 * @author David
 */
public class Main extends JavaPlugin implements Listener {

    public HashMap<UUID, Long> timeCache;
    public HashMap<UUID, Integer> pointsCache;
    public HashMap<String, String> uuidCache;
    private PlayerPoints playerPoints;

    @Override
    public void onEnable() {
        if (hookPlayerPoints()) {
            getLogger().info("Successfully hooked into PlayerPoints!");
        } else {
            getLogger().warning("Could not hook into PlayerPoints, disabling...");
            setEnabled(false);
            return;
        }
        timeCache = new HashMap<>();
        pointsCache = new HashMap<>();
        uuidCache = new HashMap<>();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTime(p);
            pointsCache.put(p.getUniqueId(), (int) Math.floor(playerPoints.getAPI().look(p.getUniqueId()) / 1000));
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("points", "dummy");
            objective.setDisplayName("Points Leaderboard");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            HashMap<String, Integer> lBoard = getLead(p.getName());
            for (String name : lBoard.keySet()) {
                objective.getScore(name).setScore(lBoard.get(name));
            }
            p.setScoreboard(scoreboard);
        }
    }

    private boolean hookPlayerPoints() {
        final Plugin plugin = this.getServer().getPluginManager().getPlugin("PlayerPoints");
        playerPoints = PlayerPoints.class.cast(plugin);
        return playerPoints != null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        updateTime(p);
        pointsCache.put(p.getUniqueId(), (int) Math.floor(playerPoints.getAPI().look(p.getUniqueId()) / 1000));
        final Scoreboard oldBoard = p.getScoreboard();
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("points", "dummy");
        objective.setDisplayName("Points Leaderboard");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        HashMap<String, Integer> lBoard = getLead(p.getName());
        for (String name : lBoard.keySet()) {
            objective.getScore(name).setScore(lBoard.get(name));
        }
        p.setScoreboard(scoreboard);
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                p.setScoreboard(oldBoard);
            }
        }, 600);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        updateTime(e.getPlayer());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        int rating = pointsCache.get(e.getPlayer().getUniqueId());
        String msg;
        if (rating >= 20) {
            msg = e.getFormat().replace("{rating}", ChatColor.DARK_BLUE + "" + rating);
        } else if (rating >= 15) {
            msg = e.getFormat().replace("{rating}", ChatColor.BLUE + "" + rating);
        } else if (rating >= 10) {
            msg = e.getFormat().replace("{rating}", "" + rating);
        } else if (rating >= 5) {
            msg = e.getFormat().replace("{rating}", ChatColor.GRAY + "" + rating);
        } else {
            msg = e.getFormat().replace("{rating}", ChatColor.DARK_GRAY + "" + rating);
        }
        e.setFormat(msg);
    }

    public void updateTime(Player p) {
        if (timeCache.containsKey(p.getUniqueId())) {
            Long oldTime = timeCache.get(p.getUniqueId());
            Long newTime = DataIO.getPlayerTimeData(p.getName(), OnTimeAPI.data.TOTALPLAY);
            if (newTime > oldTime) {
                Long diff = oldTime - newTime;
                if (diff > 2 * 60 * 60 * 1000) {
                    rate(p.getUniqueId(), false);
                }
            }
        }
        timeCache.put(p.getUniqueId(), DataIO.getPlayerTimeData(p.getName(), OnTimeAPI.data.TOTALPLAY));
    }

    public void rate(UUID uuid, boolean negative) {
        int newPoints = playerPoints.getAPI().look(uuid) + (negative ? -1000 : 1000);
        playerPoints.getAPI().set(uuid, newPoints);
        pointsCache.put(uuid, (int) Math.floor(newPoints / 1000));
    }

    public HashMap<String, Integer> getLead(String name) {
        SortedSet leaders = sortLeaders(playerPoints, playerPoints.getModuleForClass(StorageHandler.class).getPlayers());
        SortedPlayer[] array = (SortedPlayer[]) leaders.toArray(new SortedPlayer[leaders.size()]);
        HashMap<String, Integer> leadPoints = new HashMap<>();
        for (int i = 0; i < array.length; ++i) {
            SortedPlayer player = array[i];
            String pName = Bukkit.getOfflinePlayer(UUID.fromString(player.getName())).getName();
            if (pName == null) {
                if (uuidCache.containsKey(player.getName())) {
                    pName = uuidCache.get(player.getName());
                } else {
                    try {
                        URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + player.getName().replace("-", ""));
                        Scanner jsonScanner = new Scanner(url.openConnection().getInputStream(), "UTF-8");
                        String json = jsonScanner.next();
                        pName = (((JsonObject) new JsonParser().parse(json)).get("name")).toString().replace("\"", "");
                        jsonScanner.close();
                        uuidCache.put(player.getName(), pName);
                    } catch (Exception e) {
                        pName = "null";
                    }
                }
            }
            if (i < 10 || pName.equals(name)) {
                if (i > 9) {
                    leadPoints.put(ChatColor.GRAY + "...", player.getPoints() + 1);
                }
                leadPoints.put(ChatColor.AQUA + "" + (i + 1) + ". " + ChatColor.GRAY + pName, player.getPoints());
            }
        }
        return leadPoints;
    }

    private SortedSet<SortedPlayer> sortLeaders(PlayerPoints plugin, Collection<String> players) {
        TreeSet<SortedPlayer> sorted = new TreeSet<>();

        for (String name : players) {
            int points = plugin.getAPI().look(UUID.fromString(name));
            sorted.add(new SortedPlayer(name, points));
        }

        return sorted;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.RED + "This command may only be run from the console.");
            return false;
        }
        if (commandLabel.equalsIgnoreCase("rate")) {
            if (args.length == 2 && (args[0].equals("negative") || args[0].equals("positive"))) {
                String name = args[1];
                UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
                if (Bukkit.getPlayer(name) != null) {
                    uuid = Bukkit.getPlayer(name).getUniqueId();
                }
                rate(uuid, args[0].equals("negative"));
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid args. Usage: /rate <negative|positive> <playername>");
            }
        }
        return false;
    }
}
