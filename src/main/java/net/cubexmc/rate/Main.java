package net.cubexmc.rate;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by David on 7/9/2015.
 *
 * @author David
 */
public class Main extends JavaPlugin implements Listener {

    Connection conn;
    public MySQLManager mysql = new MySQLManager(this);
    public HashMap<UUID, Integer> timeMap = new HashMap<>();
    public HashMap<UUID, LocalTime> lastMove = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        try {
            this.mysql.setupDB();
            conn = this.mysql.getDB();
        } catch (Exception e) {
            getLogger().warning("Couldn't connect to database. Error: ");
            e.printStackTrace();
        }
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (lastMove.containsKey(p.getUniqueId())) {
                        LocalTime currTm = java.time.LocalTime.now();
                        LocalTime lastTm = lastMove.get(p.getUniqueId());
                        if (currTm.getHour() == lastTm.getHour() && currTm.getMinute() - lastTm.getMinute() <= 5) {
                            if (timeMap.containsKey(p.getUniqueId())) {
                                timeMap.put(p.getUniqueId(), timeMap.get(p.getUniqueId()) + 1);
                            } else {
                                timeMap.put(p.getUniqueId(), 1);
                            }
                        } else if (currTm.getHour() - 1 == lastTm.getHour() && currTm.getMinute() + 60 - lastTm.getMinute() <= 5) {
                            if (timeMap.containsKey(p.getUniqueId())) {
                                timeMap.put(p.getUniqueId(), timeMap.get(p.getUniqueId()) + 1);
                            } else {
                                timeMap.put(p.getUniqueId(), 1);
                            }
                        }
                        if (timeMap.get(p.getUniqueId()) >= 120) {
                            timeMap.put(p.getUniqueId(), timeMap.get(p.getUniqueId()) - 120);
                            if (playerDataContainsPlayer(p.getName())) {
                                mysql.checkConnection();
                                try {
                                    ResultSet rs = conn.createStatement().executeQuery("SELECT rating FROM ratings WHERE lastname='" + p.getName() + "'");
                                    rs.next();
                                    int rating = rs.getInt("rating") + 1;
                                    conn.createStatement().executeUpdate("UPDATE ratings SET rating='" + rating + "' WHERE lastname='" + p.getName() + "'");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "exec " + p.getName() + " rate positive " + p.getName());
                                getLogger().info("exec " + p.getName() + " rate positive " + p.getName());
                            }
                        }
                        Bukkit.getLogger().info("run" + timeMap.get(p.getUniqueId()));
                    }
                }
            }
        }, 0, 1200);
    }

    @Override
    public void onDisable() {
        try {
            this.mysql.closeDB();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean playerDataContainsPlayer(String name) {
        this.mysql.checkConnection();
        try {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM ratings WHERE lastname='" + name + "';");
            return rs.next();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean playerDataContainsUuid(String uuid) {
        this.mysql.checkConnection();
        try {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM ratings WHERE uuid='" + uuid + "';");
            return rs.next();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        int rating = 0;
        this.mysql.checkConnection();
        try {
            ResultSet rs = conn.createStatement().executeQuery("SELECT rating FROM ratings WHERE lastname='" + event.getPlayer().getName() + "'");
            rs.next();
            rating = rs.getInt("rating");
        } catch (Exception e) {
            e.printStackTrace();
        }
        String msg = event.getFormat().replace("{rating}", "" + rating);
        event.setFormat(msg);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)  {
        String name = event.getPlayer().getName();
        UUID uuid = event.getPlayer().getUniqueId();
        this.mysql.checkConnection();
        try {
            if (playerDataContainsUuid(uuid.toString())) {
                conn.createStatement().executeUpdate("UPDATE ratings SET lastname='" + name + "' WHERE uuid='" + uuid + "';");
            } else if (playerDataContainsPlayer(name)) {
                conn.createStatement().executeUpdate("UPDATE ratings SET uuid='" + uuid + "' WHERE lastname='" + name + "';");
            } else {
                conn.createStatement().executeUpdate("INSERT INTO ratings(uuid, lastname, rating) VALUES ('" + uuid + "', '" + name + "', 0);");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            lastMove.put(event.getPlayer().getUniqueId(), java.time.LocalTime.now());
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.RED + "This command may only be run from the console.");
            return false;
        }
        if (commandLabel.equalsIgnoreCase("rate")) {
            if (args.length == 2 && (args[0].equals("negative") || args[0].equals("positive"))) {
                String name = args[1];
                if (playerDataContainsPlayer(name)) {
                    this.mysql.checkConnection();
                    try {
                        ResultSet rs = conn.createStatement().executeQuery("SELECT rating FROM ratings WHERE lastname='" + name + "'");
                        rs.next();
                        int rating = rs.getInt("rating");
                        if (args[0].equals("negative")) {
                            rating = rating - 1;
                        } else {
                            rating = rating + 1;
                        }
                        conn.createStatement().executeUpdate("UPDATE ratings SET rating='" + rating + "' WHERE lastname='" + name + "'");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "exec " + name + " rate " + args[0] + " " + args[1]);
                    getLogger().info("exec " + name + " rate " + args[0] + " " + args[1]);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid args. Usage: /rate <negative|positive> <playername>");
            }
        }
        return false;
    }
}
