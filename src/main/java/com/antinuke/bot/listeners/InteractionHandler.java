package com.antinuke.bot.listeners;

import com.antinuke.bot.config.BotConfig;
import com.antinuke.bot.database.JsonDatabase;
import com.antinuke.bot.recovery.FastRecoverySystem;
import com.antinuke.bot.ui.InteractiveUI;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Interaction Handler - Handles button clicks and select menu selections
 */
public class InteractionHandler extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(InteractionHandler.class);
    
    private final BotConfig config;
    private final JsonDatabase database;
    private final FastRecoverySystem recoverySystem;
    
    public InteractionHandler(BotConfig config, JsonDatabase database, FastRecoverySystem recoverySystem) {
        this.config = config;
        this.database = database;
        this.recoverySystem = recoverySystem;
    }
    
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        String guildId = event.getGuild().getId();
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        
        // Only allow administrators
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permission to use this!").setEphemeral(true).queue();
            return;
        }
        
        switch (buttonId) {
            case "antinuke:protections":
                event.editMessageEmbeds(InteractiveUI.createProtectionsEmbed(config))
                        .setActionRow(InteractiveUI.createProtectionSelectMenu())
                        .queue();
                break;
                
            case "antinuke:whitelist":
                event.editMessageEmbeds(InteractiveUI.createWhitelistEmbed(config))
                        .setComponents()
                        .queue();
                break;
                
            case "antinuke:recovery":
                event.editMessageEmbeds(InteractiveUI.createRecoveryEmbed(config))
                        .setComponents(InteractiveUI.createRecoveryButtons())
                        .queue();
                break;
                
            case "antinuke:settings":
                event.editMessageEmbeds(InteractiveUI.createPunishmentEmbed(config))
                        .setActionRow(InteractiveUI.createPunishmentSelectMenu())
                        .queue();
                break;
                
            case "antinuke:enable":
                guildData.setAntiNukeEnabled(true);
                database.saveGuildData(guildId, guildData);
                event.reply("✅ Antinuke protection **enabled**!").setEphemeral(true).queue();
                updateDashboard(event);
                break;
                
            case "antinuke:disable":
                guildData.setAntiNukeEnabled(false);
                database.saveGuildData(guildId, guildData);
                event.reply("❌ Antinuke protection **disabled**!").setEphemeral(true).queue();
                updateDashboard(event);
                break;
                
            case "antinuke:snapshot":
                event.deferReply(true).queue();
                if (event.getGuild() != null) {
                    recoverySystem.createSnapshot(event.getGuild());
                    event.getHook().sendMessage("📸 Server snapshot created successfully!").queue();
                }
                break;
                
            case "antinuke:refresh":
                updateDashboard(event);
                event.reply("🔄 Dashboard refreshed!").setEphemeral(true).queue();
                break;
                
            case "recovery:snapshot":
                event.deferReply(true).queue();
                if (event.getGuild() != null) {
                    recoverySystem.createSnapshot(event.getGuild());
                    event.getHook().sendMessage("📸 Server snapshot created successfully! All current server state has been cached for instant recovery.").queue();
                }
                break;
                
            case "recovery:roles":
                event.reply("⏳ Role recovery system ready. Use `/recover roles` command to specify roles to recover.").setEphemeral(true).queue();
                break;
                
            case "recovery:channels":
                event.reply("⏳ Channel recovery system ready. Use `/recover channels` command to specify channels to recover.").setEphemeral(true).queue();
                break;
                
            case "recovery:full":
                event.deferReply(true).queue();
                if (event.getGuild() != null) {
                    event.getHook().sendMessage("🔄 Starting full server recovery... This may take a moment.").queue();
                    recoverySystem.fullServerRecovery(event.getGuild()).thenAccept(result -> {
                        event.getHook().sendMessage(String.format(
                                "✅ Full recovery completed!\n" +
                                "**Status:** %s\n" +
                                "**Items Recovered:** %d\n" +
                                "**Message:** %s",
                                result.success ? "Success" : "Failed",
                                result.itemsRecovered,
                                result.message
                        )).queue();
                    });
                }
                break;
                
            case "recovery:back":
                event.editMessageEmbeds(InteractiveUI.createDashboardEmbed(event.getGuild(), config))
                        .setComponents(InteractiveUI.createDashboardButtons())
                        .queue();
                break;
        }
    }
    
    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String menuId = event.getComponentId();
        String guildId = event.getGuild().getId();
        JsonDatabase.GuildData guildData = database.getGuildData(guildId);
        
        // Only allow administrators
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permission to use this!").setEphemeral(true).queue();
            return;
        }
        
        if (menuId.equals("antinuke:toggle_protection")) {
            StringBuilder response = new StringBuilder("🔄 Updated protections:\n\n");
            
            for (String value : event.getValues()) {
                boolean newState = toggleProtection(guildData, value);
                response.append(String.format("• %s: %s\n", 
                        formatProtectionName(value), 
                        newState ? "✅ Enabled" : "❌ Disabled"));
            }
            
            database.saveGuildData(guildId, guildData);
            event.reply(response.toString()).setEphemeral(true).queue();
            
        } else if (menuId.equals("antinuke:punishment_type")) {
            String newType = event.getValues().get(0);
            guildData.setPunishmentType(newType);
            database.saveGuildData(guildId, guildData);
            event.reply("✅ Punishment type updated to: **" + newType + "**").setEphemeral(true).queue();
        }
    }
    
    private boolean toggleProtection(JsonDatabase.GuildData guildData, String protectionId) {
        Map<String, Boolean> protections = guildData.getProtections();
        
        switch (protectionId) {
            case "antiBan":
                boolean newBan = !protections.getOrDefault("antiBan", true);
                protections.put("antiBan", newBan);
                return newBan;
            case "antiKick":
                boolean newKick = !protections.getOrDefault("antiKick", true);
                protections.put("antiKick", newKick);
                return newKick;
            case "antiChannelDelete":
                boolean newChDel = !protections.getOrDefault("antiChannelDelete", true);
                protections.put("antiChannelDelete", newChDel);
                return newChDel;
            case "antiChannelCreate":
                boolean newChCr = !protections.getOrDefault("antiChannelCreate", true);
                protections.put("antiChannelCreate", newChCr);
                return newChCr;
            case "antiRoleDelete":
                boolean newRDel = !protections.getOrDefault("antiRoleDelete", true);
                protections.put("antiRoleDelete", newRDel);
                return newRDel;
            case "antiRoleCreate":
                boolean newRCr = !protections.getOrDefault("antiRoleCreate", true);
                protections.put("antiRoleCreate", newRCr);
                return newRCr;
            case "antiWebhook":
                boolean newWh = !protections.getOrDefault("antiWebhook", true);
                protections.put("antiWebhook", newWh);
                return newWh;
            case "antiBot":
                boolean newBot = !protections.getOrDefault("antiBot", true);
                protections.put("antiBot", newBot);
                return newBot;
            case "antiRaid":
                boolean newRaid = !protections.getOrDefault("antiRaid", true);
                protections.put("antiRaid", newRaid);
                return newRaid;
        }
        return false;
    }
    
    private String formatProtectionName(String protectionId) {
        return protectionId.replaceAll("([A-Z])", " $1")
                .replace("anti", "Anti-")
                .trim();
    }
    
    private void updateDashboard(ButtonInteractionEvent event) {
        if (event.getGuild() != null) {
            event.editMessageEmbeds(InteractiveUI.createDashboardEmbed(event.getGuild(), config))
                    .setComponents(InteractiveUI.createDashboardButtons())
                    .queue();
        }
    }
}
