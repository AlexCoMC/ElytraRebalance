package com.alexcomc.elytrarebalance.elytra;

import com.alexcomc.elytrarebalance.ElytraRebalance;
import com.alexcomc.elytrarebalance.util.ColorUtil;
import com.alexcomc.elytrarebalance.util.DurationUtil;
import com.alexcomc.elytrarebalance.util.TemplateUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Módulo elytra-limit completo: estado por jugador, lógica y listener,
 * todo en un solo archivo para que sea fácil de leer y mantener.
 *
 * Reglas:
 * - Un golpe NORMAL (espada, flecha, cualquier cosa que no sea especial)
 *   marca al jugador durante "tag-time" (3 min por defecto) pero NO da ni
 *   quita tiempo de vuelo.
 * - Si el jugador no tenía marca activa (primera vez, o su marca anterior
 *   expiró del todo), el golpe normal SÍ le da el tiempo de vuelo a tope
 *   (flight-time, 5s por defecto), como si fuera "recién marcado".
 * - Un golpe ESPECIAL (mace-smash o spear "charge attack" con click derecho
 *   mantenido) resetea el tiempo de vuelo a tope, sin importar si ya tenía
 *   marca activa o no.
 * - El jab normal de la lanza (1 click) también cuenta como golpe ESPECIAL
 *   (igual que el charge attack), y resetea el tiempo de vuelo a tope. Esto
 *   aplica a cualquier tier de spear (wooden, stone, copper, iron, golden,
 *   diamond, netherite) por el nombre del Material.
 * - El smash del mazo se detecta primero por el DamageType "mace_smash" que
 *   reporta el juego; si por lo que sea esta build de Paper no lo expone,
 *   hay un respaldo: mazo en mano + el jugador venía cayendo al menos
 *   "special-attack-fall-threshold" bloques (1.5 por defecto, igual que el
 *   umbral vanilla del smash). Con "debug-special-attack: true" en el
 *   config se puede ver en consola qué está detectando cada golpe.
 * - Mientras el jugador está gliding, el tiempo de vuelo se consume; llegado
 *   a 0 se le fuerza a dejar de planear.
 * - Tras "grace-time" de inactividad (sin golpear ni ser golpeado) el tiempo
 *   de vuelo regenera poco a poco: "regen-amount" cada "regen-interval".
 * - Afecta a todos los jugadores, incluidos operadores, salvo que tengan el
 *   permiso "elytrarebalance.elytralimit.bypass" concedido explícitamente.
 */
public class ElytraLimit implements Listener {

    private static final String PLACEHOLDER = "%tiempo%";
    private static final long TICK_MILLIS = 50L;

    private static class State {
        long tagEndMillis;
        long lastActionMillis;
        double flightRemainingSeconds;
        long regenAccumulatorMillis;
    }

    private final ElytraRebalance plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    private double tagTimeSeconds;
    private double graceTimeSeconds;
    private double regenIntervalSeconds;
    private double regenAmountSeconds;
    private double flightMaxSeconds;
    private double minTimeToFlySeconds;
    private double specialAttackResetSeconds;
    private double specialAttackFallThreshold;
    private boolean debugSpecialAttack;
    private String actionbarTemplate = PLACEHOLDER;

    private Set<String> specialAttackExcludedEntities = new HashSet<>();

    private final DamageType maceSmashType;

    private BukkitTask tickTask;

