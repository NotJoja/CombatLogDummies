package io.github.notjoja.combatlogdummies.dummy;

import io.github.notjoja.combatlogdummies.CombatLogDummies;
import io.github.notjoja.combatlogdummies.hook.AxGravesHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DummyManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CombatLogDummies plugin;
    private final NamespacedKey ownerKey;
    private final Map<UUID, DummyRecord> records = new HashMap<>();
    private final File file;

    public DummyManager(CombatLogDummies plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "dummy_owner");
        this.file = new File(plugin.getDataFolder(), "dummies.yml");
        load();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireTick, 20L, 20L);
    }

    public NamespacedKey ownerKey() {
        return ownerKey;
    }

    public DummyRecord get(UUID owner) {
        return records.get(owner);
    }

    public UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------- spawn

    public void spawn(Player player, long remainingMillis) {
        remove(player.getUniqueId()); // one dummy per player

        DummyRecord r = new DummyRecord(player.getUniqueId());
        r.name = player.getName();
        r.location = player.getLocation().clone();
        r.health = player.getHealth();
        r.expiryMillis = System.currentTimeMillis() + remainingMillis;
        r.contents = player.getInventory().getContents().clone();
        r.xp = player.calculateTotalExperiencePoints();

        AttributeInstance maxAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? 20 : maxAttr.getValue();
        PlayerInventoryView inv = new PlayerInventoryView(player);

        Zombie z = r.location.getWorld().spawn(r.location, Zombie.class, zombie -> {
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setAware(false);
            zombie.setCanPickupItems(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.setPersistent(true);
            AttributeInstance zmax = zombie.getAttribute(Attribute.MAX_HEALTH);
            if (zmax != null) zmax.setBaseValue(max);
            zombie.setHealth(Math.max(0.5, Math.min(r.health, max)));
            zombie.customName(name(player.getName()));
            zombie.setCustomNameVisible(true);
            zombie.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());

            EntityEquipment eq = zombie.getEquipment();
            eq.setHelmet(inv.helmet);
            eq.setChestplate(inv.chest);
            eq.setLeggings(inv.legs);
            eq.setBoots(inv.boots);
            eq.setItemInMainHand(inv.main);
            eq.setItemInOffHand(inv.off);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                eq.setDropChance(slot, 0f);
            }
        });
        r.entityId = z.getUniqueId();
        r.state = DummyRecord.State.ALIVE;
        records.put(r.owner, r);
        save();
    }

    private Component name(String player) {
        return MM.deserialize(plugin.settings().dummyNameFormat.replace("{player}", player));
    }

    private record PlayerInventoryView(ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots,
                                       ItemStack main, ItemStack off) {
        PlayerInventoryView(Player p) {
            this(p.getInventory().getHelmet(), p.getInventory().getChestplate(), p.getInventory().getLeggings(),
                    p.getInventory().getBoots(), p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand());
        }
    }

    // ---------------------------------------------------------------- lookup / removal

    /** Finds the zombie of a record, loading its chunk if necessary. */
    public Zombie findEntity(DummyRecord r) {
        if (r.entityId == null || r.location == null || r.location.getWorld() == null) return null;
        Entity e = Bukkit.getEntity(r.entityId);
        if (e == null) {
            r.location.getChunk(); // sync-load chunk + entities
            e = Bukkit.getEntity(r.entityId);
        }
        return e instanceof Zombie z ? z : null;
    }

    /** Removes the dummy entity + record entirely. */
    public void remove(UUID owner) {
        DummyRecord r = records.remove(owner);
        if (r == null) return;
        Zombie z = findEntity(r);
        if (z != null) z.remove();
        save();
    }

    /** Despawns the entity, keeps the record (state EXPIRED) so the owner can resume at that spot. */
    private void expire(DummyRecord r) {
        Zombie z = findEntity(r);
        if (z != null) {
            r.health = z.getHealth();
            r.location = z.getLocation();
            z.remove();
        }
        r.state = DummyRecord.State.EXPIRED;
        save();
    }

    private void expireTick() {
        long now = System.currentTimeMillis();
        for (DummyRecord r : new ArrayList<>(records.values())) {
            if (r.state == DummyRecord.State.ALIVE && r.expiryMillis <= now) expire(r);
        }
    }

    /** Resets the dummy's (i.e. its owner's) combat timer back to full. */
    public void refresh(UUID owner) {
        DummyRecord r = records.get(owner);
        if (r == null || r.state != DummyRecord.State.ALIVE) return;
        r.expiryMillis = System.currentTimeMillis() + plugin.settings().durationSeconds * 1000L;
        save();
    }

    // ---------------------------------------------------------------- death

    public void handleDeath(Zombie zombie, UUID owner) {
        DummyRecord r = records.get(owner);
        if (r == null || r.state != DummyRecord.State.ALIVE) return;
        r.state = DummyRecord.State.DEAD;
        r.location = zombie.getLocation();
        r.health = 0;
        save();

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack i : r.contents) {
            if (i != null && !i.getType().isAir()) items.add(i);
        }
        Location loc = r.location;

        if (plugin.settings().useAxGraves && Bukkit.getPluginManager().isPluginEnabled("AxGraves")) {
            try {
                AxGravesHook.createGrave(loc, Bukkit.getOfflinePlayer(owner), items, r.xp);
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("AxGraves hook failed, dropping items instead: " + t);
            }
        }
        World w = loc.getWorld();
        for (ItemStack i : items) w.dropItemNaturally(loc, i);
        int orb = Math.min(r.xp, 100);
        if (orb > 0) w.spawn(loc, ExperienceOrb.class, o -> o.setExperience(orb));
    }

    // ---------------------------------------------------------------- persistence

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            try {
                DummyRecord r = new DummyRecord(UUID.fromString(key));
                r.name = s.getString("name");
                r.location = s.getLocation("location");
                r.health = s.getDouble("health");
                r.expiryMillis = s.getLong("expiry");
                List<?> list = s.getList("contents", List.of());
                r.contents = list.stream().map(o -> o instanceof ItemStack i ? i : null).toArray(ItemStack[]::new);
                r.xp = s.getInt("xp");
                String eid = s.getString("entity");
                r.entityId = eid == null ? null : UUID.fromString(eid);
                r.state = DummyRecord.State.valueOf(s.getString("state", "ALIVE"));
                records.put(r.owner, r);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping corrupt dummy record " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (DummyRecord r : records.values()) {
            String k = r.owner.toString();
            y.set(k + ".name", r.name);
            y.set(k + ".location", r.location);
            y.set(k + ".health", r.health);
            y.set(k + ".expiry", r.expiryMillis);
            y.set(k + ".contents", java.util.Arrays.asList(r.contents));
            y.set(k + ".xp", r.xp);
            y.set(k + ".entity", r.entityId == null ? null : r.entityId.toString());
            y.set(k + ".state", r.state.name());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save dummies.yml: " + ex.getMessage());
        }
    }
}
