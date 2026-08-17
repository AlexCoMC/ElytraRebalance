package com.alexcomc.elytrarebalance;

import com.alexcomc.elytrarebalance.combatlog.CombatListener;
import com.alexcomc.elytrarebalance.combatlog.CombatManager;
import com.alexcomc.elytrarebalance.elytra.ElytraLimit;
import com.alexcomc.elytrarebalance.util.ActionBarManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ElytraRebalance extends JavaPlugin {

    private MessagesManager messagesManager;
    private ActionBarManager actionBarManager;

    private CombatManager combatManager;
    private ElytraLimit elytraLimit;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messagesManager = new MessagesManager(this);

        this.actionBarManager = new ActionBarManager(this);
        actionBarManager.applyConfig(getConfig());

        enableCombatLogModule();
        enableElytraLimitModule();

        actionBarManager.start();

        ErbCommand erbCommand = new ErbCommand(this);
        getCommand("erb").setExecutor(erbCommand);
        getCommand("erb").setTabCompleter(erbCommand);

        getLogger().info("ElytraRebalance habilitado.");
    }

    @Override
    public void onDisable() {
        if (combatManager != null) {
            combatManager.clearAll();
        }
        if (elytraLimit != null) {
            elytraLimit.clearAll();
        }
        if (actionBarManager != null) {
            actionBarManager.stop();
        }
        getLogger().info("ElytraRebalance deshabilitado.");
    }

    private void enableCombatLogModule() {
        if (!getConfig().getBoolean("combat-log.enabled", true)) {
            getLogger().info("Módulo combat-log desactivado en config.yml.");
            return;
        }

        this.combatManager = new CombatManager(this);
        actionBarManager.registerProvider(combatManager::getActionBarSegment);

        getServer().getPluginManager().registerEvents(
                new CombatListener(this, combatManager), this);

        getLogger().info("Módulo combat-log activo.");
    }

    private void enableElytraLimitModule() {
        if (!getConfig().getBoolean("elytra-limit.enabled", true)) {
            getLogger().info("Módulo elytra-limit desactivado en config.yml.");
            return;
        }

        this.elytraLimit = new ElytraLimit(this);
        actionBarManager.registerProvider(elytraLimit::getActionBarSegment);
        getServer().getPluginManager().registerEvents(elytraLimit, this);

        getLogger().info("Módulo elytra-limit activo.");
    }

    public void reloadAll() {
        reloadConfig();
        messagesManager.reload();
        actionBarManager.applyConfig(getConfig());

        if (combatManager != null) {
            combatManager.applyConfig(getConfig());
        }
        if (elytraLimit != null) {
            elytraLimit.applyConfig();
        }
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public ElytraLimit getElytraLimit() {
        return elytraLimit;
    }
}
