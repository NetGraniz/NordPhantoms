package com.nordfjell.nordphantoms;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class NordPhantomsPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, MovementState> movementStates = new HashMap<>();

    private NamespacedKey angryKey;
    private BukkitTask spawnTask;
    private BukkitTask movementTask;

    private boolean debug;
    private long spawnIntervalTicks;
    private double spawnChance;
    private int maximumNearbyPhantoms;
    private double phantomCountRadius;
    private double minimumSpawnDistance;
    private double maximumSpawnDistance;
    private int spawnAttempts;
    private int spawnHeight;
    private int baseCheckRadius;
    private long movementIntervalTicks;
    private int chorusAvoidanceRadius;
    private double stuckDistance;
    private int stuckChecks;

    @Override
    public void onEnable() {
        angryKey = new NamespacedKey(this, "angry");
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        startTasks();
        Bukkit.getScheduler().runTask(this, this::initializeLoadedPhantoms);
        getLogger().info("NordPhantoms enabled: Overworld spawning disabled; End phantoms are passive until attacked.");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        movementStates.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhantomSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Phantom phantom)) {
            return;
        }

        World.Environment environment = phantom.getWorld().getEnvironment();
        if (environment == World.Environment.NORMAL) {
            event.setCancelled(true);
            debug("Cancelled an Overworld phantom spawn at " + format(event.getLocation()));
            return;
        }

        if (environment == World.Environment.THE_END) {
            makePassive(phantom);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhantomTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Phantom phantom)
                || phantom.getWorld().getEnvironment() != World.Environment.THE_END
                || isAngry(phantom)) {
            return;
        }
        if (event.getTarget() instanceof Player) {
            event.setCancelled(true);
            phantom.setTarget(null);
            phantom.setSilent(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhantomDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Phantom phantom)
                || phantom.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }

        Player attacker = responsiblePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (!phantom.isValid() || phantom.isDead()) {
                return;
            }
            makeAngry(phantom, attacker);
        });
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Phantom phantom) {
                restoreState(phantom);
            }
        }
    }

    private Player responsiblePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void makePassive(Phantom phantom) {
        phantom.getPersistentDataContainer().set(angryKey, PersistentDataType.BYTE, (byte) 0);
        phantom.setTarget(null);
        phantom.setSilent(true);
    }

    private void makeAngry(Phantom phantom, Player attacker) {
        phantom.getPersistentDataContainer().set(angryKey, PersistentDataType.BYTE, (byte) 1);
        phantom.setSilent(false);
        phantom.setTarget(attacker);
        phantom.setAnchorLocation(attacker.getLocation());
        movementStates.remove(phantom.getUniqueId());
        debug("Phantom " + phantom.getUniqueId() + " was provoked by " + attacker.getName());
    }

    private boolean isAngry(Phantom phantom) {
        Byte value = phantom.getPersistentDataContainer().get(angryKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private void restoreState(Phantom phantom) {
        if (isAngry(phantom)) {
            phantom.setSilent(false);
        } else {
            makePassive(phantom);
        }
    }

    private void initializeLoadedPhantoms() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            for (Phantom phantom : world.getEntitiesByClass(Phantom.class)) {
                restoreState(phantom);
            }
        }
    }

    private void startTasks() {
        cancelTasks();
        spawnTask = Bukkit.getScheduler().runTaskTimer(
                this, this::runSpawnCycle, spawnIntervalTicks, spawnIntervalTicks);
        movementTask = Bukkit.getScheduler().runTaskTimer(
                this, this::runMovementCycle, movementIntervalTicks, movementIntervalTicks);
    }

    private void cancelTasks() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
    }

    private void runSpawnCycle() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isValid() || player.isDead()
                    || player.getWorld().getEnvironment() != World.Environment.THE_END
                    || random.nextDouble() > spawnChance) {
                continue;
            }
            if (countNearbyPhantoms(player.getLocation()) >= maximumNearbyPhantoms) {
                continue;
            }

            Location location = findNaturalSpawnLocation(player, random);
            if (location == null) {
                debug("No base-safe phantom spawn location found near " + player.getName());
                continue;
            }

            Phantom phantom = player.getWorld().spawn(location, Phantom.class,
                    spawned -> {
                        spawned.setPersistent(false);
                        spawned.setAnchorLocation(location);
                        makePassive(spawned);
                    });
            debug("Spawned passive phantom at " + format(phantom.getLocation()));
        }
    }

    private int countNearbyPhantoms(Location center) {
        int count = 0;
        for (Entity entity : center.getWorld().getNearbyEntities(
                center, phantomCountRadius, phantomCountRadius, phantomCountRadius,
                candidate -> candidate instanceof Phantom)) {
            if (entity instanceof Phantom) {
                count++;
            }
        }
        return count;
    }

    private Location findNaturalSpawnLocation(Player player, ThreadLocalRandom random) {
        World world = player.getWorld();
        Location origin = player.getLocation();

        for (int attempt = 0; attempt < spawnAttempts; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = random.nextDouble(minimumSpawnDistance, maximumSpawnDistance);
            int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);

            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }

            Block surface = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!isNaturalEndSurface(surface) || isNearBase(world, surface.getLocation())) {
                continue;
            }

            Location spawn = new Location(world, x + 0.5,
                    Math.min(world.getMaxHeight() - 4, surface.getY() + spawnHeight), z + 0.5);
            if (isClearSpawnVolume(spawn)) {
                return spawn;
            }
        }
        return null;
    }

    private boolean isNaturalEndSurface(Block surface) {
        Material type = surface.getType();
        if (type == Material.END_STONE) {
            return true;
        }
        if (type != Material.CHORUS_PLANT && type != Material.CHORUS_FLOWER) {
            return false;
        }

        Block cursor = surface;
        while (cursor.getY() > cursor.getWorld().getMinHeight()
                && (cursor.getType() == Material.CHORUS_PLANT
                || cursor.getType() == Material.CHORUS_FLOWER
                || cursor.getType().isAir())) {
            cursor = cursor.getRelative(0, -1, 0);
        }
        return cursor.getType() == Material.END_STONE;
    }

    private boolean isNearBase(World world, Location surface) {
        int minY = Math.max(world.getMinHeight(), surface.getBlockY() - 3);
        int maxY = Math.min(world.getMaxHeight() - 1, surface.getBlockY() + 10);
        int centerX = surface.getBlockX();
        int centerZ = surface.getBlockZ();

        for (int x = centerX - baseCheckRadius; x <= centerX + baseCheckRadius; x++) {
            for (int z = centerZ - baseCheckRadius; z <= centerZ + baseCheckRadius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    return true;
                }
                for (int y = minY; y <= maxY; y++) {
                    if (!isNaturalEndMaterial(world.getBlockAt(x, y, z).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNaturalEndMaterial(Material material) {
        return material.isAir()
                || material == Material.END_STONE
                || material == Material.CHORUS_PLANT
                || material == Material.CHORUS_FLOWER;
    }

    private boolean isClearSpawnVolume(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int ox = -2; ox <= 2; ox++) {
            for (int oy = -2; oy <= 2; oy++) {
                for (int oz = -2; oz <= 2; oz++) {
                    if (!world.getBlockAt(x + ox, y + oy, z + oz).getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void runMovementCycle() {
        Map<UUID, Boolean> seen = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            for (Phantom phantom : world.getEntitiesByClass(Phantom.class)) {
                if (!phantom.isValid() || phantom.isDead()) {
                    continue;
                }
                seen.put(phantom.getUniqueId(), Boolean.TRUE);
                steerAroundChorus(phantom);
                recoverIfStuck(phantom);
            }
        }

        Iterator<UUID> iterator = movementStates.keySet().iterator();
        while (iterator.hasNext()) {
            if (!seen.containsKey(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private void steerAroundChorus(Phantom phantom) {
        Location location = phantom.getLocation();
        Location nearest = findNearestChorus(location, chorusAvoidanceRadius);
        if (nearest == null) {
            return;
        }

        Vector away = location.toVector().subtract(nearest.toVector());
        away.setY(Math.max(0.35, away.getY()));
        if (away.lengthSquared() < 0.01) {
            away = new Vector(ThreadLocalRandom.current().nextDouble(-1.0, 1.0), 0.6,
                    ThreadLocalRandom.current().nextDouble(-1.0, 1.0));
        }
        away.normalize().multiply(0.55);
        Vector redirected = phantom.getVelocity().multiply(0.55).add(away);
        phantom.setVelocity(limitVelocity(redirected, 1.1));
        phantom.setAnchorLocation(location.clone().add(away.clone().multiply(14.0)).add(0, 8, 0));
    }

    private Location findNearestChorus(Location center, int radius) {
        World world = center.getWorld();
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material type = world.getBlockAt(cx + x, cy + y, cz + z).getType();
                    if (type != Material.CHORUS_PLANT && type != Material.CHORUS_FLOWER) {
                        continue;
                    }
                    Location candidate = new Location(world, cx + x + 0.5, cy + y + 0.5, cz + z + 0.5);
                    double distance = center.distanceSquared(candidate);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = candidate;
                    }
                }
            }
        }
        return nearest;
    }

    private void recoverIfStuck(Phantom phantom) {
        UUID id = phantom.getUniqueId();
        Location current = phantom.getLocation();
        MovementState previous = movementStates.get(id);
        if (previous == null) {
            movementStates.put(id, new MovementState(current, 0));
            return;
        }

        double distanceSquared = previous.location().getWorld() == current.getWorld()
                ? previous.location().distanceSquared(current)
                : Double.MAX_VALUE;
        int checks = distanceSquared < stuckDistance * stuckDistance ? previous.stuckChecks() + 1 : 0;
        if (checks >= stuckChecks) {
            Vector escape = new Vector(
                    ThreadLocalRandom.current().nextDouble(-0.35, 0.35),
                    0.85,
                    ThreadLocalRandom.current().nextDouble(-0.35, 0.35));
            phantom.setVelocity(escape);
            phantom.setAnchorLocation(current.clone().add(escape.clone().multiply(18.0)).add(0, 10, 0));
            checks = 0;
            debug("Redirected a stuck phantom at " + format(current));
        }
        movementStates.put(id, new MovementState(current, checks));
    }

    private Vector limitVelocity(Vector vector, double maximum) {
        if (vector.lengthSquared() > maximum * maximum) {
            return vector.normalize().multiply(maximum);
        }
        return vector;
    }

    private void loadSettings() {
        reloadConfig();
        debug = getConfig().getBoolean("debug", false);
        spawnIntervalTicks = clamp(getConfig().getLong("spawn.interval-ticks", 200L), 20L, 72000L);
        spawnChance = clamp(getConfig().getDouble("spawn.chance", 1.0), 0.0, 1.0);
        maximumNearbyPhantoms = (int) clamp(getConfig().getLong("spawn.maximum-near-player", 8), 1, 128);
        phantomCountRadius = clamp(getConfig().getDouble("spawn.count-radius", 128.0), 16.0, 512.0);
        minimumSpawnDistance = clamp(getConfig().getDouble("spawn.minimum-distance", 48.0), 8.0, 512.0);
        maximumSpawnDistance = clamp(getConfig().getDouble("spawn.maximum-distance", 96.0),
                minimumSpawnDistance + 1.0, 1024.0);
        spawnAttempts = (int) clamp(getConfig().getLong("spawn.attempts", 16), 1, 64);
        spawnHeight = (int) clamp(getConfig().getLong("spawn.height-above-surface", 18), 8, 64);
        baseCheckRadius = (int) clamp(getConfig().getLong("spawn.base-check-radius", 8), 1, 24);
        movementIntervalTicks = clamp(getConfig().getLong("movement.interval-ticks", 100L), 20L, 1200L);
        chorusAvoidanceRadius = (int) clamp(getConfig().getLong("movement.chorus-radius", 3), 1, 8);
        stuckDistance = clamp(getConfig().getDouble("movement.stuck-distance", 1.0), 0.1, 16.0);
        stuckChecks = (int) clamp(getConfig().getLong("movement.stuck-checks", 3), 1, 20);
    }

    private long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void debug(String message) {
        if (debug) {
            getLogger().info(message);
        }
    }

    private String format(Location location) {
        return location.getWorld().getName() + " "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            startTasks();
            sender.sendMessage("NordPhantoms configuration reloaded.");
        } else {
            sender.sendMessage("Usage: /nordphantoms reload");
        }
        return true;
    }

    private record MovementState(Location location, int stuckChecks) {
    }
}
