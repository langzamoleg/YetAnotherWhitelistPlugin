package org.renwixx.yawl;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.renwixx.yawl.storage.WhitelistEntry;
import org.renwixx.yawl.storage.WhitelistStorage;
import org.renwixx.yawl.storage.RedisWhitelistStorage;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "yetanotherwhitelistplugin",
        name = "yawl",
        version = BuildConstants.VERSION,
        description = "Most simple whitelist plugin for Velocity server.",
        url = "https://github.com/renwixx/",
        authors = {"Renwixx"}
)
public class Yawl {

    public static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private VelocityToBackendBridge velocityToBackendBridge;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<String, WhitelistEntry> whitelistedPlayers = new ConcurrentHashMap<>();
    private boolean useClientLocale = false;
    private PluginConfig config;
    private LocaleManager localeManager;
    private WhitelistStorage storage;
    private ScheduledTask expiryTask;
    private ScheduledTask placeholderUpdateTask;

    @Inject
    public Yawl(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        reload();

        server.getEventManager().register(this, new ConnectionListener(this));

        this.velocityToBackendBridge = new VelocityToBackendBridge(this, localeManager);
        server.getEventManager().register(this, this.velocityToBackendBridge);

        CommandManager commandManager = server.getCommandManager();
        BrigadierCommand yawlCommand = WhitelistCommand.create(this, this.velocityToBackendBridge);
        commandManager.register(commandManager.metaBuilder("yawl").build(), yawlCommand);

        logger.info("YAWL (Yet Another Whitelist Plugin) has been enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        try {
            if (expiryTask != null) {
                expiryTask.cancel();
                expiryTask = null;
            }
            if (placeholderUpdateTask != null) {
                placeholderUpdateTask.cancel();
            }
            if (storage != null) {
                storage.flush(whitelistedPlayers);
                storage.close();
            }
        } catch (Exception e) {
            logger.error("Error while closing storage", e);
        }
    }

    public void reload() {
        reloadConfig();
        reloadStorage();
        removeExpiredEntriesAndMaybeKick(false);
        checkAndKickNonWhitelistedPlayers();
        scheduleExpirySweep();
        schedulePlaceholderUpdates();
    }

    private void reloadConfig() {
        this.config = new PluginConfig(dataDirectory, logger);
        this.useClientLocale = config.isUseClientLocale();
        
        if (this.localeManager == null) {
            this.localeManager = new LocaleManager(dataDirectory, this, config.getLocale(), logger);
        } else {
            this.localeManager.setLocale(config.getLocale());
        }
    }

    private void reloadStorage() {
        try {
            closeStorageIfNeeded();
            initializeStorage();
            loadWhitelistData();
        } catch (Exception e) {
            logger.error("Failed to initialize storage. Fallback to empty whitelist.", e);
            whitelistedPlayers.clear();
        }
    }

    private void closeStorageIfNeeded() throws Exception {
        if (storage != null) {
            storage.close();
        }
    }

    private void initializeStorage() throws Exception {
        storage = new RedisWhitelistStorage(config.getRedisUrl(), config.getRedisPassword(), logger);
        storage.init();
    }

    private void loadWhitelistData() throws Exception {
        whitelistedPlayers.clear();
        Map<String, WhitelistEntry> loaded = storage.loadAll();
        
        for (WhitelistEntry entry : loaded.values()) {
            String canonicalName = canonical(entry.getOriginalName());
            whitelistedPlayers.put(canonicalName,
                new WhitelistEntry(canonicalName, entry.getOriginalName(), entry.getExpiresAtMillis()));
        }
    }

    private void schedulePlaceholderUpdates() {
        try {
            if (placeholderUpdateTask != null) {
                placeholderUpdateTask.cancel();
            }
        } catch (Exception ignored) {}

        placeholderUpdateTask = server.getScheduler()
                .buildTask(this, () -> {
                    if (velocityToBackendBridge == null) return;
                    for (Player player : server.getAllPlayers()) {
                        velocityToBackendBridge.sendWhitelistUpdate(player);
                    }
                })
                .repeat(Duration.ofMinutes(config.getPlaceholderReloadInterval()))
                .schedule();
    }

    private void scheduleExpirySweep() {
        try {
            if (expiryTask != null) {
                expiryTask.cancel();
            }
        } catch (Exception ignored) {}
        expiryTask = server.getScheduler()
                .buildTask(this, () -> {
                    if (!config.isEnabled()) return;
                    removeExpiredEntriesAndMaybeKick(config.isKickActiveOnRevoke());
                })
                .repeat(Duration.ofSeconds(5))
                .schedule();
    }

    public void checkAndKickNonWhitelistedPlayers() {
        if (!config.isEnabled()) {
            return;
        }

        Component kickMessage = localeManager.getMessage("kick-message");
        server.getAllPlayers().stream()
            .filter(player -> !player.hasPermission(Permissions.BYPASS))
            .filter(player -> !isWhitelisted(player.getUsername()))
            .forEach(player -> {
                player.disconnect(kickMessage);
                logger.info("Kicked player {} - not whitelisted", player.getUsername());
            });
    }

