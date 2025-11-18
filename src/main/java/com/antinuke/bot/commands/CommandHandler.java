package com.antinuke.bot.commands;

import com.antinuke.bot.config.BotConfig;
import com.antinuke.bot.database.JsonDatabase;
import com.antinuke.bot.recovery.FastRecoverySystem;
import com.antinuke.bot.ui.InteractiveUI;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command Handler - Handles both slash commands and prefix commands
 */
public class CommandHandler extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    
    private final BotConfig config;
    private final JsonDatabase database;
    private final FastRecoverySystem recoverySystem;
    
    public CommandHandler(BotConfig config, JsonDatabase database, FastRecoverySystem recoverySystem) {
        this.config = config;
        this.database = database;
        this.recoverySystem = recoverySystem;
    }
    
    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        
        // Check permissions
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permission to use antinuke commands!").setEphemeral(true).queue();
            return;
        }
        
        switch (commandName) {
            case "antinuke":
                handleAntiNukeCommand(event);
                break;
            case "whitelist":
                handleWhitelistCommand(event);
                break;
            case "snapshot":
                handleSnapshotCommand(event);
                break;
            case "recover":
                handleRecoverCommand(event);
                break;
        }
    }
    
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;
        
        String content = event.getMessage().getContentRaw();
        String prefix = config.getPrefix();
        
        if (!content.startsWith(prefix)) return;
        
        String[] args = content.substring(prefix.length()).split("\\s+");
        String command = args[0].toLowerCase();
        
        // Check permissions
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.getChannel().sendMessage("❌ You need Administrator permission to use antinuke commands!").queue();
            return;
        }
        
        switch (command) {
            case "antinuke":
            case "dashboard":
                event.getChannel().sendMessageEmbeds(InteractiveUI.createDashboardEmbed(event.getGuild(), config))
                        .setComponents(InteractiveUI.createDashboardButtons())
                        .queue();
                break;
                
            case "whitelist":
                if (args.length < 3) {
                    event.getChannel().sendMessage("Usage: `" + prefix + "whitelist <add|remove> <user|role> <@mention|ID>`").queue();
                    return;
                }
                handlePrefixWhitelist(event, args);
                break;
                
            case "snapshot":
                recoverySystem.createSnapshot(event.getGuild());
                event.getChannel().sendMessage("📸 Server snapshot created successfully!").queue();
                break;
                
            case "recover":
                if (args.length < 2) {
                    event.getChannel().sendMessage("Usage: `" + prefix + "recover <full|roles|channels>`").queue();
                    return;
                }
                handlePrefixRecover(event, args);
                break;
                
            case "help":
                sendHelpMessage(event);
                break;
        }
    }
    
    private void handleAntiNukeCommand(SlashCommandInteractionEvent event) {
        event.replyEmbeds(InteractiveUI.createDashboardEmbed(event.getGuild(), config))
                .setComponents(InteractiveUI.createDashboardButtons())
                .queue();
    }
    
    private void handleWhitelistCommand(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        String action = event.getOption("action") != null ? event.getOption("action").getAsString() : "view";
        
        if (action.equals("view")) {
            event.replyEmbeds(InteractiveUI.createWhitelistEmbed(config)).setEphemeral(true).queue();
            return;
        }
        
        String type = event.getOption("type") != null ? event.getOption("type").getAsString() : null;
        String id = event.getOption("id") != null ? event.getOption("id").getAsString() : null;
        
        if (type == null || id == null) {
            event.reply("❌ Please specify type and ID").setEphemeral(true).queue();
            return;
        }
        
        if (action.equals("add")) {
            if (type.equals("user")) {
                guildData.getWhitelistedUsers().add(id);
                event.reply("✅ Added user to whitelist: <@" + id + ">").setEphemeral(true).queue();
            } else {
                guildData.getWhitelistedRoles().add(id);
                event.reply("✅ Added role to whitelist: <@&" + id + ">").setEphemeral(true).queue();
            }
        } else if (action.equals("remove")) {
            if (type.equals("user")) {
                guildData.getWhitelistedUsers().remove(id);
                event.reply("✅ Removed user from whitelist: <@" + id + ">").setEphemeral(true).queue();
            } else {
                guildData.getWhitelistedRoles().remove(id);
                event.reply("✅ Removed role from whitelist: <@&" + id + ">").setEphemeral(true).queue();
            }
        }
        
        database.saveGuildData(guildId, guildData);
    }
    
    private void handleSnapshotCommand(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        recoverySystem.createSnapshot(event.getGuild());
        event.getHook().sendMessage("📸 Server snapshot created successfully! All server state has been cached for instant recovery.").queue();
    }
    
    private void handleRecoverCommand(SlashCommandInteractionEvent event) {
        String type = event.getOption("type") != null ? event.getOption("type").getAsString() : "full";
        
        event.deferReply(true).queue();
        
        if (type.equals("full")) {
            event.getHook().sendMessage("🔄 Starting full server recovery...").queue();
            recoverySystem.fullServerRecovery(event.getGuild()).thenAccept(result -> {
                event.getHook().sendMessage(String.format(
                        "✅ Full recovery completed!\n" +
                        "**Items Recovered:** %d\n" +
                        "**Status:** %s",
                        result.itemsRecovered,
                        result.message
                )).queue();
            });
        }
    }
    
    private void handlePrefixWhitelist(MessageReceivedEvent event, String[] args) {
        String guildId = event.getGuild().getId();
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        String action = args[1].toLowerCase();
        String type = args[2].toLowerCase();
        
        if (args.length < 4) {
            event.getChannel().sendMessage("❌ Please mention a user or provide an ID").queue();
            return;
        }
        
        String id = args[3].replaceAll("[^0-9]", "");
        
        if (action.equals("add")) {
            if (type.equals("user")) {
                guildData.getWhitelistedUsers().add(id);
                event.getChannel().sendMessage("✅ Added user to whitelist: <@" + id + ">").queue();
            } else if (type.equals("role")) {
                guildData.getWhitelistedRoles().add(id);
                event.getChannel().sendMessage("✅ Added role to whitelist: <@&" + id + ">").queue();
            }
        } else if (action.equals("remove")) {
            if (type.equals("user")) {
                guildData.getWhitelistedUsers().remove(id);
                event.getChannel().sendMessage("✅ Removed user from whitelist: <@" + id + ">").queue();
            } else if (type.equals("role")) {
                guildData.getWhitelistedRoles().remove(id);
                event.getChannel().sendMessage("✅ Removed role from whitelist: <@&" + id + ">").queue();
            }
        }
        
        database.saveGuildData(guildId, guildData);
    }
    
    private void handlePrefixRecover(MessageReceivedEvent event, String[] args) {
        String type = args[1].toLowerCase();
        
        if (type.equals("full")) {
            event.getChannel().sendMessage("🔄 Starting full server recovery...").queue();
            recoverySystem.fullServerRecovery(event.getGuild()).thenAccept(result -> {
                event.getChannel().sendMessage(String.format(
                        "✅ Full recovery completed!\n" +
                        "**Items Recovered:** %d\n" +
                        "**Status:** %s",
                        result.itemsRecovered,
                        result.message
                )).queue();
            });
        }
    }
    
    private void sendHelpMessage(MessageReceivedEvent event) {
        String prefix = config.getPrefix();
        String help = "**🛡️ Advanced Antinuke Bot - Commands**\n\n" +
                "**Dashboard**\n" +
                "`" + prefix + "antinuke` - Open interactive dashboard\n" +
                "`" + prefix + "dashboard` - Alias for antinuke\n\n" +
                "**Whitelist Management**\n" +
                "`" + prefix + "whitelist add user @user` - Add user to whitelist\n" +
                "`" + prefix + "whitelist add role @role` - Add role to whitelist\n" +
                "`" + prefix + "whitelist remove user @user` - Remove user from whitelist\n" +
                "`" + prefix + "whitelist remove role @role` - Remove role from whitelist\n\n" +
                "**Recovery**\n" +
                "`" + prefix + "snapshot` - Create server snapshot\n" +
                "`" + prefix + "recover full` - Full server recovery\n\n" +
                "**Info**\n" +
                "`" + prefix + "help` - Show this message\n\n" +
                "**Features:**\n" +
                "✨ Dual monitoring (JDA + Direct API)\n" +
                "✨ Fastest recovery system\n" +
                "✨ Interactive buttons & menus\n" +
                "✨ Advanced threat detection\n" +
                "✨ Superior to Wick & Zeon";
        
        event.getChannel().sendMessage(help).queue();
    }
}
