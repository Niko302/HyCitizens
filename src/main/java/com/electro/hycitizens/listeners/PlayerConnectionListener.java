package com.electro.hycitizens.listeners;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.api.scripting.ScriptManager;
import com.electro.hycitizens.api.scripting.VariableManager;
import com.electro.hycitizens.managers.DialogEditorManager;
import com.electro.hycitizens.managers.DialogueManager;
import com.electro.hycitizens.managers.ScriptEditorManager;
import com.electro.hycitizens.util.UpdateChecker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

public class PlayerConnectionListener {
    private static final int DEFAULT_BOOT_SKIN_RESPAWN_DELAY_SECONDS = 10;

    private final HyCitizensPlugin plugin;
    private final AtomicBoolean bootSkinRespawnScheduled = new AtomicBoolean(false);

    public PlayerConnectionListener(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Citizen skins do not render after a server boot until the citizens are respawned, while a
     * manual respawn-all always fixes them. Until the underlying cause is found, do that respawn
     * automatically once, shortly after the first player is fully loaded in.
     *
     * <p>Disable with {@code citizens.boot-skin-respawn.enabled: false}, or change the delay with
     * {@code citizens.boot-skin-respawn.delay-seconds}.
     */
    private void scheduleBootSkinRespawn() {
        if (!bootSkinRespawnScheduled.compareAndSet(false, true)) {
            return;
        }

        var config = plugin.getConfigManager();
        if (config != null && !config.getBoolean("citizens.boot-skin-respawn.enabled", true)) {
            return;
        }

        int delaySeconds = config == null
                ? DEFAULT_BOOT_SKIN_RESPAWN_DELAY_SECONDS
                : Math.max(0, config.getInt("citizens.boot-skin-respawn.delay-seconds",
                        DEFAULT_BOOT_SKIN_RESPAWN_DELAY_SECONDS));

        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            try {
                int count = plugin.getCitizensManager().respawnAllCitizens(true);
                getLogger().atInfo().log("[HyCitizens] Respawned " + count
                        + " citizen(s) after boot so their skins render. Disable with"
                        + " citizens.boot-skin-respawn.enabled: false");
            } catch (Exception e) {
                getLogger().atWarning().log("[HyCitizens] Boot skin respawn failed: " + e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public void onPlayerConnect(@Nonnull PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null)
            return;

        // We need to wait until the player is fully loaded into a world
        final long startTimeMs = System.currentTimeMillis();

        final ScheduledFuture<?>[] futureRef = new ScheduledFuture<?>[1];

        futureRef[0] = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            if (playerRef == null)
                return;

            // Stop the scheduler after 15 seconds
            if (System.currentTimeMillis() - startTimeMs >= 15000) {
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }
                return;
            }

            if (playerRef.getWorldUuid() == null)
                return;

            World world = Universe.get().getWorld(playerRef.getWorldUuid());
            if (world == null)
                return;

            world.execute(() -> {
                // Check if player has moved worlds
                if (!world.getWorldConfig().getUuid().equals(playerRef.getWorldUuid())) {
                    return;
                }

                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }

                Player player = playerRef.getReference().getStore().getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }

                // Stop the scheduler
                if (futureRef[0] != null) {
                    futureRef[0].cancel(false);
                }

                // Trigger player connect script hooks
                ScriptManager.get().handlePlayerConnect(playerRef);

                scheduleBootSkinRespawn();

                if (!playerRef.hasPermission("hycitizens.admin")) {
                    return;
                }

                // Check for updates
                UpdateChecker.checkAsync();

                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    if (!UpdateChecker.hasUpdate()) {
                        return;
                    }

                    world.execute(() -> {
                        if (player == null) {
                            return;
                        }

                        playerRef.sendMessage(
                                Message.join(
                                        Message.raw("[HyCitizens] ").color(Color.GREEN),

                                        Message.raw("Update available! ").color(Color.YELLOW),

                                        Message.raw("Latest: ").color(Color.GRAY),
                                        Message.raw(UpdateChecker.getLatestVersion()).color(Color.CYAN),

                                        Message.raw(" | ").color(Color.DARK_GRAY),

                                        Message.raw("Download (Click)").color(Color.ORANGE)
                                                .link("https://www.curseforge.com/hytale/mods/hycitizens"),

                                        Message.raw(" | ").color(Color.DARK_GRAY),

                                        Message.raw("Donate (Click)").color(Color.PINK)
                                                .link("https://ko-fi.com/electrogames"),

                                        Message.raw(" | ").color(Color.DARK_GRAY),

                                        Message.raw("Discord (Click)").color(Color.CYAN)
                                                .link("https://discord.gg/Snqz9E58Dr"),

                                        Message.raw(" - check out our Discord to get notified for future updates, request features, and submit bug reports.")
                                                .color(Color.GRAY)
                                )
                        );
                    });
                }, 5, TimeUnit.SECONDS);

            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        UUID playerUuid = playerRef.getUuid();
        plugin.getCitizensManager().clearPlayerRuntimeState(playerUuid);
        DialogueManager.get().endDialogueSession(playerRef, "DISCONNECT");
        DialogueManager.get().unloadPlayerState(playerUuid);
        ScriptManager.get().handlePlayerDisconnect(playerRef);
        VariableManager.get().unloadPlayer(playerUuid);
        DialogEditorManager.get().revokeSessions(playerUuid);
        ScriptEditorManager.get().revokeSessions(playerUuid);
    }
}
