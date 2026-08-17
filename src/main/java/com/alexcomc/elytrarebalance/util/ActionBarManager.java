package com.alexcomc.elytrarebalance.util;

import com.alexcomc.elytrarebalance.ElytraRebalance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionBarManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public interface SegmentProvider {
        Component getSegment(Player player);
    }

    private final ElytraRebalance plugin;
    private final List<SegmentProvider> providers = new ArrayList<>();
    private final Set<UUID> lastHadContent = ConcurrentHashMap.newKeySet();

    private String prefix = "&8| ";
    private String separator = " &8| ";
    private String suffix = " &8|";
    private int periodTicks = 2;

    private BukkitTask task;

    public ActionBarManager(ElytraRebalance plugin) {
        this.plugin = plugin;
    }

    public void registerProvider(SegmentProvider provider) {
        providers.add(provider);
    }

    public void applyConfig(FileConfiguration config) {
        this.prefix = config.getString("actionbar.prefix", "&8| ");
        this.separator = config.getString("actionbar.separator", " &8| ");
        this.suffix = config.getString("actionbar.suffix", " &8|");
        this.periodTicks = Math.max(1, config.getInt("actionbar.update-interval-ticks", 2));
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastHadContent.clear();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Component> segments = new ArrayList<>(providers.size());
            for (SegmentProvider provider : providers) {
                Component segment = provider.getSegment(player);
                if (segment != null) {
                    segments.add(segment);
                }
            }

            UUID uuid = player.getUniqueId();

            if (segments.isEmpty()) {
                if (lastHadContent.remove(uuid)) {
                    player.sendActionBar(Component.empty());
                }
                continue;
            }

            lastHadContent.add(uuid);

            Component message = legacy(prefix);
            for (int i = 0; i < segments.size(); i++) {
                if (i > 0) {
                    message = message.append(legacy(separator));
                }
                message = message.append(segments.get(i));
            }
            message = message.append(legacy(suffix));

            player.sendActionBar(message);
        }
    }

    private static Component legacy(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return LEGACY.deserialize(raw.replace('&', '§'));
    }
}