    public ElytraLimit(ElytraRebalance plugin) {
        this.plugin = plugin;
        this.maceSmashType = lookupDamageType("mace_smash");
        applyConfig();
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private static DamageType lookupDamageType(String key) {
        try {
            return Registry.DAMAGE_TYPE.get(NamespacedKey.minecraft(key));
        } catch (Throwable t) {
            return null;
        }
    }

    public void applyConfig() {
        FileConfiguration config = plugin.getConfig();
        this.tagTimeSeconds = DurationUtil.parseToSeconds(config.getString("elytra-limit.tag-time", "3m"), 180);
        this.graceTimeSeconds = DurationUtil.parseToSeconds(config.getString("elytra-limit.grace-time", "2m"), 120);
        this.regenIntervalSeconds = DurationUtil.parseToSeconds(config.getString("elytra-limit.regen-interval", "45s"), 45);
        this.regenAmountSeconds = DurationUtil.parseToSeconds(config.getString("elytra-limit.regen-amount", "1s"), 1);
        this.flightMaxSeconds = DurationUtil.parseToSeconds(config.getString("elytra-limit.flight-time", "5s"), 5);
        this.minTimeToFlySeconds = DurationUtil.parseToSeconds(config.getString("elytra-limit.min-time-to-fly", "1s"), 1);
        this.specialAttackResetSeconds = DurationUtil.parseToSeconds(
                config.getString("elytra-limit.special-attack-reset-time", "5s"), flightMaxSeconds);
        this.specialAttackFallThreshold = config.getDouble("elytra-limit.special-attack-fall-threshold", 1.5);
        this.debugSpecialAttack = config.getBoolean("elytra-limit.debug-special-attack", false);
        this.actionbarTemplate = config.getString("elytra-limit.actionbar", PLACEHOLDER);

        Set<String> excluded = new HashSet<>();
        for (String raw : config.getStringList("elytra-limit.special-attack-excluded")) {
            if (raw == null || raw.isBlank()) continue;
            excluded.add(raw.trim().toLowerCase(Locale.ROOT));
        }
        this.specialAttackExcludedEntities = excluded;
    }

    // ================= EVENTOS =================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null) return;
        if (attacker.hasPermission("elytrarebalance.elytralimit.bypass")) return;

        // Golpear a otro jugador: siempre cuenta.
        if (event.getEntity() instanceof Player victim) {
            if (attacker.equals(victim)) return;
            applyHit(attacker, isSpecialAttack(event, attacker));
            return;
        }

        // Golpear a una entidad que no es jugador: solo cuenta si es un
        // ataque especial (mace-smash / spear-charge / spear normal si el
        // módulo "spear-always-special" está activo) y la entidad no está
        // excluida (ej. armor_stand, boat).
        if (isExcludedEntity(event.getEntity())) return;
        if (isSpecialAttack(event, attacker)) {
            applyHit(attacker, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.hasPermission("elytrarebalance.elytralimit.bypass")) return;

        if (!canFly(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean isSpecialAttack(EntityDamageByEntityEvent event, Player attacker) {
        String observedKey = null;
        boolean special = false;

        try {
            DamageType type = event.getDamageSource().getDamageType();
            observedKey = type.getKey().getKey();
            if (maceSmashType != null && type == maceSmashType) {
                special = true;
            } else if ("mace_smash".equals(observedKey)) {
                special = true;
            }
        } catch (Throwable ignored) {
            // El registro no expuso el DamageType en esta build; seguimos
            // con los chequeos de respaldo de abajo.
        }

        boolean holdingMace = isHoldingMace(attacker);
        boolean holdingSpear = isHoldingSpear(attacker);

        // Respaldo para el mazo: si el juego no reportó "mace_smash" (por
        // ejemplo, por diferencias de esta build de Paper) pero el jugador
        // tiene un mazo en mano y venía cayendo lo suficiente como para que
        // el propio juego arme el smash (umbral vanilla: 1.5 bloques),
        // lo tratamos igual como especial.
        if (!special && holdingMace && attacker.getFallDistance() >= specialAttackFallThreshold) {
            special = true;
        }

        // Cualquier golpe con spear (cualquier tier, jab o charge) cuenta
        // siempre como especial, igual que el smash del mazo.
        if (!special && holdingSpear) {
            special = true;
        }

        if (debugSpecialAttack) {
            plugin.getLogger().info("[ERB-debug] " + attacker.getName()
                    + " damageType=" + observedKey
                    + " mainHand=" + attacker.getInventory().getItemInMainHand().getType()
                    + " fallDistance=" + attacker.getFallDistance()
                    + " especial=" + special);
        }

        return special;
    }

    private boolean isHoldingMace(Player attacker) {
        return attacker.getInventory().getItemInMainHand().getType() == Material.MACE;
    }

    private boolean isHoldingSpear(Player attacker) {
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        return mainHand != null && mainHand.getType().name().endsWith("_SPEAR");
    }

    private boolean isExcludedEntity(Entity entity) {
        String typeName = entity.getType().name().toLowerCase(Locale.ROOT);
        if (specialAttackExcludedEntities.contains(typeName)) return true;
        if (entity instanceof ArmorStand && specialAttackExcludedEntities.contains("armor_stand")) return true;
        if (entity instanceof Boat && specialAttackExcludedEntities.contains("boat")) return true;
        return false;
    }

    // ================= LÓGICA =================

    private void applyHit(Player attacker, boolean special) {
        long now = System.currentTimeMillis();
        UUID uuid = attacker.getUniqueId();
        State state = states.get(uuid);
        boolean isNewOrExpired = (state == null) || (state.tagEndMillis <= now);

        if (state == null) {
            state = new State();
            states.put(uuid, state);
        }

        state.tagEndMillis = now + Math.round(tagTimeSeconds * 1000.0);
        state.lastActionMillis = now;

        if (special) {
            // Golpe especial: siempre resetea el tiempo de vuelo a tope.
            state.flightRemainingSeconds = specialAttackResetSeconds;
            state.regenAccumulatorMillis = 0L;
        } else if (isNewOrExpired) {
            // Primera vez que se marca (o su marca anterior ya había
            // expirado del todo): arranca con el tiempo de vuelo a tope,
            // igual que si fuera la primera vez.
            state.flightRemainingSeconds = flightMaxSeconds;
            state.regenAccumulatorMillis = 0L;
        }
        // Golpe normal sobre una marca ya activa: no se toca el tiempo de vuelo.
    }

    private boolean isRestricted(UUID uuid) {
        State state = states.get(uuid);
        return state != null && state.tagEndMillis > System.currentTimeMillis();
    }

    private boolean canFly(UUID uuid) {
        if (!isRestricted(uuid)) return true;
        State state = states.get(uuid);
        return state != null && state.flightRemainingSeconds >= minTimeToFlySeconds;
    }

    private void tick() {
        if (states.isEmpty()) return;

        long now = System.currentTimeMillis();
        long regenIntervalMs = Math.round(regenIntervalSeconds * 1000.0);
        long graceTimeMs = Math.round(graceTimeSeconds * 1000.0);

        states.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            State state = entry.getValue();

            if (now >= state.tagEndMillis) {
                return true;
            }

            long sinceAction = now - state.lastActionMillis;
            if (sinceAction >= graceTimeMs && state.flightRemainingSeconds < flightMaxSeconds && regenIntervalMs > 0) {
                state.regenAccumulatorMillis += TICK_MILLIS;
                while (state.regenAccumulatorMillis >= regenIntervalMs && state.flightRemainingSeconds < flightMaxSeconds) {
                    state.flightRemainingSeconds = Math.min(flightMaxSeconds, state.flightRemainingSeconds + regenAmountSeconds);
                    state.regenAccumulatorMillis -= regenIntervalMs;
                }
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && player.isGliding()) {
                state.flightRemainingSeconds = Math.max(0, state.flightRemainingSeconds - (TICK_MILLIS / 1000.0));
                if (state.flightRemainingSeconds <= 0) {
                    player.setGliding(false);
                }
            }

            return false;
        });
    }

    // ================= ACTION BAR =================

    public Component getActionBarSegment(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.tagEndMillis <= System.currentTimeMillis()) return null;
        if (!hasElytra(player)) return null;

        String formatted = DurationUtil.formatSeconds(state.flightRemainingSeconds);
        TextColor color = ColorUtil.reserveGradient(state.flightRemainingSeconds, flightMaxSeconds);
        return TemplateUtil.apply(actionbarTemplate, PLACEHOLDER, formatted, color);
    }

    private boolean hasElytra(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == Material.ELYTRA) return true;
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && item.getType() == Material.ELYTRA) return true;
        }
        return inventory.getItemInOffHand().getType() == Material.ELYTRA;
    }

    // ================= CICLO DE VIDA =================

    public void clearAll() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        states.clear();
    }
}