    private String canonical(String name) {
        return config.isCaseSensitive() ? name : name.toLowerCase(Locale.ROOT);
    }

    public boolean isWhitelisted(String playerName) {
        WhitelistEntry entry = whitelistedPlayers.get(canonical(playerName));
        if (entry == null) return false;
        return !entry.isExpired();
    }

    public List<String> getWhitelistedPlayers() {
        return whitelistedPlayers.values().stream()
                .filter(e -> !e.isExpired())
                .sorted(Comparator.comparing(WhitelistEntry::getOriginalName, String.CASE_INSENSITIVE_ORDER))
                .map(WhitelistEntry::getOriginalName)
                .toList();
    }

    public boolean addPlayer(String playerName) {
        return addPlayerInternal(playerName, null);
    }

    public boolean addPlayer(String playerName, Duration duration) {
        Long expiresAt = duration == null ? null : Instant.now().plus(duration).toEpochMilli();
        return addPlayerInternal(playerName, expiresAt);
    }

    public Optional<WhitelistEntry> getEntry(String playerName) {
        String processed = playerName.trim();
        if (processed.isEmpty()) return Optional.empty();
        String canonical = canonical(processed);
        return Optional.ofNullable(whitelistedPlayers.get(canonical));
    }

    public boolean updatePlayerExpiry(String playerName, Long expiresAtMillis) {
        String processed = playerName.trim();
        if (processed.isEmpty()) {
            return false;
        }
        
        String canonical = canonical(processed);
        WhitelistEntry existingEntry = whitelistedPlayers.get(canonical);
        
        if (existingEntry == null) {
            return false;
        }
        
        WhitelistEntry updatedEntry = new WhitelistEntry(canonical, existingEntry.getOriginalName(), expiresAtMillis);
        whitelistedPlayers.put(canonical, updatedEntry);
        persistWhitelist(processed);
        
        return true;
    }

    private boolean addPlayerInternal(String playerName, Long expiresAtMillis) {
        String processed = playerName.trim();
        if (processed.isEmpty()) {
            return false;
        }

        String canonical = canonical(processed);
        WhitelistEntry newEntry = new WhitelistEntry(canonical, processed, expiresAtMillis);
        WhitelistEntry existingEntry = whitelistedPlayers.putIfAbsent(canonical, newEntry);
        
        if (existingEntry == null) {
            persistWhitelist(processed);
            return true;
        }
        
        if (shouldUpdateExistingEntry(existingEntry, expiresAtMillis)) {
            whitelistedPlayers.put(canonical, newEntry);
            persistWhitelist(processed);
        }
        
        return false;
    }

    private boolean shouldUpdateExistingEntry(WhitelistEntry existing, Long newExpiry) {
        return !Objects.equals(existing.getExpiresAtMillis(), newExpiry) && newExpiry != null;
    }

    private void persistWhitelist(String playerName) {
        try {
            if (storage != null) {
                storage.flush(whitelistedPlayers);
            }
        } catch (Exception e) {
            logger.error("Failed to persist whitelist for player: {}", playerName, e);
        }
    }

    public boolean removePlayer(String playerName) {
        String processed = playerName.trim();
        String canonical = canonical(processed);

        WhitelistEntry removedEntry = whitelistedPlayers.remove(canonical);
        
        if (removedEntry == null) {
            return false;
        }
        
        persistWhitelist(processed);
        kickPlayerIfNeeded(processed);
        
        return true;
    }

    private void kickPlayerIfNeeded(String playerName) {
        if (!config.isKickActiveOnRevoke()) {
            return;
        }
        
        server.getPlayer(playerName).ifPresent(player -> {
            if (!player.hasPermission(Permissions.BYPASS)) {
                player.disconnect(localeManager.getMessage("kick-message"));
                logger.info("Kicked player {} - removed from whitelist", player.getUsername());
            }
        });
    }

    private void removeExpiredEntriesAndMaybeKick(boolean kickActive) {
        if (!kickActive || !config.isEnabled()) {
            return;
        }
        
        whitelistedPlayers.values().stream()
            .filter(WhitelistEntry::isExpired)
            .forEach(this::kickExpiredPlayer);
    }

    private void kickExpiredPlayer(WhitelistEntry entry) {
        server.getPlayer(entry.getOriginalName()).ifPresent(player -> {
            if (!player.hasPermission(Permissions.BYPASS)) {
                player.disconnect(localeManager.getMessage("kick-message"));
                logger.info("Kicked player {} - whitelist access expired", player.getUsername());
            }
        });
    }

    public VelocityToBackendBridge getVelocityToBackendBridge() { return velocityToBackendBridge; }
    public LocaleManager getLocaleManager() { return localeManager; }
    public boolean shouldUseClientLocale() { return useClientLocale; }
    public PluginConfig getConfig() { return config; }
    public Logger getLogger() { return logger; }
    public ProxyServer getServer() { return server; }
}