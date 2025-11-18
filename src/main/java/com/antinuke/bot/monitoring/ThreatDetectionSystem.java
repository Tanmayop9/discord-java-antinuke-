package com.antinuke.bot.monitoring;

import com.antinuke.bot.config.BotConfig;
import com.antinuke.bot.database.JsonDatabase;
import com.antinuke.bot.recovery.FastRecoverySystem;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Threat Detection System - Monitors and analyzes suspicious activities
 */
public class ThreatDetectionSystem {
    private static final Logger logger = LoggerFactory.getLogger(ThreatDetectionSystem.class);
    
    private final BotConfig config;
    private final JDA jda;
    private final JsonDatabase database;
    private final FastRecoverySystem recoverySystem;
    private final Map<String, Map<String, ActionTracker>> guildUserActions;
    private final Map<String, List<Long>> raidJoinTracking;
    private final ScheduledExecutorService cleanupScheduler;
    
    public ThreatDetectionSystem(BotConfig config, JDA jda, JsonDatabase database, FastRecoverySystem recoverySystem) {
        this.config = config;
        this.jda = jda;
        this.database = database;
        this.recoverySystem = recoverySystem;
        this.guildUserActions = new ConcurrentHashMap<>();
        this.raidJoinTracking = new ConcurrentHashMap<>();
        this.cleanupScheduler = Executors.newScheduledThreadPool(1);
        
        // Cleanup old tracking data every minute
        cleanupScheduler.scheduleAtFixedRate(this::cleanupOldData, 60, 60, TimeUnit.SECONDS);
    }
    
    /**
     * Record an action and check if it's suspicious
     */
    public ThreatAssessment recordAction(String guildId, String userId, ActionType actionType, String targetId) {
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        if (!guildData.isAntiNukeEnabled()) {
            return new ThreatAssessment(false, 0, "Antinuke disabled");
        }
        
        // Check whitelist
        if (isWhitelisted(guildId, userId)) {
            return new ThreatAssessment(false, 0, "User whitelisted");
        }
        
        // Get or create action tracker for this user in this guild
        Map<String, ActionTracker> guildActions = guildUserActions.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>());
        ActionTracker tracker = guildActions.computeIfAbsent(userId, k -> new ActionTracker());
        
        // Record the action
        tracker.recordAction(actionType);
        
        // Check if action count exceeds threshold
        int actionCount = tracker.getActionCount(actionType, 60); // Last 60 seconds
        int threshold = getThreshold(actionType);
        
        if (actionCount >= threshold) {
            logger.warn("THREAT DETECTED: User {} in guild {} performed {} {} actions in 60s (threshold: {})",
                    userId, guildId, actionCount, actionType, threshold);
            
            return new ThreatAssessment(true, actionCount, 
                    String.format("Exceeded threshold: %d %s actions", actionCount, actionType));
        }
        
