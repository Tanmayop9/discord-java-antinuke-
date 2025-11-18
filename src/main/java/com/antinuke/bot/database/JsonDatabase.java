package com.antinuke.bot.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Custom JSON Database - Ultra-fast with optimized I/O
 */
public class JsonDatabase {
    private static final Logger logger = LoggerFactory.getLogger(JsonDatabase.class);
    private static final String DB_DIR = "database";
    private static final String GUILDS_DIR = DB_DIR + "/guilds";
    private static final String BACKUPS_DIR = DB_DIR + "/backups";
    
    private final ObjectMapper mapper;
    private final Map<String, GuildData> cache;
    private final Map<String, ReadWriteLock> locks;
    private final ScheduledExecutorService autoSaveExecutor;
    private final Set<String> modifiedGuilds;
    
    public JsonDatabase() {
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.cache = new ConcurrentHashMap<>(1024); // Optimized initial capacity
        this.locks = new ConcurrentHashMap<>(1024);
        this.modifiedGuilds = ConcurrentHashMap.newKeySet();
        this.autoSaveExecutor = Executors.newScheduledThreadPool(2);
        
        initializeDirectories();
        autoSaveExecutor.scheduleAtFixedRate(this::autoSave, 15, 15, TimeUnit.SECONDS); // Faster saves
        logger.info("JSON Database initialized with optimized performance");
    }
    
    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(GUILDS_DIR));
            Files.createDirectories(Paths.get(BACKUPS_DIR));
        } catch (IOException e) {
            logger.error("Failed to create database directories", e);
        }
    }
    
    public GuildData getGuildData(String guildId) {
        return cache.computeIfAbsent(guildId, id -> {
            locks.putIfAbsent(id, new ReentrantReadWriteLock());
            ReadWriteLock lock = locks.get(id);
            lock.readLock().lock();
            try {
                return loadGuildData(id);
            } finally {
                lock.readLock().unlock();
            }
        });
    }
    
    public void saveGuildData(String guildId, GuildData data) {
        cache.put(guildId, data);
        modifiedGuilds.add(guildId);
    }
    
    public void flushGuildData(String guildId) {
        GuildData data = cache.get(guildId);
        if (data != null) {
            locks.putIfAbsent(guildId, new ReentrantReadWriteLock());
            ReadWriteLock lock = locks.get(guildId);
            lock.writeLock().lock();
            try {
                saveGuildDataToDisk(guildId, data);
                modifiedGuilds.remove(guildId);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    public void loadAllGuilds() {
        File guildsDir = new File(GUILDS_DIR);
        File[] files = guildsDir.listFiles((dir, name) -> name.endsWith(".json"));
        
        if (files != null) {
            int loaded = 0;
            for (File file : files) {
                String guildId = file.getName().replace(".json", "");
                try {
                    cache.put(guildId, loadGuildData(guildId));
                    loaded++;
                } catch (Exception e) {
                    logger.error("Failed to load guild data: {}", guildId, e);
                }
            }
            logger.info("Loaded {} guild configurations", loaded);
        }
    }
    
    private void autoSave() {
        if (modifiedGuilds.isEmpty()) return;
        
        Set<String> toSave = new HashSet<>(modifiedGuilds);
        for (String guildId : toSave) {
            try {
                flushGuildData(guildId);
            } catch (Exception e) {
                logger.error("Auto-save failed for guild: {}", guildId, e);
            }
        }
    }
    
    private GuildData loadGuildData(String guildId) {
        File file = new File(GUILDS_DIR + "/" + guildId + ".json");
        
        if (!file.exists()) {
            return new GuildData(guildId);
        }
        
        try {
            return mapper.readValue(file, GuildData.class);
        } catch (IOException e) {
            logger.error("Failed to load guild data: {}", guildId, e);
            return new GuildData(guildId);
        }
    }
    
    private void saveGuildDataToDisk(String guildId, GuildData data) {
        File file = new File(GUILDS_DIR + "/" + guildId + ".json");
        
        try {
            mapper.writeValue(file, data);
        } catch (IOException e) {
            logger.error("Failed to save guild data: {}", guildId, e);
        }
    }
    
    public void shutdown() {
        logger.info("Shutting down database...");
        autoSaveExecutor.shutdown();
        
        for (String guildId : new HashSet<>(modifiedGuilds)) {
            flushGuildData(guildId);
        }
        
        logger.info("Database shutdown complete");
    }
    
    // GuildData class with setup configuration
    public static class GuildData {
        private String guildId;
        private boolean antiNukeEnabled = true;
        private boolean setupComplete = false;
        private List<String> whitelistedUsers = new ArrayList<>();
        private List<String> whitelistedRoles = new ArrayList<>();
        private String punishmentType = "BAN";
        private String logChannelId = "";
        private String logCategoryId = "";
        private String bypassRoleId = "";
        private Map<String, Boolean> protections = new HashMap<>();
        private Map<String, Integer> thresholds = new HashMap<>();
        private long lastSnapshot = 0;
        private int totalThreatsBlocked = 0;
        private int totalRecoveries = 0;
        
        public GuildData() {}
        
        public GuildData(String guildId) {
            this.guildId = guildId;
            initializeDefaults();
        }
        
        private void initializeDefaults() {
            protections.put("antiBan", true);
            protections.put("antiKick", true);
            protections.put("antiChannelDelete", true);
            protections.put("antiChannelCreate", true);
            protections.put("antiRoleDelete", true);
            protections.put("antiRoleCreate", true);
            protections.put("antiWebhook", true);
            protections.put("antiBot", true);
            protections.put("antiRaid", true);
            protections.put("antiSpam", true);
            
            thresholds.put("ban", 3);
            thresholds.put("kick", 3);
            thresholds.put("channelDelete", 2);
            thresholds.put("channelCreate", 3);
            thresholds.put("roleDelete", 2);
            thresholds.put("roleCreate", 3);
            thresholds.put("webhook", 2);
            thresholds.put("raid", 10);
        }
        
        // Getters and Setters
        public String getGuildId() { return guildId; }
        public void setGuildId(String guildId) { this.guildId = guildId; }
        
        public boolean isAntiNukeEnabled() { return antiNukeEnabled; }
        public void setAntiNukeEnabled(boolean antiNukeEnabled) { this.antiNukeEnabled = antiNukeEnabled; }
        
        public boolean isSetupComplete() { return setupComplete; }
        public void setSetupComplete(boolean setupComplete) { this.setupComplete = setupComplete; }
        
        public List<String> getWhitelistedUsers() { return whitelistedUsers; }
        public void setWhitelistedUsers(List<String> whitelistedUsers) { this.whitelistedUsers = whitelistedUsers; }
        
        public List<String> getWhitelistedRoles() { return whitelistedRoles; }
        public void setWhitelistedRoles(List<String> whitelistedRoles) { this.whitelistedRoles = whitelistedRoles; }
        
        public String getPunishmentType() { return punishmentType; }
        public void setPunishmentType(String punishmentType) { this.punishmentType = punishmentType; }
        
        public String getLogChannelId() { return logChannelId; }
        public void setLogChannelId(String logChannelId) { this.logChannelId = logChannelId; }
        
        public String getLogCategoryId() { return logCategoryId; }
        public void setLogCategoryId(String logCategoryId) { this.logCategoryId = logCategoryId; }
        
        public String getBypassRoleId() { return bypassRoleId; }
        public void setBypassRoleId(String bypassRoleId) { this.bypassRoleId = bypassRoleId; }
        
        public Map<String, Boolean> getProtections() { return protections; }
        public void setProtections(Map<String, Boolean> protections) { this.protections = protections; }
        
        public Map<String, Integer> getThresholds() { return thresholds; }
        public void setThresholds(Map<String, Integer> thresholds) { this.thresholds = thresholds; }
        
        public long getLastSnapshot() { return lastSnapshot; }
        public void setLastSnapshot(long lastSnapshot) { this.lastSnapshot = lastSnapshot; }
        
        public int getTotalThreatsBlocked() { return totalThreatsBlocked; }
        public void setTotalThreatsBlocked(int totalThreatsBlocked) { this.totalThreatsBlocked = totalThreatsBlocked; }
        
        public int getTotalRecoveries() { return totalRecoveries; }
        public void setTotalRecoveries(int totalRecoveries) { this.totalRecoveries = totalRecoveries; }
        
        public void incrementThreatsBlocked() { this.totalThreatsBlocked++; }
        public void incrementRecoveries() { this.totalRecoveries++; }
    }
}
