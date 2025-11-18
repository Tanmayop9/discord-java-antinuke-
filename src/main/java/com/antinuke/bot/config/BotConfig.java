package com.antinuke.bot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BotConfig {
    private String token;
    private String ownerId;
    private String prefix;
    private AntiNukeConfig antiNuke;
    private DualMonitoringConfig dualMonitoring;

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static BotConfig load(String path) throws IOException {
        File configFile = new File(path);
        if (!configFile.exists()) {
            BotConfig defaultConfig = createDefault();
            defaultConfig.save(path);
            throw new IOException("Config file not found. Created default config at: " + path + ". Please configure it.");
        }
        return mapper.readValue(configFile, BotConfig.class);
    }

    public void save(String path) throws IOException {
        mapper.writeValue(new File(path), this);
    }

    private static BotConfig createDefault() {
        BotConfig config = new BotConfig();
        config.setToken("YOUR_BOT_TOKEN_HERE");
        config.setOwnerId("YOUR_OWNER_ID");
        config.setPrefix("!");
        
        AntiNukeConfig antiNuke = new AntiNukeConfig();
        antiNuke.setEnabled(true);
        antiNuke.setMaxActionsPerMinute(5);
        antiNuke.setWhitelistedUsers(new ArrayList<>());
        antiNuke.setWhitelistedRoles(new ArrayList<>());
        antiNuke.setPunishmentType("BAN");
        antiNuke.setLogChannelId("");
        antiNuke.setBackupInterval(300);
        
        FastRecoveryConfig fastRecovery = new FastRecoveryConfig();
        fastRecovery.setEnabled(true);
        fastRecovery.setConcurrentOperations(10);
        fastRecovery.setCacheSize(10000);
        fastRecovery.setSnapshotInterval(60);
        antiNuke.setFastRecovery(fastRecovery);
        
        ProtectionsConfig protections = new ProtectionsConfig();
        protections.setAntiBan(true);
        protections.setAntiKick(true);
        protections.setAntiChannelDelete(true);
        protections.setAntiChannelCreate(true);
        protections.setAntiRoleDelete(true);
        protections.setAntiRoleCreate(true);
        protections.setAntiWebhook(true);
        protections.setAntiBot(true);
        protections.setAntiRaid(true);
        protections.setAntiSpam(true);
        protections.setAntiEmoji(true);
        protections.setAntiSticker(true);
        protections.setAntiServerUpdate(true);
        protections.setAntiPrune(true);
        antiNuke.setProtections(protections);
        
        ThresholdsConfig thresholds = new ThresholdsConfig();
        thresholds.setBanThreshold(3);
        thresholds.setKickThreshold(3);
        thresholds.setChannelDeleteThreshold(2);
        thresholds.setChannelCreateThreshold(3);
        thresholds.setRoleDeleteThreshold(2);
        thresholds.setRoleCreateThreshold(3);
        thresholds.setWebhookThreshold(2);
        thresholds.setRaidJoinThreshold(10);
        thresholds.setRaidTimeWindow(10);
        antiNuke.setThresholds(thresholds);
        
        config.setAntiNuke(antiNuke);
        
        DualMonitoringConfig dualMonitoring = new DualMonitoringConfig();
        dualMonitoring.setEnabled(true);
        dualMonitoring.setApiPollInterval(2000);
        dualMonitoring.setUseWebSocket(true);
        dualMonitoring.setUseDirectApi(true);
        config.setDualMonitoring(dualMonitoring);
        
        return config;
    }

    // Getters and Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    
    public AntiNukeConfig getAntiNuke() { return antiNuke; }
    public void setAntiNuke(AntiNukeConfig antiNuke) { this.antiNuke = antiNuke; }
    
    public DualMonitoringConfig getDualMonitoring() { return dualMonitoring; }
    public void setDualMonitoring(DualMonitoringConfig dualMonitoring) { this.dualMonitoring = dualMonitoring; }

    public static class AntiNukeConfig {
        private boolean enabled;
        private int maxActionsPerMinute;
        private List<String> whitelistedUsers;
        private List<String> whitelistedRoles;
        private String punishmentType;
        private String logChannelId;
        private int backupInterval;
        private FastRecoveryConfig fastRecovery;
        private ProtectionsConfig protections;
        private ThresholdsConfig thresholds;

        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getMaxActionsPerMinute() { return maxActionsPerMinute; }
        public void setMaxActionsPerMinute(int maxActionsPerMinute) { this.maxActionsPerMinute = maxActionsPerMinute; }
        
        public List<String> getWhitelistedUsers() { return whitelistedUsers; }
        public void setWhitelistedUsers(List<String> whitelistedUsers) { this.whitelistedUsers = whitelistedUsers; }
        
        public List<String> getWhitelistedRoles() { return whitelistedRoles; }
        public void setWhitelistedRoles(List<String> whitelistedRoles) { this.whitelistedRoles = whitelistedRoles; }
        
        public String getPunishmentType() { return punishmentType; }
        public void setPunishmentType(String punishmentType) { this.punishmentType = punishmentType; }
        
        public String getLogChannelId() { return logChannelId; }
        public void setLogChannelId(String logChannelId) { this.logChannelId = logChannelId; }
        
        public int getBackupInterval() { return backupInterval; }
        public void setBackupInterval(int backupInterval) { this.backupInterval = backupInterval; }
        
        public FastRecoveryConfig getFastRecovery() { return fastRecovery; }
        public void setFastRecovery(FastRecoveryConfig fastRecovery) { this.fastRecovery = fastRecovery; }
        
        public ProtectionsConfig getProtections() { return protections; }
        public void setProtections(ProtectionsConfig protections) { this.protections = protections; }
        
        public ThresholdsConfig getThresholds() { return thresholds; }
        public void setThresholds(ThresholdsConfig thresholds) { this.thresholds = thresholds; }
    }

    public static class FastRecoveryConfig {
        private boolean enabled;
        private int concurrentOperations;
        private int cacheSize;
        private int snapshotInterval;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getConcurrentOperations() { return concurrentOperations; }
        public void setConcurrentOperations(int concurrentOperations) { this.concurrentOperations = concurrentOperations; }
        
        public int getCacheSize() { return cacheSize; }
        public void setCacheSize(int cacheSize) { this.cacheSize = cacheSize; }
        
        public int getSnapshotInterval() { return snapshotInterval; }
        public void setSnapshotInterval(int snapshotInterval) { this.snapshotInterval = snapshotInterval; }
    }

    public static class ProtectionsConfig {
        private boolean antiBan;
        private boolean antiKick;
        private boolean antiChannelDelete;
        private boolean antiChannelCreate;
        private boolean antiRoleDelete;
        private boolean antiRoleCreate;
        private boolean antiWebhook;
        private boolean antiBot;
        private boolean antiRaid;
        private boolean antiSpam;
        private boolean antiEmoji;
        private boolean antiSticker;
        private boolean antiServerUpdate;
        private boolean antiPrune;

        public boolean isAntiBan() { return antiBan; }
        public void setAntiBan(boolean antiBan) { this.antiBan = antiBan; }
        
        public boolean isAntiKick() { return antiKick; }
        public void setAntiKick(boolean antiKick) { this.antiKick = antiKick; }
        
        public boolean isAntiChannelDelete() { return antiChannelDelete; }
        public void setAntiChannelDelete(boolean antiChannelDelete) { this.antiChannelDelete = antiChannelDelete; }
        
        public boolean isAntiChannelCreate() { return antiChannelCreate; }
        public void setAntiChannelCreate(boolean antiChannelCreate) { this.antiChannelCreate = antiChannelCreate; }
        
        public boolean isAntiRoleDelete() { return antiRoleDelete; }
        public void setAntiRoleDelete(boolean antiRoleDelete) { this.antiRoleDelete = antiRoleDelete; }
        
        public boolean isAntiRoleCreate() { return antiRoleCreate; }
        public void setAntiRoleCreate(boolean antiRoleCreate) { this.antiRoleCreate = antiRoleCreate; }
        
        public boolean isAntiWebhook() { return antiWebhook; }
        public void setAntiWebhook(boolean antiWebhook) { this.antiWebhook = antiWebhook; }
        
        public boolean isAntiBot() { return antiBot; }
        public void setAntiBot(boolean antiBot) { this.antiBot = antiBot; }
        
        public boolean isAntiRaid() { return antiRaid; }
        public void setAntiRaid(boolean antiRaid) { this.antiRaid = antiRaid; }
        
        public boolean isAntiSpam() { return antiSpam; }
        public void setAntiSpam(boolean antiSpam) { this.antiSpam = antiSpam; }
        
        public boolean isAntiEmoji() { return antiEmoji; }
        public void setAntiEmoji(boolean antiEmoji) { this.antiEmoji = antiEmoji; }
        
        public boolean isAntiSticker() { return antiSticker; }
        public void setAntiSticker(boolean antiSticker) { this.antiSticker = antiSticker; }
        
        public boolean isAntiServerUpdate() { return antiServerUpdate; }
        public void setAntiServerUpdate(boolean antiServerUpdate) { this.antiServerUpdate = antiServerUpdate; }
        
        public boolean isAntiPrune() { return antiPrune; }
        public void setAntiPrune(boolean antiPrune) { this.antiPrune = antiPrune; }
    }

    public static class ThresholdsConfig {
        private int banThreshold;
        private int kickThreshold;
        private int channelDeleteThreshold;
        private int channelCreateThreshold;
        private int roleDeleteThreshold;
        private int roleCreateThreshold;
        private int webhookThreshold;
        private int raidJoinThreshold;
        private int raidTimeWindow;

        public int getBanThreshold() { return banThreshold; }
        public void setBanThreshold(int banThreshold) { this.banThreshold = banThreshold; }
        
        public int getKickThreshold() { return kickThreshold; }
        public void setKickThreshold(int kickThreshold) { this.kickThreshold = kickThreshold; }
        
        public int getChannelDeleteThreshold() { return channelDeleteThreshold; }
        public void setChannelDeleteThreshold(int channelDeleteThreshold) { this.channelDeleteThreshold = channelDeleteThreshold; }
        
        public int getChannelCreateThreshold() { return channelCreateThreshold; }
        public void setChannelCreateThreshold(int channelCreateThreshold) { this.channelCreateThreshold = channelCreateThreshold; }
        
        public int getRoleDeleteThreshold() { return roleDeleteThreshold; }
        public void setRoleDeleteThreshold(int roleDeleteThreshold) { this.roleDeleteThreshold = roleDeleteThreshold; }
        
        public int getRoleCreateThreshold() { return roleCreateThreshold; }
        public void setRoleCreateThreshold(int roleCreateThreshold) { this.roleCreateThreshold = roleCreateThreshold; }
        
        public int getWebhookThreshold() { return webhookThreshold; }
        public void setWebhookThreshold(int webhookThreshold) { this.webhookThreshold = webhookThreshold; }
        
        public int getRaidJoinThreshold() { return raidJoinThreshold; }
        public void setRaidJoinThreshold(int raidJoinThreshold) { this.raidJoinThreshold = raidJoinThreshold; }
        
        public int getRaidTimeWindow() { return raidTimeWindow; }
        public void setRaidTimeWindow(int raidTimeWindow) { this.raidTimeWindow = raidTimeWindow; }
    }

    public static class DualMonitoringConfig {
        private boolean enabled;
        private int apiPollInterval;
        private boolean useWebSocket;
        private boolean useDirectApi;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getApiPollInterval() { return apiPollInterval; }
        public void setApiPollInterval(int apiPollInterval) { this.apiPollInterval = apiPollInterval; }
        
        public boolean isUseWebSocket() { return useWebSocket; }
        public void setUseWebSocket(boolean useWebSocket) { this.useWebSocket = useWebSocket; }
        
        public boolean isUseDirectApi() { return useDirectApi; }
        public void setUseDirectApi(boolean useDirectApi) { this.useDirectApi = useDirectApi; }
    }
}
