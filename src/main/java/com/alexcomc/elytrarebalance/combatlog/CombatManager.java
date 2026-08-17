package com.alexcomc.elytrarebalance.combatlog;

import com.alexcomc.elytrarebalance.ElytraRebalance;
import com.alexcomc.elytrarebalance.util.ColorUtil;
import com.alexcomc.elytrarebalance.util.DurationUtil;
import com.alexcomc.elytrarebalance.util.TemplateUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {

    public static final String PLACEHOLDER = "%combat%";

    private final ElytraRebalance plugin;

    private double cooldownSeconds;
    private String actionbarTemplate = PLACEHOLDER;

    private final Map<UUID, Long> combatEndTime = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;

    public CombatManager(ElytraRebalance plugin) {
        this.plugin = plugin;
        applyConfig(plugin.getConfig());
        startCleanupTask();
    }

    public void applyConfig(FileConfiguration config) {
        this.cooldownSeconds = DurationUtil.parseToSeconds(
                config.getString("combat-log.cooldown", "30s"), 30);
        this.actionbarTemplate = config.getString("combat-log.actionbar", "%combat%");
    }

    private void startCleanupTask() {
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 20L, 20L);
    }

    private void cleanupExpired() {
        if (combatEndTime.isEmpty()) return;
        long now = System.currentTimeMillis();
        combatEndTime.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public void tagPlayer(Player player) {
        long endTime = System.currentTimeMillis() + Math.round(cooldownSeconds * 1000.0);
        combatEndTime.put(player.getUniqueId(), endTime);
    }

    public void clearTag(UUID uuid) {
        combatEndTime.remove(uuid);
    }

    public boolean isInCombat(UUID uuid) {
        Long end = combatEndTime.get(uuid);
        return end != null && end > System.currentTimeMillis();
    }

    public double getSecondsLeft(UUID uuid) {
        Long end = combatEndTime.get(uuid);
        if (end == null) return 0;
        double millisLeft = end - System.currentTimeMillis();
        return Math.max(0, millisLeft / 1000.0);
    }

    public Component getActionBarSegment(Player player) {
        double secondsLeft = getSecondsLeft(player.getUniqueId());
        if (secondsLeft <= 0) return null;

        String formatted = DurationUtil.formatSeconds(secondsLeft);
        TextColor color = ColorUtil.countdownGradient(secondsLeft, cooldownSeconds);
        return TemplateUtil.apply(actionbarTemplate, PLACEHOLDER, formatted, color);
    }

    public void clearAll() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        combatEndTime.clear();
    }
}
