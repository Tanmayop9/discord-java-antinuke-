package com.antinuke.bot.monitoring;

import com.antinuke.bot.config.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Dual Monitoring System - Combines JDA event monitoring with direct Discord API polling
 * for maximum coverage and fastest detection of malicious activities
 */
public class DualMonitoringSystem {
    private static final Logger logger = LoggerFactory.getLogger(DualMonitoringSystem.class);
    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";
    
    private final JDA jda;
    private final String botToken;
    private final BotConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService apiPoller;
    private final Map<String, List<AuditLogEntry>> recentAuditLogs;
    private final ThreatDetectionSystem threatDetector;
    
    public DualMonitoringSystem(JDA jda, String botToken, BotConfig config, ThreatDetectionSystem threatDetector) {
        this.jda = jda;
        this.botToken = botToken;
        this.config = config;
        this.threatDetector = threatDetector;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiPoller = Executors.newScheduledThreadPool(2);
        this.recentAuditLogs = new ConcurrentHashMap<>();
    }
    
    /**
     * Start dual monitoring (JDA + Direct API)
     */
    public void startMonitoring() {
        if (!config.getDualMonitoring().isEnabled()) {
            logger.info("Dual monitoring is disabled in configuration");
            return;
        }
        
        logger.info("Starting dual monitoring system...");
        
        // JDA monitoring is automatic through event listeners
        logger.info("JDA WebSocket monitoring: ACTIVE");
        
        // Start direct API polling for additional coverage
        if (config.getDualMonitoring().isUseDirectApi()) {
            int pollInterval = config.getDualMonitoring().getApiPollInterval();
            startDirectApiPolling(pollInterval);
            logger.info("Direct API polling: ACTIVE (interval: {}ms)", pollInterval);
        }
    }
    
