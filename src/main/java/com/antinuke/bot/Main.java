package com.antinuke.bot;

import com.antinuke.bot.commands.CommandHandler;
import com.antinuke.bot.config.BotConfig;
import com.antinuke.bot.database.JsonDatabase;
import com.antinuke.bot.listeners.AntiNukeListener;
import com.antinuke.bot.listeners.GuildEventListener;
import com.antinuke.bot.listeners.InteractionHandler;
import com.antinuke.bot.monitoring.DualMonitoringSystem;
import com.antinuke.bot.monitoring.ThreatDetectionSystem;
import com.antinuke.bot.recovery.FastRecoverySystem;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Main Bot Class - Advanced Discord Antinuke Bot
 * Features:
 * - Dual Monitoring (JDA WebSocket + Direct API)
 * - Fast Recovery System with concurrent operations
 * - Interactive UI with buttons and select menus
 * - Superior protection compared to Wick and Zeon
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static BotConfig config;
    private static JDA jda;
    private static JsonDatabase database;
    private static FastRecoverySystem recoverySystem;
    private static ThreatDetectionSystem threatDetector;
    private static DualMonitoringSystem dualMonitoring;
    
    public static void main(String[] args) {
        try {
            logger.info("╔═══════════════════════════════════════════════════════╗");
            logger.info("║   Advanced Discord Antinuke Bot                      ║");
            logger.info("║   Superior to Wick & Zeon                            ║");
            logger.info("║   Dual Monitoring | Fast Recovery | Interactive UI   ║");
            logger.info("║   Scalable for Large Server Deployments              ║");
            logger.info("╚═══════════════════════════════════════════════════════╝");
            
            // Initialize JSON Database
            logger.info("Initializing JSON Database...");
            database = new JsonDatabase();
            database.loadAllGuilds();
            logger.info("✓ JSON Database initialized");
            
            // Load configuration
            String configPath = "src/main/resources/config.json";
            if (args.length > 0) {
                configPath = args[0];
            }
            
            logger.info("Loading configuration from: {}", configPath);
            config = BotConfig.load(configPath);
            
            if (config.getToken().equals("YOUR_BOT_TOKEN_HERE")) {
                logger.error("Please configure your bot token in config.json!");
                System.exit(1);
            }
            
            // Initialize Fast Recovery System
            BotConfig.FastRecoveryConfig recoveryConfig = config.getAntiNuke().getFastRecovery();
            logger.info("Initializing Fast Recovery System...");
            recoverySystem = new FastRecoverySystem(
                    recoveryConfig.getCacheSize(),
                    recoveryConfig.getConcurrentOperations(),
                    recoveryConfig.getSnapshotInterval()
            );
            logger.info("✓ Fast Recovery System initialized with {} concurrent operations", 
                    recoveryConfig.getConcurrentOperations());
            
            // Build JDA with all necessary intents
            logger.info("Building JDA instance...");
            jda = JDABuilder.createDefault(config.getToken())
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MODERATION,
                            GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
                            GatewayIntent.GUILD_WEBHOOKS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_PRESENCES
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .enableCache(CacheFlag.ROLE_TAGS, CacheFlag.MEMBER_OVERRIDES)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.watching("for threats | Dual Monitoring Active"))
                    .build();
            
            logger.info("Waiting for JDA to be ready...");
            jda.awaitReady();
            logger.info("✓ JDA is ready!");
            
            // Initialize Threat Detection System
            logger.info("Initializing Threat Detection System...");
            threatDetector = new ThreatDetectionSystem(config, jda, database, recoverySystem);
            logger.info("✓ Threat Detection System initialized");
            
            // Initialize Dual Monitoring System
            logger.info("Initializing Dual Monitoring System...");
            dualMonitoring = new DualMonitoringSystem(jda, config.getToken(), config, threatDetector);
            dualMonitoring.startMonitoring();
            logger.info("✓ Dual Monitoring System active");
            
            // Register event listeners
            logger.info("Registering event listeners...");
            jda.addEventListener(new AntiNukeListener(config, database, threatDetector, recoverySystem));
            jda.addEventListener(new CommandHandler(config, database, recoverySystem));
            jda.addEventListener(new InteractionHandler(config, database, recoverySystem));
            jda.addEventListener(new GuildEventListener(database, recoverySystem));
            logger.info("✓ Event listeners registered");
            
            // Register slash commands
            logger.info("Registering slash commands...");
            registerCommands();
            logger.info("✓ Slash commands registered");
            
            // Create initial snapshots for all guilds
            logger.info("Creating initial snapshots...");
            for (Guild guild : jda.getGuilds()) {
                recoverySystem.createSnapshot(guild);
                if (recoveryConfig.isEnabled()) {
                    recoverySystem.scheduleSnapshots(guild, recoveryConfig.getSnapshotInterval());
                }
                logger.info("✓ Snapshot created for guild: {}", guild.getName());
            }
            
            logger.info("╔═══════════════════════════════════════════════════════╗");
            logger.info("║   Bot is now ONLINE and PROTECTING {} servers!      ║", jda.getGuilds().size());
            logger.info("║   Antinuke: {}                                      ║", 
                    config.getAntiNuke().isEnabled() ? "ENABLED " : "DISABLED");
            logger.info("║   Fast Recovery: {}                                 ║", 
                    recoveryConfig.isEnabled() ? "ENABLED " : "DISABLED");
            logger.info("║   Dual Monitoring: {}                               ║", 
                    config.getDualMonitoring().isEnabled() ? "ENABLED " : "DISABLED");
            logger.info("╚═══════════════════════════════════════════════════════╝");
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down bot...");
                shutdown();
            }));
            
        } catch (Exception e) {
            logger.error("Fatal error starting bot:", e);
            System.exit(1);
        }
    }
    
    private static void registerCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("antinuke", "Open the antinuke dashboard"),
                
                Commands.slash("whitelist", "Manage whitelist")
                        .addOptions(
                                new OptionData(OptionType.STRING, "action", "Action to perform", true)
                                        .addChoice("Add", "add")
                                        .addChoice("Remove", "remove")
                                        .addChoice("View", "view"),
                                new OptionData(OptionType.STRING, "type", "User or role", false)
                                        .addChoice("User", "user")
                                        .addChoice("Role", "role"),
                                new OptionData(OptionType.STRING, "id", "User or role ID", false)
                        ),
                
                Commands.slash("snapshot", "Create a server snapshot for fast recovery"),
                
                Commands.slash("recover", "Recover server state")
                        .addOptions(
                                new OptionData(OptionType.STRING, "type", "What to recover", true)
                                        .addChoice("Full Server", "full")
                                        .addChoice("Roles", "roles")
                                        .addChoice("Channels", "channels")
                        )
        ).queue(
                success -> logger.info("Slash commands registered successfully"),
                error -> logger.error("Failed to register slash commands", error)
        );
    }
    
    private static void shutdown() {
        try {
            if (database != null) {
                database.shutdown();
            }
            if (recoverySystem != null) {
                recoverySystem.shutdown();
            }
            if (dualMonitoring != null) {
                dualMonitoring.shutdown();
            }
            if (threatDetector != null) {
                threatDetector.shutdown();
            }
            if (jda != null) {
                jda.shutdown();
            }
            logger.info("Bot shutdown complete");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
    
    public static BotConfig getConfig() {
        return config;
    }
    
    public static JDA getJDA() {
        return jda;
    }
    
    public static FastRecoverySystem getRecoverySystem() {
        return recoverySystem;
    }
    
    public static ThreatDetectionSystem getThreatDetector() {
        return threatDetector;
    }
    
    public static DualMonitoringSystem getDualMonitoring() {
        return dualMonitoring;
    }
    
    public static JsonDatabase getDatabase() {
        return database;
    }
}
