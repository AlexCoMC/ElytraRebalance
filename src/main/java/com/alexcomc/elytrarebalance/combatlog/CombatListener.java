package com.alexcomc.elytrarebalance.combatlog;

import com.alexcomc.elytrarebalance.ElytraRebalance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public class CombatListener implements Listener {

    private final ElytraRebalance plugin;
    private final CombatManager combatManager;

    public CombatListener(ElytraRebalance plugin, CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamage() <= 0) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null) return;
        if (attacker.equals(victim)) return;

        if (!victim.hasPermission("elytrarebalance.combatlog.bypass")) {
            combatManager.tagPlayer(victim);
        }
        if (!attacker.hasPermission("elytrarebalance.combatlog.bypass")) {
            combatManager.tagPlayer(attacker);
        }
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!combatManager.isInCombat(player.getUniqueId())) {
            return;
        }
        if (player.hasPermission("elytrarebalance.combatlog.bypass")) {
            return;
        }

        combatManager.clearTag(player.getUniqueId());

        if (plugin.getConfig().getBoolean("combat-log.broadcast-death", true)) {
            String broadcastMsg = plugin.getMessagesManager()
                    .get("combat-log.death-broadcast", "&c{player} murió por desconectarse en combate.")
                    .replace("{player}", player.getName());
            Bukkit.broadcastMessage(broadcastMsg);
        }

        if (player.getHealth() > 0) {
            player.setHealth(0.0);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        combatManager.clearTag(event.getEntity().getUniqueId());
    }
}
