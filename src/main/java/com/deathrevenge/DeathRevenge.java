package com.deathrevenge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.*;

public class DeathRevenge extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, RevengeTask> revengeTasks = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, List<ItemStack>> revengeItems = new HashMap<>();
    private final Map<UUID, Player> pendingRevengeTargets = new HashMap<>();
    private final Map<UUID, Integer> respawnCountdowns = new HashMap<>();
    private final Map<UUID, BossBar> revengeBossBars = new HashMap<>();
    private final Map<UUID, Integer> targetWarnings = new HashMap<>();
    
    private int revengeTime;
    private int revengeFailBanDuration;
    private int noPlayersBanDuration;
    private Material revengeSwordType;
    private boolean revengeSwordEnchanted;
    private List<String> revengeSwordEnchantments;
    private boolean revengeArmorEnabled;
    private Material revengeHelmetType;
    private Material revengeChestplateType;
    private Material revengeLeggingsType;
    private Material revengeBootsType;
    private boolean revengeArmorEnchanted;
    private List<String> revengeArmorEnchantments;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Load configuration values
        loadConfig();
        
        // Clean up any existing boss bars from previous runs
        removeAllBossBars();
        
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("deathrevenge").setExecutor(this);
    }
    
    private void loadConfig() {
        revengeTime = getConfig().getInt("revenge-time", 30);
        revengeFailBanDuration = getConfig().getInt("ban-durations.revenge-fail", 60);
        noPlayersBanDuration = getConfig().getInt("ban-durations.no-players", 30);
        
        // Load sword configuration
        String swordType = getConfig().getString("revenge-sword.type", "DIAMOND_SWORD");
        try {
            revengeSwordType = Material.valueOf(swordType);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sword type in config: " + swordType + ". Using DIAMOND_SWORD instead.");
            revengeSwordType = Material.DIAMOND_SWORD;
        }
        
        revengeSwordEnchanted = getConfig().getBoolean("revenge-sword.enchanted", true);
        revengeSwordEnchantments = getConfig().getStringList("revenge-sword.enchantments");
        
        // Load armor configuration
        revengeArmorEnabled = getConfig().getBoolean("revenge-armor.enabled", true);
        
        String helmetType = getConfig().getString("revenge-armor.helmet", "CHAINMAIL_HELMET");
        try {
            revengeHelmetType = Material.valueOf(helmetType);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid helmet type in config: " + helmetType + ". Using CHAINMAIL_HELMET instead.");
            revengeHelmetType = Material.CHAINMAIL_HELMET;
        }
        
        String chestplateType = getConfig().getString("revenge-armor.chestplate", "CHAINMAIL_CHESTPLATE");
        try {
            revengeChestplateType = Material.valueOf(chestplateType);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid chestplate type in config: " + chestplateType + ". Using CHAINMAIL_CHESTPLATE instead.");
            revengeChestplateType = Material.CHAINMAIL_CHESTPLATE;
        }
        
        String leggingsType = getConfig().getString("revenge-armor.leggings", "NONE");
        try {
            revengeLeggingsType = Material.valueOf(leggingsType);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid leggings type in config: " + leggingsType + ". Using AIR instead.");
            revengeLeggingsType = Material.AIR;
        }
        
        String bootsType = getConfig().getString("revenge-armor.boots", "NONE");
        try {
            revengeBootsType = Material.valueOf(bootsType);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid boots type in config: " + bootsType + ". Using AIR instead.");
            revengeBootsType = Material.AIR;
        }
        
        revengeArmorEnchanted = getConfig().getBoolean("revenge-armor.enchanted", true);
        revengeArmorEnchantments = getConfig().getStringList("revenge-armor.enchantments");
    }

    private ItemStack createRevengeSword() {
        ItemStack sword = new ItemStack(revengeSwordType);
        
        if (revengeSwordEnchanted) {
            for (String enchantString : revengeSwordEnchantments) {
                try {
                    String[] parts = enchantString.split(":");
                    if (parts.length == 2) {
                        Enchantment enchantment = Enchantment.getByName(parts[0]);
                        int level = Integer.parseInt(parts[1]);
                        if (enchantment != null) {
                            sword.addUnsafeEnchantment(enchantment, level);
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("Invalid enchantment format: " + enchantString);
                }
            }
        }
        
        return sword;
    }

    private ItemStack createRevengeArmor(Material type) {
        if (type == Material.AIR) {
            return null;
        }
        
        ItemStack armor = new ItemStack(type);
        
        if (revengeArmorEnchanted) {
            for (String enchantString : revengeArmorEnchantments) {
                try {
                    String[] parts = enchantString.split(":");
                    if (parts.length == 2) {
                        Enchantment enchantment = Enchantment.getByName(parts[0]);
                        int level = Integer.parseInt(parts[1]);
                        if (enchantment != null) {
                            armor.addUnsafeEnchantment(enchantment, level);
                        }
                    }
                } catch (Exception e) {
                    getLogger().warning("Invalid enchantment format: " + enchantString);
                }
            }
        }
        
        return armor;
    }

    private void removeRevengeItems(Player player) {
        List<ItemStack> items = revengeItems.remove(player.getUniqueId());
        if (items != null) {
            // Remove all revenge items from inventory
            for (ItemStack item : items) {
                if (item != null) {
                    player.getInventory().remove(item);
                }
            }
            
            // Clear armor slots if they were used
            if (revengeArmorEnabled) {
                if (revengeHelmetType != Material.AIR) {
                    player.getInventory().setHelmet(null);
                }
                if (revengeChestplateType != Material.AIR) {
                    player.getInventory().setChestplate(null);
                }
                if (revengeLeggingsType != Material.AIR) {
                    player.getInventory().setLeggings(null);
                }
                if (revengeBootsType != Material.AIR) {
                    player.getInventory().setBoots(null);
                }
            }
        }
    }

    private void removeAllBossBars() {
        // Remove all boss bars from the server
        Iterator<KeyedBossBar> iterator = Bukkit.getBossBars();
        while (iterator.hasNext()) {
            KeyedBossBar bossBar = iterator.next();
            bossBar.removeAll();
            Bukkit.removeBossBar(bossBar.getKey());
        }
        revengeBossBars.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("deathrevenge")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("clearbars") && sender.hasPermission("deathrevenge.clearbars")) {
                // Remove all boss bars from the server
                removeAllBossBars();
                
                // Also cancel any running tasks
                for (RevengeTask task : revengeTasks.values()) {
                    if (task != null) {
                        task.cancel();
                    }
                }
                revengeTasks.clear();
                
                sender.sendMessage("§aAll boss bars have been cleared from the server!");
                return true;
            }
            
            sender.sendMessage("§6§lDeathRevenge Help");
            sender.sendMessage("§eWhen you die, you will be given a chance for revenge!");
            sender.sendMessage("§eYou will be teleported to a random player and have " + revengeTime + " seconds to kill them.");
            sender.sendMessage("§eYou will receive a " + revengeSwordType.name().toLowerCase().replace("_", " ") + " for your revenge!");
            if (revengeArmorEnabled) {
                StringBuilder armorMessage = new StringBuilder("§eYou will also receive ");
                List<String> armorPieces = new ArrayList<>();
                
                if (revengeHelmetType != Material.AIR) {
                    armorPieces.add(revengeHelmetType.name().toLowerCase().replace("_", " "));
                }
                if (revengeChestplateType != Material.AIR) {
                    armorPieces.add(revengeChestplateType.name().toLowerCase().replace("_", " "));
                }
                if (revengeLeggingsType != Material.AIR) {
                    armorPieces.add(revengeLeggingsType.name().toLowerCase().replace("_", " "));
                }
                if (revengeBootsType != Material.AIR) {
                    armorPieces.add(revengeBootsType.name().toLowerCase().replace("_", " "));
                }
                
                if (!armorPieces.isEmpty()) {
                    armorMessage.append(String.join(", ", armorPieces));
                    armorMessage.append(" to mark you as a revenge seeker!");
                    sender.sendMessage(armorMessage.toString());
                }
            }
            sender.sendMessage("§cIf you fail to kill your target, you will be banned for " + revengeFailBanDuration + " minutes!");
            sender.sendMessage("§aIf you succeed, you will be teleported back to your death location.");
            sender.sendMessage("§eIf no players are online when you die, you will be banned for " + noPlayersBanDuration + " minutes.");
            sender.sendMessage("§6§lCommands:");
            sender.sendMessage("§e/deathrevenge clearbars §7- Remove all revenge boss bars (requires permission)");
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
            
            // Remove the revenge items
            removeRevengeItems(victim);
            
            // Ban the player for configured duration
            Bukkit.getScheduler().runTask(this, () -> 
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                    victim.getName(),
                    "Failed revenge attempt",
                    new Date(System.currentTimeMillis() + (revengeFailBanDuration * 60000L)), // Convert minutes to milliseconds
                    null
                )
            );
            victim.kickPlayer("You failed your revenge attempt. You are banned for " + revengeFailBanDuration + " minutes!");
            return;
        }

        // Don't give revenge if the player was killed by someone in revenge mode
        if (killer != null && revengeTasks.containsKey(killer.getUniqueId())) {
            victim.sendMessage("§cYou were killed by a revenge seeker. No revenge for you!");
            return;
        }

        // Store death location
        deathLocations.put(victim.getUniqueId(), victim.getLocation());

        // Find a random player that isn't the victim
        List<Player> possibleTargets = new ArrayList<>(Bukkit.getOnlinePlayers());
        possibleTargets.remove(victim);
        if (possibleTargets.isEmpty()) {
            // Ban the player for configured duration when no other players are online
            Bukkit.getScheduler().runTask(this, () -> 
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                    victim.getName(),
                    "No players available for revenge",
                    new Date(System.currentTimeMillis() + (noPlayersBanDuration * 60000L)), // Convert minutes to milliseconds
                    null
                )
            );
            victim.kickPlayer("No players available for revenge. You are banned for " + noPlayersBanDuration + " minutes!");
            return;
        }

        Player randomTarget = possibleTargets.get(new Random().nextInt(possibleTargets.size()));
        pendingRevengeTargets.put(victim.getUniqueId(), randomTarget);
        
        // Start warning countdown for the hunter
        targetWarnings.put(victim.getUniqueId(), 5);
        new BukkitRunnable() {
            @Override
            public void run() {
                int warning = targetWarnings.get(victim.getUniqueId());
                if (warning > 0) {
                    victim.sendTitle(
                        "§c§lPREPARE FOR REVENGE!",
                        "§eYou will be teleported in " + warning + "...",
                        0, 20, 0
                    );
                    targetWarnings.put(victim.getUniqueId(), warning - 1);
                } else {
                    targetWarnings.remove(victim.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L); // Run every second
        
        victim.sendMessage("§cYou will be teleported to a random player for revenge when you respawn!");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // First, check if this player has any revenge items and remove them
        removeRevengeItems(player);
        
        // Then check if they have a pending revenge
        Player target = pendingRevengeTargets.remove(player.getUniqueId());
        
        if (target != null && target.isOnline()) {
            // Start a countdown before teleporting
            respawnCountdowns.put(player.getUniqueId(), 5); // 5 seconds countdown
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    int countdown = respawnCountdowns.get(player.getUniqueId());
                    if (countdown > 0) {
                        player.sendTitle(
                            "§c§lGET READY!",
                            "§eTeleporting in " + countdown + "...",
                            0, 20, 0
                        );
                        respawnCountdowns.put(player.getUniqueId(), countdown - 1);
                    } else {
                        respawnCountdowns.remove(player.getUniqueId());
                        this.cancel();
                        
                        // Now teleport and give items
                        player.teleport(target.getLocation());
                        
                        // Give the revenge items
                        List<ItemStack> items = new ArrayList<>();
                        
                        // Give sword
                        ItemStack revengeSword = createRevengeSword();
                        player.getInventory().addItem(revengeSword);
                        items.add(revengeSword);
                        
                        // Give armor if enabled
                        if (revengeArmorEnabled) {
                            List<String> armorPieces = new ArrayList<>();
                            
                            ItemStack helmet = createRevengeArmor(revengeHelmetType);
                            if (helmet != null) {
                                player.getInventory().setHelmet(helmet);
                                items.add(helmet);
                                armorPieces.add(helmet.getType().name().toLowerCase().replace("_", " "));
                            }
                            
                            ItemStack chestplate = createRevengeArmor(revengeChestplateType);
                            if (chestplate != null) {
                                player.getInventory().setChestplate(chestplate);
                                items.add(chestplate);
                                armorPieces.add(chestplate.getType().name().toLowerCase().replace("_", " "));
                            }
                            
                            ItemStack leggings = createRevengeArmor(revengeLeggingsType);
                            if (leggings != null) {
                                player.getInventory().setLeggings(leggings);
                                items.add(leggings);
                                armorPieces.add(leggings.getType().name().toLowerCase().replace("_", " "));
                            }
                            
                            ItemStack boots = createRevengeArmor(revengeBootsType);
                            if (boots != null) {
                                player.getInventory().setBoots(boots);
                                items.add(boots);
                                armorPieces.add(boots.getType().name().toLowerCase().replace("_", " "));
                            }
                            
                            if (!armorPieces.isEmpty()) {
                                player.sendMessage("§eYou have been given " + String.join(", ", armorPieces) + " to mark you as a revenge seeker!");
                            }
                        }
                        
                        revengeItems.put(player.getUniqueId(), items);
                        
                        player.sendMessage("§cYou have " + revengeTime + " seconds to kill your target for revenge!");
                        player.sendMessage("§eYou have been given a " + revengeSwordType.name().toLowerCase().replace("_", " ") + " for your revenge!");

                        // Create and start the revenge task
                        RevengeTask revengeTask = new RevengeTask(player, target);
                        revengeTasks.put(player.getUniqueId(), revengeTask);
                    }
                }
            }.runTaskTimer(this, 0L, 20L); // Run every second
        }
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && revengeTasks.containsKey(killer.getUniqueId())) {
            RevengeTask task = revengeTasks.get(killer.getUniqueId());
            
            // Check if the killed player was the revenge target
            if (victim.getUniqueId().equals(task.getTargetUUID())) {
                // Cancel and remove the task first to prevent any race conditions
                task.cancel();
                revengeTasks.remove(killer.getUniqueId());
                
                // Remove the revenge items
                removeRevengeItems(killer);
                
                // Remove boss bar
                task.bossBar.removeAll();
                revengeBossBars.remove(killer.getUniqueId());
                
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
        private final BossBar bossBar;
        private int timeLeft;
        private final long startTime;
        private boolean isCancelled = false;

        public RevengeTask(Player player, Player target) {
            this.playerUUID = player.getUniqueId();
            this.targetUUID = target.getUniqueId();
            this.timeLeft = revengeTime;
            this.startTime = System.currentTimeMillis();
            
            // Create boss bar
            this.bossBar = Bukkit.createBossBar(
                "§cRevenge Time: " + timeLeft + "s",
                BarColor.RED,
                BarStyle.SOLID
            );
            
            // Show boss bar to all players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                this.bossBar.addPlayer(onlinePlayer);
            }
            
            // Update boss bar progress
            this.bossBar.setProgress(1.0);
            
            // Store the boss bar
            revengeBossBars.put(playerUUID, bossBar);
            
            // Start the task to run every tick
            this.runTaskTimer(DeathRevenge.this, 0L, 1L);
        }

        @Override
        public void run() {
            if (isCancelled) {
                return;
            }

            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                // Calculate time left based on actual elapsed time
                long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                timeLeft = Math.max(0, revengeTime - (int)elapsedTime);
                
                // Update boss bar
                double progress = (double) timeLeft / revengeTime;
                bossBar.setProgress(progress);
                bossBar.setTitle("§cRevenge Time: " + timeLeft + "s");
                
                if (timeLeft <= 0) {
                    // Remove the revenge items
                    removeRevengeItems(player);
                    
                    // Remove boss bar
                    bossBar.removeAll();
                    revengeBossBars.remove(playerUUID);
                    
                    // Teleport player to their spawnpoint before banning
                    Location spawnLocation = player.getBedSpawnLocation();
                    if (spawnLocation == null) {
                        spawnLocation = player.getWorld().getSpawnLocation();
                    }
                    Location finalSpawnLocation = spawnLocation;
                    
                    // Time's up - teleport and ban the player
                    Bukkit.getScheduler().runTask(DeathRevenge.this, () -> {
                        player.teleport(finalSpawnLocation);
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                            player.getName(),
                            "Failed revenge attempt",
                            new Date(System.currentTimeMillis() + (revengeFailBanDuration * 60000L)), // Convert minutes to milliseconds
                            null
                        );
                        player.kickPlayer("Time's up! You failed your revenge attempt. You are banned for " + revengeFailBanDuration + " minutes!");
                    });
                
                    // Cancel this task
                    this.cancel();
                    revengeTasks.remove(playerUUID);
                    deathLocations.remove(playerUUID);
                }
            } else {
                // Player went offline, clean up
                bossBar.removeAll();
                revengeBossBars.remove(playerUUID);
                this.cancel();
                revengeTasks.remove(playerUUID);
                deathLocations.remove(playerUUID);
            }
        }

        @Override
        public void cancel() {
            isCancelled = true;
            super.cancel();
            revengeTasks.remove(playerUUID);
            deathLocations.remove(playerUUID);
        }

        public UUID getTargetUUID() {
            return targetUUID;
        }
    }

    @Override
    public void onDisable() {
        // Clean up all boss bars when plugin is disabled
        for (BossBar bossBar : revengeBossBars.values()) {
            if (bossBar != null) {
                bossBar.removeAll();
            }
        }
        revengeBossBars.clear();
        
        // Also clean up any running tasks
        for (RevengeTask task : revengeTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        revengeTasks.clear();
    }
} 