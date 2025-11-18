package com.antinuke.bot.listeners;

import com.antinuke.bot.database.JsonDatabase;
import com.antinuke.bot.recovery.FastRecoverySystem;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guild Event Listener - Handles bot joining/leaving servers for scalability
 */
public class GuildEventListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildEventListener.class);
    
    private final JsonDatabase database;
    private final FastRecoverySystem recoverySystem;
    
    public GuildEventListener(JsonDatabase database, FastRecoverySystem recoverySystem) {
        this.database = database;
        this.recoverySystem = recoverySystem;
    }
    
    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();
        
        // Initialize guild data if not exists
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        logger.info("Guild ready: {} ({})", guildName, guildId);
        
        // Create initial snapshot
        recoverySystem.createSnapshot(event.getGuild());
        guildData.setLastSnapshot(System.currentTimeMillis());
        database.saveGuildData(guildId, guildData);
        
        // Schedule periodic snapshots
        recoverySystem.scheduleSnapshots(event.getGuild(), 60);
        
        logger.info("Initialized protection for guild: {}", guildName);
    }
    
    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();
        
        logger.info("✅ Bot joined new guild: {} ({}) - Total guilds: {}", 
                guildName, guildId, event.getJDA().getGuilds().size());
        
        // Create guild configuration
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        database.saveGuildData(guildId, guildData);
        database.flushGuildData(guildId);
        
        // Create initial snapshot
        recoverySystem.createSnapshot(event.getGuild());
        guildData.setLastSnapshot(System.currentTimeMillis());
        database.saveGuildData(guildId, guildData);
        
        // Schedule periodic snapshots
        recoverySystem.scheduleSnapshots(event.getGuild(), 60);
        
        // Send welcome message to system channel if available
        if (event.getGuild().getSystemChannel() != null) {
            event.getGuild().getSystemChannel().sendMessage(
                    "**🛡️ Advanced Antinuke Bot**\n\n" +
                    "Thank you for adding me to your server!\n\n" +
                    "**Features:**\n" +
                    "✨ Dual monitoring (JDA + Direct API)\n" +
                    "✨ Fastest recovery system\n" +
                    "✨ Interactive dashboard with buttons\n" +
                    "✨ Superior to Wick & Zeon\n" +
                    "✨ Scalable for large servers\n\n" +
                    "**Get Started:**\n" +
                    "• Use `/antinuke` or `!antinuke` to open the dashboard\n" +
                    "• Configure protections and whitelist\n" +
                    "• All protections are enabled by default\n\n" +
                    "**Need Help?**\n" +
                    "Use `!help` to see all commands"
            ).queue(
                    success -> logger.info("Sent welcome message to guild: {}", guildName),
                    error -> logger.debug("Could not send welcome message to guild: {}", guildName)
            );
        }
        
        logger.info("Setup completed for new guild: {}", guildName);
    }
    
    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();
        
        logger.info("❌ Bot left guild: {} ({}) - Remaining guilds: {}", 
                guildName, guildId, event.getJDA().getGuilds().size());
        
        // Create final backup before removal (optional - can be added if needed)
        // database.createBackup(guildId);
        
        // Note: We don't remove the guild data immediately in case bot is re-added
        // Data will be kept for potential re-join
        
        logger.info("Guild data preserved for: {}", guildName);
    }
}