    /**
     * Poll Discord API directly for audit logs (catches events JDA might miss)
     */
    private void startDirectApiPolling(int intervalMs) {
        apiPoller.scheduleAtFixedRate(() -> {
            for (Guild guild : jda.getGuilds()) {
                try {
                    pollAuditLogs(guild);
                } catch (Exception e) {
                    logger.error("Error polling audit logs for guild: {}", guild.getId(), e);
                }
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Directly poll audit logs via Discord API
     */
    private void pollAuditLogs(Guild guild) {
        try {
            String url = DISCORD_API_BASE + "/guilds/" + guild.getId() + "/audit-logs?limit=50";
            
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .build();
            
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String jsonData = response.body().string();
                JsonNode rootNode = objectMapper.readTree(jsonData);
                JsonNode auditLogEntries = rootNode.get("audit_log_entries");
                
                if (auditLogEntries != null && auditLogEntries.isArray()) {
                    List<AuditLogEntry> newEntries = new ArrayList<>();
                    
                    for (JsonNode entry : auditLogEntries) {
                        String actionType = entry.get("action_type").asText();
                        String userId = entry.has("user_id") ? entry.get("user_id").asText() : null;
                        String targetId = entry.has("target_id") ? entry.get("target_id").asText() : null;
                        
                        // Check if this is a new entry we haven't seen
                        String entryId = entry.get("id").asText();
                        if (!isEntryProcessed(guild.getId(), entryId)) {
                            // Process the audit log entry
                            processDirectApiAuditEntry(guild, actionType, userId, targetId, entry);
                            markEntryProcessed(guild.getId(), entryId);
                        }
                    }
                }
            }
            response.close();
            
        } catch (Exception e) {
            logger.debug("Error in direct API polling for guild: {}", guild.getId(), e);
        }
    }
    
    /**
     * Process audit entry from direct API
     */
    private void processDirectApiAuditEntry(Guild guild, String actionType, String userId, String targetId, JsonNode entry) {
        try {
            // Map action types to our threat detection
            switch (actionType) {
                case "20": // MEMBER_KICK
                    threatDetector.recordAction(guild.getId(), userId, ActionType.KICK, targetId);
                    break;
                case "22": // MEMBER_BAN_ADD
                    threatDetector.recordAction(guild.getId(), userId, ActionType.BAN, targetId);
                    break;
                case "10": // CHANNEL_CREATE
                    threatDetector.recordAction(guild.getId(), userId, ActionType.CHANNEL_CREATE, targetId);
                    break;
                case "12": // CHANNEL_DELETE
                    threatDetector.recordAction(guild.getId(), userId, ActionType.CHANNEL_DELETE, targetId);
                    break;
                case "30": // ROLE_CREATE
                    threatDetector.recordAction(guild.getId(), userId, ActionType.ROLE_CREATE, targetId);
                    break;
                case "32": // ROLE_DELETE
                    threatDetector.recordAction(guild.getId(), userId, ActionType.ROLE_DELETE, targetId);
                    break;
                case "50": // WEBHOOK_CREATE
                    threatDetector.recordAction(guild.getId(), userId, ActionType.WEBHOOK_CREATE, targetId);
                    break;
                case "51": // WEBHOOK_UPDATE
                    threatDetector.recordAction(guild.getId(), userId, ActionType.WEBHOOK_UPDATE, targetId);
                    break;
            }
            
            logger.debug("Processed direct API audit entry: type={}, user={}, guild={}", 
                    actionType, userId, guild.getId());
                    
        } catch (Exception e) {
            logger.error("Error processing direct API audit entry", e);
        }
    }
    
    /**
     * Get server information directly via API
     */
    public JsonNode getServerInfoDirect(String guildId) throws IOException {
        String url = DISCORD_API_BASE + "/guilds/" + guildId;
        
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .build();
        
        Response response = httpClient.newCall(request).execute();
        if (response.isSuccessful() && response.body() != null) {
            String jsonData = response.body().string();
            response.close();
            return objectMapper.readTree(jsonData);
        }
        response.close();
        return null;
    }
    
    /**
     * Get members directly via API (for raid detection)
     */
    public JsonNode getMembersDirect(String guildId, int limit) throws IOException {
        String url = DISCORD_API_BASE + "/guilds/" + guildId + "/members?limit=" + limit;
        
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .build();
        
        Response response = httpClient.newCall(request).execute();
        if (response.isSuccessful() && response.body() != null) {
            String jsonData = response.body().string();
            response.close();
            return objectMapper.readTree(jsonData);
        }
        response.close();
        return null;
    }
    
    /**
     * Execute moderation action directly via API (faster than JDA)
     */
    public boolean banUserDirect(String guildId, String userId, String reason) {
        try {
            String url = DISCORD_API_BASE + "/guilds/" + guildId + "/bans/" + userId;
            
            Map<String, Object> body = new HashMap<>();
            body.put("delete_message_seconds", 0);
            if (reason != null) {
                body.put("reason", reason);
            }
            
            RequestBody requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(body),
                    MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                    .url(url)
                    .put(requestBody)
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .header("X-Audit-Log-Reason", reason != null ? reason : "Antinuke protection")
                    .build();
            
            Response response = httpClient.newCall(request).execute();
            boolean success = response.isSuccessful();
            response.close();
            
            if (success) {
                logger.info("Banned user {} in guild {} via direct API", userId, guildId);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error banning user via direct API", e);
            return false;
        }
    }
    
    /**
     * Remove role from user directly via API
     */
    public boolean removeRoleDirect(String guildId, String userId, String roleId) {
        try {
            String url = DISCORD_API_BASE + "/guilds/" + guildId + "/members/" + userId + "/roles/" + roleId;
            
            Request request = new Request.Builder()
                    .url(url)
                    .delete()
                    .header("Authorization", "Bot " + botToken)
                    .header("X-Audit-Log-Reason", "Antinuke protection")
                    .build();
            
            Response response = httpClient.newCall(request).execute();
            boolean success = response.isSuccessful();
            response.close();
            return success;
            
        } catch (Exception e) {
            logger.error("Error removing role via direct API", e);
            return false;
        }
    }
    
    /**
     * Kick user directly via API
     */
    public boolean kickUserDirect(String guildId, String userId, String reason) {
        try {
            String url = DISCORD_API_BASE + "/guilds/" + guildId + "/members/" + userId;
            
            Request request = new Request.Builder()
                    .url(url)
                    .delete()
                    .header("Authorization", "Bot " + botToken)
                    .header("X-Audit-Log-Reason", reason != null ? reason : "Antinuke protection")
                    .build();
            
            Response response = httpClient.newCall(request).execute();
            boolean success = response.isSuccessful();
            response.close();
            
            if (success) {
                logger.info("Kicked user {} in guild {} via direct API", userId, guildId);
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Error kicking user via direct API", e);
            return false;
        }
    }
    
    // Helper methods for tracking processed entries
    private boolean isEntryProcessed(String guildId, String entryId) {
        List<AuditLogEntry> entries = recentAuditLogs.get(guildId);
        if (entries == null) return false;
        return entries.stream().anyMatch(e -> e.getId().equals(entryId));
    }
    
    private void markEntryProcessed(String guildId, String entryId) {
        recentAuditLogs.computeIfAbsent(guildId, k -> new CopyOnWriteArrayList<>());
        // Keep only last 100 entries per guild to prevent memory issues
        List<AuditLogEntry> entries = recentAuditLogs.get(guildId);
        if (entries.size() > 100) {
            entries.remove(0);
        }
    }
    
    public void shutdown() {
        apiPoller.shutdown();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
