package com.antinuke.bot.ui;

import com.antinuke.bot.config.BotConfig;
import com.antinuke.bot.utils.ExecutionTimer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive UI Builder - Creates buttons and select menus for antinuke management
 */
public class InteractiveUI {
    
    /**
     * Create main dashboard embed with navigation buttons
     */
    public static MessageEmbed createDashboardEmbed(Guild guild, BotConfig config) {
        return createDashboardEmbed(guild, config, null);
    }
    
    public static MessageEmbed createDashboardEmbed(Guild guild, BotConfig config, ExecutionTimer timer) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Advanced Antinuke Dashboard")
                .setDescription("**Server:** " + guild.getName() + "\n" +
                        "**Status:** " + (config.getAntiNuke().isEnabled() ? "✅ Active" : "❌ Disabled"))
                .setColor(config.getAntiNuke().isEnabled() ? Color.GREEN : Color.RED)
                .addField("🔒 Protections", 
                        String.format(
                                "Anti-Ban: %s\n" +
                                "Anti-Kick: %s\n" +
                                "Anti-Channel: %s\n" +
                                "Anti-Role: %s\n" +
                                "Anti-Raid: %s\n" +
                                "Anti-Bot: %s",
                                getStatus(config.getAntiNuke().getProtections().isAntiBan()),
                                getStatus(config.getAntiNuke().getProtections().isAntiKick()),
                                getStatus(config.getAntiNuke().getProtections().isAntiChannelDelete()),
                                getStatus(config.getAntiNuke().getProtections().isAntiRoleDelete()),
                                getStatus(config.getAntiNuke().getProtections().isAntiRaid()),
                                getStatus(config.getAntiNuke().getProtections().isAntiBot())
                        ), true)
                .addField("⚡ Fast Recovery", 
                        String.format(
                                "Enabled: %s\n" +
                                "Concurrent Ops: %d\n" +
                                "Cache Size: %d\n" +
                                "Snapshot Interval: %ds",
                                getStatus(config.getAntiNuke().getFastRecovery().isEnabled()),
                                config.getAntiNuke().getFastRecovery().getConcurrentOperations(),
                                config.getAntiNuke().getFastRecovery().getCacheSize(),
                                config.getAntiNuke().getFastRecovery().getSnapshotInterval()
                        ), true)
                .addField("⚙️ Settings", 
                        String.format(
                                "Punishment: %s\n" +
                                "Max Actions/Min: %d\n" +
                                "Whitelisted Users: %d",
                                config.getAntiNuke().getPunishmentType(),
                                config.getAntiNuke().getMaxActionsPerMinute(),
                                config.getAntiNuke().getWhitelistedUsers().size()
                        ), true)
                .setFooter(timer != null ? timer.getFooterText() : "Use the buttons below to configure")
                .setThumbnail(guild.getIconUrl());
        
