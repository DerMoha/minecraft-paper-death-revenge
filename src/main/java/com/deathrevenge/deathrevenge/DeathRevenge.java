package com.deathrevenge.deathrevenge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.*;

public class DeathRevenge extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, RevengeTask> revengeTasks = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("deathrevenge").setExecutor(this);
        getCommand("dr").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("deathrevenge") || command.getName().equalsIgnoreCase("dr")) {
            sender.sendMessage("§6§lDeathRevenge Help");
            sender.sendMessage("§eWhen you die, you will be given a chance for revenge!");
            sender.sendMessage("§eYou will be teleported to a random player and have 30 seconds to kill them.");
            sender.sendMessage("§cIf you fail to kill your target, you will be banned for 1 hour!");
            sender.sendMessage("§aIf you succeed, you will be teleported back to your death location.");
            sender.sendMessage("§eIf no players are online when you die, you will be banned for 30 minutes.");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // If this player was in revenge mode and failed to kill their target 
        if (revengeTasks.containsKey(victim.getUniqueId())) {
            RevengeTask task = revengeTasks.get(victim.getUniqueId());
            task.cancel();
            revengeTasks.remove(victim.getUniqueId());
            
            // Ban the player for 1 hour
            Bukkit.getScheduler().runTask(this, () -> 
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                    victim.getName(),
                    "Failed revenge attempt",
                    new Date(System.currentTimeMillis() + 3600000), // 1 hour
                    null
                )
            );
            victim.kickPlayer("You failed your revenge attempt. You are banned for 1 hour!");
            return;
        }

        // Store death location
        deathLocations.put(victim.getUniqueId(), victim.getLocation());

        // Find a random player that isn't the victim
        List<Player> possibleTargets = new ArrayList<>(Bukkit.getOnlinePlayers());
        possibleTargets.remove(victim);
        if (possibleTargets.isEmpty()) {
            // Ban the player for 30 minutes when no other players are online
            Bukkit.getScheduler().runTask(this, () -> 
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                    victim.getName(),
                    "No players available for revenge",
                    new Date(System.currentTimeMillis() + 1800000), // 30 minutes
                    null
                )
            );
            victim.kickPlayer("No players available for revenge. You are banned for 30 minutes!");
            return;
        }

        Player randomTarget = possibleTargets.get(new Random().nextInt(possibleTargets.size()));

        // Teleport the victim to the random target
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (victim.isOnline()) {
                victim.teleport(randomTarget.getLocation());
                victim.sendMessage("§cYou have 30 seconds to kill " + randomTarget.getName() + " for revenge!");
                randomTarget.sendMessage("§c" + victim.getName() + " is hunting you for revenge!");

                // Create and start the revenge task
                RevengeTask revengeTask = new RevengeTask(victim, randomTarget);
                revengeTasks.put(victim.getUniqueId(), revengeTask);
                revengeTask.runTaskLater(this, 600); // 30 seconds = 600 ticks
            }
        }, 1L); // Small delay after death
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && revengeTasks.containsKey(killer.getUniqueId())) {
            RevengeTask task = revengeTasks.get(killer.getUniqueId());
            
            // Check if the killed player was the revenge target
            if (victim.getUniqueId().equals(task.getTargetUUID())) {
                task.cancel();
                revengeTasks.remove(killer.getUniqueId());
                
                // Teleport back to death location and set health to 1
                Location deathLoc = deathLocations.get(killer.getUniqueId());
                if (deathLoc != null) {
                    killer.teleport(deathLoc);
                    deathLocations.remove(killer.getUniqueId());
                }
                
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    killer.setHealth(1.0);
                    killer.sendMessage("§aRevenge successful! You've been returned to your death location.");
                }, 1L);
            }
        }
    }

    private class RevengeTask extends BukkitRunnable {
        private final UUID playerUUID;
        private final UUID targetUUID;

        public RevengeTask(Player player, Player target) {
            this.playerUUID = player.getUniqueId();
            this.targetUUID = target.getUniqueId();
        }

        @Override
        public void run() {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                // Time's up - ban the player
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                    player.getName(),
                    "Failed revenge attempt",
                    new Date(System.currentTimeMillis() + 3600000), // 1 hour
                    null
                );
                player.kickPlayer("Time's up! You failed your revenge attempt. You are banned for 1 hour!");
            }
            revengeTasks.remove(playerUUID);
            deathLocations.remove(playerUUID);
        }

        public UUID getTargetUUID() {
            return targetUUID;
        }
    }
} 