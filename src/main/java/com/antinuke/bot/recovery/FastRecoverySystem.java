package com.antinuke.bot.recovery;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Fast Recovery System - Implements the fastest possible recovery mechanisms
 * for server state restoration after attacks.
 */
public class FastRecoverySystem {
    private static final Logger logger = LoggerFactory.getLogger(FastRecoverySystem.class);
    
    private final Cache<String, ServerSnapshot> snapshotCache;
    private final ExecutorService recoveryExecutor;
    private final ScheduledExecutorService snapshotScheduler;
    private final int maxConcurrentOperations;
    
    public FastRecoverySystem(int cacheSize, int concurrentOperations, int snapshotIntervalSeconds) {
        this.maxConcurrentOperations = concurrentOperations;
        
        // High-performance cache for instant recovery
        this.snapshotCache = Caffeine.newBuilder()
                .maximumSize(cacheSize)
                .expireAfterWrite(Duration.ofHours(24))
                .recordStats()
                .build();
        
        // Dedicated thread pool for parallel recovery operations
        this.recoveryExecutor = Executors.newFixedThreadPool(
                concurrentOperations,
                r -> {
                    Thread t = new Thread(r, "FastRecovery-Thread");
                    t.setDaemon(true);
                    return t;
                }
        );
        
        // Scheduled snapshots for always having recent state
        this.snapshotScheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Create a snapshot of the entire guild state
     */
    public void createSnapshot(Guild guild) {
        try {
            ServerSnapshot snapshot = new ServerSnapshot();
            snapshot.guildId = guild.getId();
            snapshot.guildName = guild.getName();
            snapshot.timestamp = System.currentTimeMillis();
            
            // Capture roles with all permissions and settings
            snapshot.roles = guild.getRoles().stream()
                    .map(this::captureRole)
                    .collect(Collectors.toList());
            
            // Capture channels with permissions and settings
            snapshot.channels = guild.getChannels().stream()
                    .map(this::captureChannel)
                    .collect(Collectors.toList());
            
            // Capture members and their roles
            snapshot.memberRoles = new ConcurrentHashMap<>();
            guild.loadMembers().onSuccess(members -> {
                members.forEach(member -> {
                    snapshot.memberRoles.put(member.getId(), 
                        member.getRoles().stream()
                            .map(Role::getId)
                            .collect(Collectors.toList()));
                });
            }).onError(error -> {
                logger.debug("Could not load members for snapshot");
            });
            
            // Capture webhooks
            snapshot.webhooks = new CopyOnWriteArrayList<>();
            for (TextChannel channel : guild.getTextChannels()) {
                try {
                    channel.retrieveWebhooks().queue(webhooks -> {
                        webhooks.forEach(webhook -> {
                            snapshot.webhooks.add(captureWebhook(webhook));
                        });
                    });
                } catch (Exception e) {
                    logger.debug("Could not retrieve webhooks for channel: {}", channel.getId());
                }
            }
            
            // Store snapshot in cache for instant recovery
            snapshotCache.put(guild.getId(), snapshot);
            logger.info("Created snapshot for guild: {} ({} roles, {} channels, {} members)",
                    guild.getName(), snapshot.roles.size(), snapshot.channels.size(), snapshot.memberRoles.size());
                    
        } catch (Exception e) {
            logger.error("Error creating snapshot for guild: {}", guild.getId(), e);
        }
    }
    
    /**
     * Rapidly recover deleted/modified roles
     */
    public CompletableFuture<RecoveryResult> recoverRoles(Guild guild, List<String> deletedRoleIds) {
        ServerSnapshot snapshot = snapshotCache.getIfPresent(guild.getId());
        if (snapshot == null) {
            return CompletableFuture.completedFuture(
                new RecoveryResult(false, "No snapshot available", 0));
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger recovered = new AtomicInteger(0);
        
        for (String roleId : deletedRoleIds) {
            RoleSnapshot roleSnapshot = snapshot.roles.stream()
                    .filter(r -> r.id.equals(roleId))
                    .findFirst()
                    .orElse(null);
            
            if (roleSnapshot != null) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        guild.createRole()
                                .setName(roleSnapshot.name)
                                .setColor(roleSnapshot.color)
                                .setPermissions(roleSnapshot.permissions)
                                .setHoisted(roleSnapshot.hoisted)
                                .setMentionable(roleSnapshot.mentionable)
                                .queue(role -> {
                                    recovered.incrementAndGet();
                                    logger.info("Recovered role: {} in guild: {}", role.getName(), guild.getName());
                                });
                    } catch (Exception e) {
                        logger.error("Error recovering role: {}", roleSnapshot.name, e);
                    }
                }, recoveryExecutor);
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> new RecoveryResult(true, "Roles recovered", recovered.get()));
    }
    
    /**
     * Rapidly recover deleted channels
     */
    public CompletableFuture<RecoveryResult> recoverChannels(Guild guild, List<String> deletedChannelIds) {
        ServerSnapshot snapshot = snapshotCache.getIfPresent(guild.getId());
        if (snapshot == null) {
            return CompletableFuture.completedFuture(
                new RecoveryResult(false, "No snapshot available", 0));
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger recovered = new AtomicInteger(0);
        
        for (String channelId : deletedChannelIds) {
            ChannelSnapshot channelSnapshot = snapshot.channels.stream()
                    .filter(c -> c.id.equals(channelId))
                    .findFirst()
                    .orElse(null);
            
            if (channelSnapshot != null) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        restoreChannel(guild, channelSnapshot);
                        recovered.incrementAndGet();
                    } catch (Exception e) {
                        logger.error("Error recovering channel: {}", channelSnapshot.name, e);
                    }
                }, recoveryExecutor);
                futures.add(future);
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> new RecoveryResult(true, "Channels recovered", recovered.get()));
    }
    
    /**
     * Rapidly restore member roles (fastest operation)
     */
    public CompletableFuture<RecoveryResult> restoreMemberRoles(Guild guild, String memberId) {
        ServerSnapshot snapshot = snapshotCache.getIfPresent(guild.getId());
        if (snapshot == null) {
            return CompletableFuture.completedFuture(
                new RecoveryResult(false, "No snapshot available", 0));
        }
        
        List<String> roleIds = snapshot.memberRoles.get(memberId);
        if (roleIds == null || roleIds.isEmpty()) {
            return CompletableFuture.completedFuture(
                new RecoveryResult(false, "No roles to restore", 0));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Member member = guild.retrieveMemberById(memberId).complete();
                List<Role> rolesToAdd = roleIds.stream()
                        .map(guild::getRoleById)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                
                // Batch role addition for speed
                for (Role role : rolesToAdd) {
                    guild.addRoleToMember(member, role).queue();
                }
                
                logger.info("Restored {} roles for member: {} in guild: {}", 
                        rolesToAdd.size(), memberId, guild.getName());
                return new RecoveryResult(true, "Roles restored", rolesToAdd.size());
                
            } catch (Exception e) {
                logger.error("Error restoring member roles: {}", memberId, e);
                return new RecoveryResult(false, e.getMessage(), 0);
            }
        }, recoveryExecutor);
    }
    
    /**
     * Mass unban - rapidly unban multiple users
     */
    public CompletableFuture<RecoveryResult> massUnban(Guild guild, List<String> userIds) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger unbanned = new AtomicInteger(0);
        
        for (String userId : userIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    guild.unban(UserSnowflake.fromId(userId)).queue(
                        success -> {
                            unbanned.incrementAndGet();
                            logger.info("Unbanned user: {} in guild: {}", userId, guild.getName());
                        },
                        error -> logger.debug("Could not unban user: {}", userId)
                    );
                } catch (Exception e) {
                    logger.debug("Error unbanning user: {}", userId);
                }
            }, recoveryExecutor);
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> new RecoveryResult(true, "Mass unban completed", unbanned.get()));
    }
    
    /**
     * Full server recovery - recovers everything
     */
    public CompletableFuture<RecoveryResult> fullServerRecovery(Guild guild) {
        ServerSnapshot snapshot = snapshotCache.getIfPresent(guild.getId());
        if (snapshot == null) {
            return CompletableFuture.completedFuture(
                new RecoveryResult(false, "No snapshot available", 0));
        }
        
        AtomicInteger totalRecovered = new AtomicInteger(0);
        
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting full server recovery for: {}", guild.getName());
                
                // Step 1: Recover roles first (needed for channels)
                List<String> currentRoleIds = guild.getRoles().stream()
                        .map(Role::getId)
                        .collect(Collectors.toList());
                        
                List<String> missingRoles = snapshot.roles.stream()
                        .map(r -> r.id)
                        .filter(id -> !currentRoleIds.contains(id))
                        .collect(Collectors.toList());
                
                if (!missingRoles.isEmpty()) {
                    RecoveryResult roleResult = recoverRoles(guild, missingRoles).get();
                    totalRecovered.addAndGet(roleResult.itemsRecovered);
                }
                
                // Step 2: Recover channels
                List<String> currentChannelIds = guild.getChannels().stream()
                        .map(GuildChannel::getId)
                        .collect(Collectors.toList());
                        
                List<String> missingChannels = snapshot.channels.stream()
                        .map(c -> c.id)
                        .filter(id -> !currentChannelIds.contains(id))
                        .collect(Collectors.toList());
                
                if (!missingChannels.isEmpty()) {
                    RecoveryResult channelResult = recoverChannels(guild, missingChannels).get();
                    totalRecovered.addAndGet(channelResult.itemsRecovered);
                }
                
                // Step 3: Restore member roles (concurrent for all members)
                List<CompletableFuture<RecoveryResult>> memberFutures = new ArrayList<>();
                snapshot.memberRoles.keySet().forEach(memberId -> {
                    memberFutures.add(restoreMemberRoles(guild, memberId));
                });
                
                CompletableFuture.allOf(memberFutures.toArray(new CompletableFuture[0])).get();
                
                logger.info("Full server recovery completed for: {} ({} items recovered)", 
                        guild.getName(), totalRecovered.get());
                        
            } catch (Exception e) {
                logger.error("Error during full server recovery: {}", guild.getId(), e);
            }
        }, recoveryExecutor).thenApply(v -> 
            new RecoveryResult(true, "Full recovery completed", totalRecovered.get()));
    }
    
    // Helper methods
    private RoleSnapshot captureRole(Role role) {
        RoleSnapshot snapshot = new RoleSnapshot();
        snapshot.id = role.getId();
        snapshot.name = role.getName();
        snapshot.color = role.getColor();
        snapshot.permissions = role.getPermissionsRaw();
        snapshot.hoisted = role.isHoisted();
        snapshot.mentionable = role.isMentionable();
        snapshot.position = role.getPosition();
        return snapshot;
    }
    
    private ChannelSnapshot captureChannel(GuildChannel channel) {
        ChannelSnapshot snapshot = new ChannelSnapshot();
        snapshot.id = channel.getId();
        snapshot.name = channel.getName();
        snapshot.type = channel.getType().name();
        snapshot.position = 0; // Position not available in this JDA version
        
        if (channel instanceof Category) {
            snapshot.parentId = null;
        } else {
            snapshot.parentId = null; // Parent category not easily accessible
        }
        
        return snapshot;
    }
    
    private WebhookSnapshot captureWebhook(Webhook webhook) {
        WebhookSnapshot snapshot = new WebhookSnapshot();
        snapshot.id = webhook.getId();
        snapshot.name = webhook.getName();
        snapshot.channelId = webhook.getChannel().getId();
        snapshot.token = webhook.getToken();
        return snapshot;
    }
    
    private void restoreChannel(Guild guild, ChannelSnapshot snapshot) {
        try {
            switch (snapshot.type) {
                case "TEXT":
                    guild.createTextChannel(snapshot.name)
                            .setPosition(snapshot.position)
                            .queue();
                    break;
                case "VOICE":
                    guild.createVoiceChannel(snapshot.name)
                            .setPosition(snapshot.position)
                            .queue();
                    break;
                case "CATEGORY":
                    guild.createCategory(snapshot.name)
                            .setPosition(snapshot.position)
                            .queue();
                    break;
            }
            logger.info("Restored channel: {} in guild: {}", snapshot.name, guild.getName());
        } catch (Exception e) {
            logger.error("Error restoring channel: {}", snapshot.name, e);
        }
    }
    
    public void scheduleSnapshots(Guild guild, int intervalSeconds) {
        snapshotScheduler.scheduleAtFixedRate(
                () -> createSnapshot(guild),
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }
    
    public void shutdown() {
        recoveryExecutor.shutdown();
        snapshotScheduler.shutdown();
    }
    
    // Data classes
    public static class ServerSnapshot {
        String guildId;
        String guildName;
        long timestamp;
        List<RoleSnapshot> roles;
        List<ChannelSnapshot> channels;
        Map<String, List<String>> memberRoles;
        List<WebhookSnapshot> webhooks;
    }
    
    public static class RoleSnapshot {
        String id;
        String name;
        java.awt.Color color;
        long permissions;
        boolean hoisted;
        boolean mentionable;
        int position;
    }
    
    public static class ChannelSnapshot {
        String id;
        String name;
        String type;
        String parentId;
        int position;
    }
    
    public static class WebhookSnapshot {
        String id;
        String name;
        String channelId;
        String token;
    }
    
    public static class RecoveryResult {
        public final boolean success;
        public final String message;
        public final int itemsRecovered;
        
        public RecoveryResult(boolean success, String message, int itemsRecovered) {
            this.success = success;
            this.message = message;
            this.itemsRecovered = itemsRecovered;
        }
    }
}
