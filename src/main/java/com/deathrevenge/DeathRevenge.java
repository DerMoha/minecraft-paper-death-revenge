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
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Particle;

import java.util.*;

public class DeathRevenge extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, RevengeTask> revengeTasks = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, List<ItemStack>> revengeItems = new HashMap<>();
    private final Map<UUID, Player> pendingRevengeTargets = new HashMap<>();
    private final Map<UUID, Integer> respawnCountdowns = new HashMap<>();
    private final Map<UUID, BossBar> revengeBossBars = new HashMap<>();
    private final Map<UUID, Integer> targetWarnings = new HashMap<>();
    private final Map<UUID, Long> targetCooldowns = new HashMap<>();
    
    private int revengeTime;
    private int revengeFailBanDuration;
    private int noPlayersBanDuration;
    private int targetCooldown;
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
        targetCooldown = getConfig().getInt("target-cooldown", 300);
        
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
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("clearbars") && sender.hasPermission("deathrevenge.clearbars")) {
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
                } else if (args[0].equalsIgnoreCase("cooldown") && sender instanceof Player) {
                    Player player = (Player) sender;
                    Long cooldownEnd = targetCooldowns.get(player.getUniqueId());
                    if (cooldownEnd != null && cooldownEnd > System.currentTimeMillis()) {
                        long secondsLeft = (cooldownEnd - System.currentTimeMillis()) / 1000;
                        sender.sendMessage("§eYou cannot be targeted for revenge for " + secondsLeft + " more seconds.");
                    } else {
                        sender.sendMessage("§aYou can be targeted for revenge now!");
                    }
                    return true;
                }
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
            sender.sendMessage("§ePlayers cannot be targeted for revenge for " + targetCooldown + " seconds after being a target.");
            sender.sendMessage("§6§lCommands:");
            sender.sendMessage("§e/deathrevenge clearbars §7- Remove all revenge boss bars (requires permission)");
            sender.sendMessage("§e/deathrevenge cooldown §7- Check your revenge target cooldown status");
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
            
            // Play fail sound
            victim.playSound(victim.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            
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
            victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
            victim.sendMessage("§cYou were killed by a revenge seeker. No revenge for you!");
            return;
        }

        // Don't give revenge if the player was killed by someone who was their revenge target
        if (killer != null && revengeTasks.containsKey(victim.getUniqueId())) {
            RevengeTask task = revengeTasks.get(victim.getUniqueId());
            if (killer.getUniqueId().equals(task.getTargetUUID())) {
                victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
                victim.sendMessage("§cYou were killed by your revenge target. No revenge for you!");
                return;
            }
        }

        // Store death location
        deathLocations.put(victim.getUniqueId(), victim.getLocation());

        // Find a random player that isn't the victim and isn't in revenge mode
        List<Player> possibleTargets = new ArrayList<>(Bukkit.getOnlinePlayers());
        possibleTargets.remove(victim);
        possibleTargets.removeIf(p -> revengeTasks.containsKey(p.getUniqueId()));
        
        // Remove players who are on cooldown
        possibleTargets.removeIf(p -> {
            Long cooldownEnd = targetCooldowns.get(p.getUniqueId());
            return cooldownEnd != null && cooldownEnd > System.currentTimeMillis();
        });
        
        if (possibleTargets.isEmpty()) {
            // Play fail sound
            victim.playSound(victim.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            
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
        
        // Play revenge sound
        victim.playSound(victim.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.0f);
        
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
            
            // Play initial revenge sound
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.0f);
            
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
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        respawnCountdowns.put(player.getUniqueId(), countdown - 1);
                    } else {
                        respawnCountdowns.remove(player.getUniqueId());
                        this.cancel();
                        
                        // Play teleport sound
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        
                        // Now teleport and give items
                        player.teleport(target.getLocation());
                        
                        // Spawn teleport particles
                        spawnTeleportParticles(player, 50);
                        
                        // Apply blindness and slowness effects
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1, false, false)); // 3 seconds
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false)); // 3 seconds
                        
                        // Notify the hunter
                        player.sendTitle("§c§lBLINDED!", "§eYou have 3 seconds to recover!", 0, 60, 20);
                        
                        // Notify the target with particles
                        target.sendMessage("§cA revenge seeker has appeared nearby!");
                        target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        spawnWarningParticles(target, 30);
                        
                        target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        
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
                        
                        // Play item receive sound
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // If player was a revenge target, find new targets for their hunters
        for (Map.Entry<UUID, RevengeTask> entry : new HashMap<>(revengeTasks).entrySet()) {
            RevengeTask task = entry.getValue();
            if (task.getTargetUUID().equals(player.getUniqueId())) {
                // The target logged out, cancel the revenge
                Player hunter = Bukkit.getPlayer(entry.getKey());
                if (hunter != null) {
                    hunter.sendMessage("§cYour revenge target logged out. Revenge cancelled.");
                    hunter.playSound(hunter.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    spawnWarningParticles(hunter, 20); // Add particles for visual feedback
                }
                task.cancel();
                revengeTasks.remove(entry.getKey());
                revengeBossBars.remove(entry.getKey());
                deathLocations.remove(entry.getKey());
                removeRevengeItems(hunter);
                
                // Set cooldown for the target
                targetCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (targetCooldown * 1000L));
            }
        }
        
        // If player was in revenge mode, cancel their revenge
        if (revengeTasks.containsKey(player.getUniqueId())) {
            RevengeTask task = revengeTasks.get(player.getUniqueId());
            Player target = Bukkit.getPlayer(task.getTargetUUID());
            if (target != null) {
                target.sendMessage("§aYour revenge seeker has logged out. You're safe... for now.");
                target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 1.0f, 1.0f);
                spawnTeleportParticles(target, 20); // Add particles for visual feedback
            }
            task.cancel();
            revengeTasks.remove(player.getUniqueId());
            revengeBossBars.remove(player.getUniqueId());
            deathLocations.remove(player.getUniqueId());
            removeRevengeItems(player);
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
                getLogger().info("Revenge successful! " + killer.getName() + " killed their target " + victim.getName());
                
                // Play success sound
                killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                
                // Cancel and remove the task first to prevent any race conditions
                task.cancel();
                revengeTasks.remove(killer.getUniqueId());
                getLogger().info("Revenge task cancelled and removed for " + killer.getName());
                
                // Remove the revenge items
                removeRevengeItems(killer);
                getLogger().info("Revenge items removed from " + killer.getName());
                
                // Remove boss bar
                task.bossBar.removeAll();
                revengeBossBars.remove(killer.getUniqueId());
                getLogger().info("Boss bar removed for " + killer.getName());
                
                // Teleport back to death location and set health to 1
                Location deathLoc = deathLocations.get(killer.getUniqueId());
                if (deathLoc != null) {
                    killer.teleport(deathLoc);
                    deathLocations.remove(killer.getUniqueId());
                    getLogger().info(killer.getName() + " teleported back to death location");
                } else {
                    getLogger().warning("No death location found for " + killer.getName());
                }
                
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    killer.setHealth(1.0);
                    killer.setFoodLevel(20); // Give full food
                    killer.sendMessage("§aRevenge successful! You've been returned to your death location.");
                    getLogger().info("Health set to 1 and success message sent to " + killer.getName());
                }, 1L);
            } else {
                getLogger().info("Kill detected but not revenge target: " + killer.getName() + " killed " + victim.getName());
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

    private void spawnTeleportParticles(Player player, int count) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.END_ROD, loc, count, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, count/2, 0.5, 0.5, 0.5, 0.1);
    }

    private void spawnWarningParticles(Player player, int count) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.CLOUD, loc, count, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.FLAME, loc, count/2, 0.5, 0.5, 0.5, 0.1);
    }
} 