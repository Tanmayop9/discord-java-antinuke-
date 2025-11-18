package com.antinuke.bot.listeners;

import com.antinuke.bot.config.BotConfig;
import com.antinuke.bot.database.JsonDatabase;
import com.antinuke.bot.monitoring.ThreatDetectionSystem;
import com.antinuke.bot.recovery.FastRecoverySystem;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Antinuke Event Listener - Monitors all server events for malicious activities
 */
public class AntiNukeListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(AntiNukeListener.class);
    
    private final BotConfig config;
    private final JsonDatabase database;
    private final ThreatDetectionSystem threatDetector;
    private final FastRecoverySystem recoverySystem;
    
    public AntiNukeListener(BotConfig config, JsonDatabase database, ThreatDetectionSystem threatDetector, FastRecoverySystem recoverySystem) {
        this.config = config;
        this.database = database;
        this.threatDetector = threatDetector;
        this.recoverySystem = recoverySystem;
    }
    
    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        JsonDatabase.GuildData guildData = database.getGuildData(event.getGuild().getId());
        if (!guildData.isAntiNukeEnabled() || !guildData.getProtections().getOrDefault("antiBan", true)) return;
        
        Guild guild = event.getGuild();
        String bannedUserId = event.getUser().getId();
        
        // Get audit log to find who performed the ban
        guild.retrieveAuditLogs()
                .type(ActionType.BAN)
                .limit(1)
                .queue(auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        String executorId = entry.getUserId();
                        
                        if (executorId != null) {
                            // Record the ban action
                            ThreatDetectionSystem.ThreatAssessment assessment = 
                                    threatDetector.recordAction(guild.getId(), executorId, ActionType.BAN, bannedUserId);
                            
                            if (assessment.isThreat) {
                                // Punish the malicious user
                                threatDetector.executePunishment(guild.getId(), executorId, 
                                        "Mass banning detected (" + assessment.actionCount + " bans)");
                                
                                // Trigger recovery - unban the victims
                                List<String> bannedIds = new ArrayList<>();
                                bannedIds.add(bannedUserId);
                                threatDetector.triggerRecovery(guild.getId(), ActionType.BAN, bannedIds);
                            }
                        }
                    }
                });
    }
    
    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        JsonDatabase.GuildData guildData = database.getGuildData(event.getGuild().getId());
        if (!guildData.isAntiNukeEnabled() || !guildData.getProtections().getOrDefault("antiKick", true)) return;
        
        Guild guild = event.getGuild();
        String removedUserId = event.getUser().getId();
        
        // Check if it was a kick (not a leave)
        guild.retrieveAuditLogs()
                .type(ActionType.KICK)
                .limit(1)
                .queue(auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        
                        // Check if this audit entry is recent (within 3 seconds)
                        if (System.currentTimeMillis() - entry.getTimeCreated().toInstant().toEpochMilli() < 3000) {
                            String executorId = entry.getUserId();
                            
                            if (executorId != null) {
                                ThreatDetectionSystem.ThreatAssessment assessment = 
                                        threatDetector.recordAction(guild.getId(), executorId, ActionType.KICK, removedUserId);
                                
                                if (assessment.isThreat) {
                                    threatDetector.executePunishment(guild.getId(), executorId, 
                                            "Mass kicking detected (" + assessment.actionCount + " kicks)");
                                }
                            }
                        }
                    }
                });
    }
    
    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        JsonDatabase.GuildData guildData = database.getGuildData(event.getGuild().getId());
        if (!guildData.isAntiNukeEnabled() || !guildData.getProtections().getOrDefault("antiChannelCreate", true)) return;
        
        Guild guild = event.getGuild();
        
        guild.retrieveAuditLogs()
                .type(ActionType.CHANNEL_CREATE)
                .limit(1)
                .queue(auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        String executorId = entry.getUserId();
                        
                        if (executorId != null) {
                            ThreatDetectionSystem.ThreatAssessment assessment = 
                                    threatDetector.recordAction(guild.getId(), executorId, 
                                            ActionType.CHANNEL_CREATE, event.getChannel().getId());
                            
                            if (assessment.isThreat) {
                                threatDetector.executePunishment(guild.getId(), executorId, 
                                        "Mass channel creation detected (" + assessment.actionCount + " channels)");
                                
                                // Delete the spam channels
                                event.getChannel().delete().queue(
                                    success -> logger.info("Deleted spam channel: {}", event.getChannel().getId()),
                                    error -> logger.debug("Could not delete spam channel")
                                );
                            }
                        }
                    }
                });
    }
    
    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        JsonDatabase.GuildData guildData = database.getGuildData(event.getGuild().getId());
        if (!guildData.isAntiNukeEnabled() || !guildData.getProtections().getOrDefault("antiChannelDelete", true)) return;
        
        Guild guild = event.getGuild();
        String channelId = event.getChannel().getId();
        
        guild.retrieveAuditLogs()
                .type(ActionType.CHANNEL_DELETE)
                .limit(1)
                .queue(auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        String executorId = entry.getUserId();
                        
                        if (executorId != null) {
                            ThreatDetectionSystem.ThreatAssessment assessment = 
                                    threatDetector.recordAction(guild.getId(), executorId, 
                                            ActionType.CHANNEL_DELETE, channelId);
                            
                            if (assessment.isThreat) {
                                threatDetector.executePunishment(guild.getId(), executorId, 
                                        "Mass channel deletion detected (" + assessment.actionCount + " channels)");
                                
                                // Trigger fast recovery
                                List<String> deletedIds = new ArrayList<>();
                                deletedIds.add(channelId);
                                threatDetector.triggerRecovery(guild.getId(), ActionType.CHANNEL_DELETE, deletedIds);
                            }
                        }
                    }
                });
    }
    
    @Override
    public void onRoleCreate(@NotNull RoleCreateEvent event) {
        JsonDatabase.GuildData guildData = database.getGuildData(event.getGuild().getId());
        if (!guildData.isAntiNukeEnabled() || !guildData.getProtections().getOrDefault("antiRoleCreate", true)) return;
        
        Guild guild = event.getGuild();
        
        guild.retrieveAuditLogs()
                .type(ActionType.ROLE_CREATE)
                .limit(1)
                .queue(auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        String executorId = entry.getUserId();
                        
                        if (executorId != null) {
                            ThreatDetectionSystem.ThreatAssessment assessment = 
                                    threatDetector.recordAction(guild.getId(), executorId, 
                                            ActionType.ROLE_CREATE, event.getRole().getId());
                            
                            if (assessment.isThreat) {
                                threatDetector.executePunishment(guild.getId(), executorId, 
                                        "Mass role creation detected (" + assessment.actionCount + " roles)");
                                
                                // Delete the spam roles
                                event.getRole().delete().queue(
                                    success -> logger.info("Deleted spam role: {}", event.getRole().getId()),
                                    error -> logger.debug("Could not delete spam role")
                                );
                            }
                        }
                    }
                });
    }
    
    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        JsonDatabase.GuildData guildData = database.getGuildData(event.getGuild().getId());
        if (!guildData.isAntiNukeEnabled() || !guildData.getProtections().getOrDefault("antiRoleDelete", true)) return;
        
        Guild guild = event.getGuild();
        String roleId = event.getRole().getId();
        
        guild.retrieveAuditLogs()
                .type(ActionType.ROLE_DELETE)
                .limit(1)
                .queue(auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        String executorId = entry.getUserId();
                        
                        if (executorId != null) {
                            ThreatDetectionSystem.ThreatAssessment assessment = 
                                    threatDetector.recordAction(guild.getId(), executorId, 
                                            ActionType.ROLE_DELETE, roleId);
                            
                            if (assessment.isThreat) {
                                threatDetector.executePunishment(guild.getId(), executorId, 
                                        "Mass role deletion detected (" + assessment.actionCount + " roles)");
                                
                                // Trigger fast recovery
                                List<String> deletedIds = new ArrayList<>();
                                deletedIds.add(roleId);
                                threatDetector.triggerRecovery(guild.getId(), ActionType.ROLE_DELETE, deletedIds);
                            }
                        }
                    }
                });
    }
    
    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        JsonDatabase.GuildData guildData = database.getGuildData(event.getGuild().getId());
        if (!guildData.isAntiNukeEnabled() || !guildData.getProtections().getOrDefault("antiRaid", true)) return;
        
        Guild guild = event.getGuild();
        
        // Check for raid pattern
        boolean isRaid = threatDetector.checkRaidPattern(guild.getId());
        
        if (isRaid) {
            logger.warn("RAID DETECTED in guild: {}", guild.getName());
            
            // Anti-bot check
            if (guildData.getProtections().getOrDefault("antiBot", true) && event.getUser().isBot()) {
                // Kick/ban bot accounts during raid
                guild.kick(event.getMember()).queue(
                    success -> logger.info("Kicked bot during raid: {}", event.getUser().getId()),
                    error -> logger.debug("Could not kick bot: {}", event.getUser().getId())
                );
            }
        }
    }
}