        return embed.build();
    }
    
    /**
     * Create navigation buttons for dashboard
     */
    public static List<ActionRow> createDashboardButtons() {
        List<ActionRow> rows = new ArrayList<>();
        
        rows.add(ActionRow.of(
                Button.primary("antinuke:protections", "🔒 Protections")
                        .withEmoji(Emoji.fromUnicode("🔒")),
                Button.primary("antinuke:whitelist", "👥 Whitelist")
                        .withEmoji(Emoji.fromUnicode("👥")),
                Button.primary("antinuke:recovery", "⚡ Recovery")
                        .withEmoji(Emoji.fromUnicode("⚡")),
                Button.primary("antinuke:settings", "⚙️ Settings")
                        .withEmoji(Emoji.fromUnicode("⚙️"))
        ));
        
        rows.add(ActionRow.of(
                Button.success("antinuke:enable", "Enable Antinuke")
                        .withEmoji(Emoji.fromUnicode("✅")),
                Button.danger("antinuke:disable", "Disable Antinuke")
                        .withEmoji(Emoji.fromUnicode("❌")),
                Button.secondary("antinuke:snapshot", "📸 Create Snapshot")
                        .withEmoji(Emoji.fromUnicode("📸")),
                Button.secondary("antinuke:refresh", "🔄 Refresh")
                        .withEmoji(Emoji.fromUnicode("🔄"))
        ));
        
        return rows;
    }
    
    /**
     * Create protections configuration embed
     */
    public static MessageEmbed createProtectionsEmbed(BotConfig config) {
        return createProtectionsEmbed(config, null);
    }
    
    public static MessageEmbed createProtectionsEmbed(BotConfig config, ExecutionTimer timer) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Protection Settings")
                .setDescription("Configure which protections are active")
                .setColor(Color.BLUE)
                .addField("Member Protections", 
                        String.format(
                                "Anti-Ban: %s\n" +
                                "Anti-Kick: %s\n" +
                                "Anti-Prune: %s",
                                getStatus(config.getAntiNuke().getProtections().isAntiBan()),
                                getStatus(config.getAntiNuke().getProtections().isAntiKick()),
                                getStatus(config.getAntiNuke().getProtections().isAntiPrune())
                        ), true)
                .addField("Channel Protections", 
                        String.format(
                                "Anti-Delete: %s\n" +
                                "Anti-Create: %s",
                                getStatus(config.getAntiNuke().getProtections().isAntiChannelDelete()),
                                getStatus(config.getAntiNuke().getProtections().isAntiChannelCreate())
                        ), true)
                .addField("Role Protections", 
                        String.format(
                                "Anti-Delete: %s\n" +
                                "Anti-Create: %s",
                                getStatus(config.getAntiNuke().getProtections().isAntiRoleDelete()),
                                getStatus(config.getAntiNuke().getProtections().isAntiRoleCreate())
                        ), true)
                .addField("Other Protections", 
                        String.format(
                                "Anti-Webhook: %s\n" +
                                "Anti-Bot: %s\n" +
                                "Anti-Raid: %s\n" +
                                "Anti-Spam: %s\n" +
                                "Anti-Emoji: %s\n" +
                                "Anti-Sticker: %s\n" +
                                "Anti-Server Update: %s",
                                getStatus(config.getAntiNuke().getProtections().isAntiWebhook()),
                                getStatus(config.getAntiNuke().getProtections().isAntiBot()),
                                getStatus(config.getAntiNuke().getProtections().isAntiRaid()),
                                getStatus(config.getAntiNuke().getProtections().isAntiSpam()),
                                getStatus(config.getAntiNuke().getProtections().isAntiEmoji()),
                                getStatus(config.getAntiNuke().getProtections().isAntiSticker()),
                                getStatus(config.getAntiNuke().getProtections().isAntiServerUpdate())
                        ), false)
                .setFooter(timer != null ? timer.getFooterText() : "Select protections to configure");
        
        return embed.build();
    }
    
    /**
     * Create protection toggle select menu
     */
    public static StringSelectMenu createProtectionSelectMenu() {
        return StringSelectMenu.create("antinuke:toggle_protection")
                .setPlaceholder("Select protection to toggle")
                .addOptions(
                        SelectOption.of("Anti-Ban", "antiBan")
                                .withDescription("Protect against mass bans")
                                .withEmoji(Emoji.fromUnicode("🔨")),
                        SelectOption.of("Anti-Kick", "antiKick")
                                .withDescription("Protect against mass kicks")
                                .withEmoji(Emoji.fromUnicode("👢")),
                        SelectOption.of("Anti-Channel Delete", "antiChannelDelete")
                                .withDescription("Protect against channel deletion")
                                .withEmoji(Emoji.fromUnicode("💬")),
                        SelectOption.of("Anti-Channel Create", "antiChannelCreate")
                                .withDescription("Protect against spam channels")
                                .withEmoji(Emoji.fromUnicode("➕")),
                        SelectOption.of("Anti-Role Delete", "antiRoleDelete")
                                .withDescription("Protect against role deletion")
                                .withEmoji(Emoji.fromUnicode("🎭")),
                        SelectOption.of("Anti-Role Create", "antiRoleCreate")
                                .withDescription("Protect against spam roles")
                                .withEmoji(Emoji.fromUnicode("🆕")),
                        SelectOption.of("Anti-Webhook", "antiWebhook")
                                .withDescription("Protect against webhook abuse")
                                .withEmoji(Emoji.fromUnicode("🔗")),
                        SelectOption.of("Anti-Bot", "antiBot")
                                .withDescription("Protect against bot raids")
                                .withEmoji(Emoji.fromUnicode("🤖")),
                        SelectOption.of("Anti-Raid", "antiRaid")
                                .withDescription("Protect against member raids")
                                .withEmoji(Emoji.fromUnicode("🚨"))
                )
                .setMaxValues(9)
                .build();
    }
    
    /**
     * Create recovery panel embed
     */
    public static MessageEmbed createRecoveryEmbed(BotConfig config) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚡ Fast Recovery System")
                .setDescription("The fastest recovery system for Discord servers")
                .setColor(Color.ORANGE)
                .addField("Recovery Settings", 
                        String.format(
                                "**Status:** %s\n" +
                                "**Concurrent Operations:** %d threads\n" +
                                "**Cache Size:** %d items\n" +
                                "**Snapshot Interval:** %d seconds\n" +
                                "**Backup Interval:** %d seconds",
                                config.getAntiNuke().getFastRecovery().isEnabled() ? "✅ Enabled" : "❌ Disabled",
                                config.getAntiNuke().getFastRecovery().getConcurrentOperations(),
                                config.getAntiNuke().getFastRecovery().getCacheSize(),
                                config.getAntiNuke().getFastRecovery().getSnapshotInterval(),
                                config.getAntiNuke().getBackupInterval()
                        ), false)
                .addField("Features", 
                        "✨ Instant role restoration\n" +
                        "✨ Rapid channel recovery\n" +
                        "✨ Mass unban capability\n" +
                        "✨ Parallel operations for maximum speed\n" +
                        "✨ Automatic snapshots\n" +
                        "✨ High-performance caching", false)
                .setFooter("Use buttons below for recovery actions");
        
        return embed.build();
    }
    
    /**
     * Create recovery action buttons
     */
    public static List<ActionRow> createRecoveryButtons() {
        List<ActionRow> rows = new ArrayList<>();
        
        rows.add(ActionRow.of(
                Button.success("recovery:snapshot", "📸 Create Snapshot")
                        .withEmoji(Emoji.fromUnicode("📸")),
                Button.primary("recovery:roles", "🎭 Recover Roles")
                        .withEmoji(Emoji.fromUnicode("🎭")),
                Button.primary("recovery:channels", "💬 Recover Channels")
                        .withEmoji(Emoji.fromUnicode("💬")),
                Button.danger("recovery:full", "🔄 Full Recovery")
                        .withEmoji(Emoji.fromUnicode("🔄"))
        ));
        
        rows.add(ActionRow.of(
                Button.secondary("recovery:back", "⬅️ Back to Dashboard")
                        .withEmoji(Emoji.fromUnicode("⬅️"))
        ));
        
        return rows;
    }
    
    /**
     * Create whitelist management embed
     */
    public static MessageEmbed createWhitelistEmbed(BotConfig config) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👥 Whitelist Management")
                .setDescription("Manage trusted users and roles")
                .setColor(Color.CYAN)
                .addField("Whitelisted Users", 
                        config.getAntiNuke().getWhitelistedUsers().isEmpty() ? 
                                "No users whitelisted" : 
                                String.join("\n", config.getAntiNuke().getWhitelistedUsers()
                                        .stream()
                                        .map(id -> "<@" + id + ">")
                                        .toList()), 
                        false)
                .addField("Whitelisted Roles", 
                        config.getAntiNuke().getWhitelistedRoles().isEmpty() ? 
                                "No roles whitelisted" : 
                                String.join("\n", config.getAntiNuke().getWhitelistedRoles()
                                        .stream()
                                        .map(id -> "<@&" + id + ">")
                                        .toList()), 
                        false)
                .setFooter("Whitelisted users/roles bypass all antinuke protections");
        
        return embed.build();
    }
    
    /**
     * Create punishment settings embed
     */
    public static MessageEmbed createPunishmentEmbed(BotConfig config) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚖️ Punishment Settings")
                .setDescription("Configure how the bot punishes malicious users")
                .setColor(Color.RED)
                .addField("Current Punishment Type", 
                        "**" + config.getAntiNuke().getPunishmentType() + "**", false)
                .addField("Available Types", 
                        "• **BAN** - Permanently ban the user\n" +
                        "• **KICK** - Kick the user from server\n" +
                        "• **STRIP_ROLES** - Remove all roles from user", false);
        
        return embed.build();
    }
    
    /**
     * Create punishment type select menu
     */
    public static StringSelectMenu createPunishmentSelectMenu() {
        return StringSelectMenu.create("antinuke:punishment_type")
                .setPlaceholder("Select punishment type")
                .addOptions(
                        SelectOption.of("Ban", "BAN")
                                .withDescription("Permanently ban malicious users")
                                .withEmoji(Emoji.fromUnicode("🔨"))
                                .withDefault(false),
                        SelectOption.of("Kick", "KICK")
                                .withDescription("Kick malicious users from server")
                                .withEmoji(Emoji.fromUnicode("👢"))
                                .withDefault(false),
                        SelectOption.of("Strip Roles", "STRIP_ROLES")
                                .withDescription("Remove all roles from malicious users")
                                .withEmoji(Emoji.fromUnicode("🎭"))
                                .withDefault(false)
                )
                .build();
    }
    
    private static String getStatus(boolean enabled) {
        return enabled ? "✅" : "❌";
    }
}