        return new ThreatAssessment(false, actionCount, "Normal activity");
    }
    
    /**
     * Check if user is whitelisted
     */
    public boolean isWhitelisted(String guildId, String userId) {
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        
        // Owner is always whitelisted
        if (userId.equals(config.getOwnerId())) {
            return true;
        }
        
        // Check user whitelist
        if (guildData.getWhitelistedUsers().contains(userId)) {
            return true;
        }
        
        // Check role whitelist
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                Member member = guild.retrieveMemberById(userId).complete();
                if (member != null) {
                    for (Role role : member.getRoles()) {
                        if (guildData.getWhitelistedRoles().contains(role.getId())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error checking role whitelist for user: {}", userId);
        }
        
        return false;
    }
    
    /**
     * Record raid join attempt
     */
    public boolean checkRaidPattern(String guildId) {
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        List<Long> joins = raidJoinTracking.computeIfAbsent(guildId, k -> new CopyOnWriteArrayList<>());
        joins.add(System.currentTimeMillis());
        
        // Remove joins older than the time window
        int timeWindow = guildData.getThresholds().getOrDefault("raid", 10);
        long cutoff = System.currentTimeMillis() - (timeWindow * 1000L);
        joins.removeIf(timestamp -> timestamp < cutoff);
        
        // Check if join count exceeds threshold
        int threshold = guildData.getThresholds().getOrDefault("raid", 10);
        if (joins.size() >= threshold) {
            logger.warn("RAID DETECTED: {} joins in {}s in guild {}", joins.size(), timeWindow, guildId);
            return true;
        }
        
        return false;
    }
    
    /**
     * Execute punishment for malicious user
     */
    public void executePunishment(String guildId, String userId, String reason) {
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) return;
            
            JsonDatabase.GuildData guildData = database.getGuildData(guildId);
            String punishmentType = guildData.getPunishmentType();
            logger.info("Executing punishment: {} for user {} in guild {} - Reason: {}", 
                    punishmentType, userId, guildId, reason);
            
            // Increment threats blocked counter
            guildData.incrementThreatsBlocked();
            database.saveGuildData(guildId, guildData);
            
            switch (punishmentType.toUpperCase()) {
                case "BAN":
                    guild.ban(UserSnowflake.fromId(userId), 0, TimeUnit.SECONDS)
                            .reason("Antinuke: " + reason)
                            .queue(
                                success -> logger.info("Banned malicious user: {}", userId),
                                error -> logger.error("Failed to ban user: {}", userId, error)
                            );
                    break;
                    
                case "KICK":
                    guild.kick(UserSnowflake.fromId(userId))
                            .reason("Antinuke: " + reason)
                            .queue(
                                success -> logger.info("Kicked malicious user: {}", userId),
                                error -> logger.error("Failed to kick user: {}", userId, error)
                            );
                    break;
                    
                case "STRIP_ROLES":
                    guild.retrieveMemberById(userId).queue(member -> {
                        List<Role> roles = new ArrayList<>(member.getRoles());
                        for (Role role : roles) {
                            guild.removeRoleFromMember(member, role)
                                    .reason("Antinuke: " + reason)
                                    .queue();
                        }
                        logger.info("Stripped all roles from user: {}", userId);
                    });
                    break;
                    
                default:
                    logger.warn("Unknown punishment type: {}", punishmentType);
            }
            
            // Log to configured channel
            logThreat(guild, userId, reason, punishmentType);
            
        } catch (Exception e) {
            logger.error("Error executing punishment for user: {}", userId, e);
        }
    }
    
    /**
     * Trigger recovery after attack
     */
    public void triggerRecovery(String guildId, ActionType actionType, List<String> affectedIds) {
        try {
            Guild guild = jda.getGuildById(guildId);
            if (guild == null) return;
            
            logger.info("Triggering fast recovery for guild: {} (action: {})", guildId, actionType);
            
            switch (actionType) {
                case BAN:
                    // Mass unban
                    recoverySystem.massUnban(guild, affectedIds).thenAccept(result -> {
                        logger.info("Recovery result: {} - {}", result.success, result.message);
                    });
                    break;
                    
                case ROLE_DELETE:
                    // Recover deleted roles
                    recoverySystem.recoverRoles(guild, affectedIds).thenAccept(result -> {
                        logger.info("Recovery result: {} - {}", result.success, result.message);
                    });
                    break;
                    
                case CHANNEL_DELETE:
                    // Recover deleted channels
                    recoverySystem.recoverChannels(guild, affectedIds).thenAccept(result -> {
                        logger.info("Recovery result: {} - {}", result.success, result.message);
                    });
                    break;
                    
                default:
                    logger.debug("No recovery action defined for: {}", actionType);
            }
            
        } catch (Exception e) {
            logger.error("Error triggering recovery", e);
        }
    }
    
    private int getThreshold(ActionType actionType) {
        // Use default thresholds from config
        BotConfig.ThresholdsConfig thresholds = config.getAntiNuke().getThresholds();
        
        switch (actionType) {
            case BAN:
                return thresholds.getBanThreshold();
            case KICK:
                return thresholds.getKickThreshold();
            case CHANNEL_DELETE:
                return thresholds.getChannelDeleteThreshold();
            case CHANNEL_CREATE:
                return thresholds.getChannelCreateThreshold();
            case ROLE_DELETE:
                return thresholds.getRoleDeleteThreshold();
            case ROLE_CREATE:
                return thresholds.getRoleCreateThreshold();
            case WEBHOOK_CREATE:
            case WEBHOOK_UPDATE:
                return thresholds.getWebhookThreshold();
            default:
                return 5; // Default threshold
        }
    }
    
    private void logThreat(Guild guild, String userId, String reason, String punishment) {
        JsonDatabase.GuildData guildData = database.getGuildData(guild.getId());
        String logChannelId = guildData.getLogChannelId();
        if (logChannelId != null && !logChannelId.isEmpty()) {
            try {
                GuildChannel channel = guild.getGuildChannelById(logChannelId);
                if (channel instanceof net.dv8tion.jda.api.entities.channel.concrete.TextChannel) {
                    net.dv8tion.jda.api.entities.channel.concrete.TextChannel textChannel = 
                        (net.dv8tion.jda.api.entities.channel.concrete.TextChannel) channel;
                    
                    textChannel.sendMessage(String.format(
                            "🚨 **Antinuke Alert**\n" +
                            "**User:** <@%s>\n" +
                            "**Action:** %s\n" +
                            "**Reason:** %s\n" +
                            "**Time:** <t:%d:F>",
                            userId, punishment, reason, System.currentTimeMillis() / 1000
                    )).queue();
                }
            } catch (Exception e) {
                logger.error("Error logging threat to channel", e);
            }
        }
    }
    
    private void cleanupOldData() {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        
        // Cleanup action tracking
        guildUserActions.values().forEach(guildActions -> {
            guildActions.values().forEach(tracker -> tracker.cleanup(cutoff));
        });
        
        // Cleanup raid tracking
        raidJoinTracking.values().forEach(joins -> {
            joins.removeIf(timestamp -> timestamp < cutoff);
        });
    }
    
    public void shutdown() {
        cleanupScheduler.shutdown();
    }
    
    // Helper classes
    public static class ActionTracker {
        private final Map<ActionType, List<Long>> actions = new ConcurrentHashMap<>();
        
        public void recordAction(ActionType actionType) {
            actions.computeIfAbsent(actionType, k -> new CopyOnWriteArrayList<>())
                    .add(System.currentTimeMillis());
        }
        
        public int getActionCount(ActionType actionType, int secondsWindow) {
            List<Long> actionList = actions.get(actionType);
            if (actionList == null) return 0;
            
            long cutoff = System.currentTimeMillis() - (secondsWindow * 1000L);
            return (int) actionList.stream()
                    .filter(timestamp -> timestamp >= cutoff)
                    .count();
        }
        
        public void cleanup(long cutoff) {
            actions.values().forEach(list -> list.removeIf(timestamp -> timestamp < cutoff));
        }
    }
    
    public static class ThreatAssessment {
        public final boolean isThreat;
        public final int actionCount;
        public final String reason;
        
        public ThreatAssessment(boolean isThreat, int actionCount, String reason) {
            this.isThreat = isThreat;
            this.actionCount = actionCount;
            this.reason = reason;
        }
    }
}
