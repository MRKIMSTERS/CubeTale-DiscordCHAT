package com.cubetale.discordchat.console;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ConsoleManager {

    private final CubeTaleDiscordChat plugin;
    private ConsoleLogHandler logHandler;

    public ConsoleManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (!plugin.getConfigManager().isConsoleEnabled()) return;

        String consoleChannelId = plugin.getConfigManager().getConsoleChannelId();
        if (consoleChannelId == null || consoleChannelId.isEmpty() || consoleChannelId.equals("CONSOLE_CHANNEL_ID")) {
            plugin.getPluginLogger().info("Console channel not configured, console forwarding disabled.");
            return;
        }

        logHandler = new ConsoleLogHandler(plugin, consoleChannelId);

        // Attach to root logger to capture all server output
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(logHandler);

        plugin.getPluginLogger().info("Console forwarding to Discord channel: " + consoleChannelId);
    }

    public void shutdown() {
        if (logHandler != null) {
            Logger rootLogger = Logger.getLogger("");
            rootLogger.removeHandler(logHandler);
            logHandler = null;
        }
    }

    /**
     * Custom Java Logging Handler that forwards log records to Discord.
     */
    private static class ConsoleLogHandler extends Handler {

        private final CubeTaleDiscordChat plugin;
        private final String channelId;
        private final List<String> blockedPatterns;
        private long lastSendTime = 0;
        private final StringBuilder messageBuffer = new StringBuilder();
        private static final long BUFFER_FLUSH_MS = 1000L;

        ConsoleLogHandler(CubeTaleDiscordChat plugin, String channelId) {
            this.plugin = plugin;
            this.channelId = channelId;
            this.blockedPatterns = plugin.getConfig().getStringList("console.blocked-patterns");
            setLevel(Level.INFO);
        }

        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) return;
            if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;

            String message = record.getMessage();
            if (message == null || message.trim().isEmpty()) return;

            // Filter sensitive info
            if (plugin.getConfigManager().isFilterSensitive()) {
                for (String patternStr : blockedPatterns) {
                    try {
                        Pattern pattern = Pattern.compile(patternStr);
                        if (pattern.matcher(message).find()) {
                            message = pattern.matcher(message).replaceAll("[FILTERED]");
                        }
                    } catch (Exception e) {
                        // Ignore bad patterns
                    }
                }
            }

            // Avoid sending our own Discord messages back to Discord (infinite loop prevention)
            if (message.contains("[CubeTale-DiscordCHAT]") &&
                    (message.contains("Sent") || message.contains("webhook"))) {
                return;
            }

            String levelPrefix = getEmoji(record.getLevel());
            String formatted = levelPrefix + " `" + sanitizeForDiscord(message) + "`";

            // Buffer and send
            final String finalFormatted = formatted;
            synchronized (messageBuffer) {
                if (messageBuffer.length() > 0) messageBuffer.append("\n");
                messageBuffer.append(finalFormatted);

                // Flush if buffer is large enough or enough time has passed
                if (messageBuffer.length() > 1800) {
                    flushBuffer();
                }
            }
        }

        private void flushBuffer() {
            if (messageBuffer.length() == 0) return;
            String toSend = messageBuffer.toString();
            messageBuffer.setLength(0);

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getDiscordBot().isConnected()) {
                    TextChannel channel = plugin.getDiscordBot().getJda().getTextChannelById(channelId);
                    if (channel != null) {
                        // Split if too long for Discord's 2000 char limit
                        if (toSend.length() <= 2000) {
                            channel.sendMessage(toSend).queue(null, e -> {});
                        } else {
                            String[] parts = toSend.split("\n");
                            StringBuilder chunk = new StringBuilder();
                            for (String part : parts) {
                                if (chunk.length() + part.length() + 1 > 1900) {
                                    channel.sendMessage(chunk.toString()).queue(null, e -> {});
                                    chunk = new StringBuilder();
                                }
                                if (chunk.length() > 0) chunk.append("\n");
                                chunk.append(part);
                            }
                            if (chunk.length() > 0) {
                                channel.sendMessage(chunk.toString()).queue(null, e -> {});
                            }
                        }
                    }
                }
            });
        }

        private String getEmoji(Level level) {
            if (level == Level.SEVERE) return "🔴";
            if (level == Level.WARNING) return "🟡";
            return "⬜";
        }

        private String sanitizeForDiscord(String message) {
            // Escape backtick for code formatting
            return message.replace("`", "'").substring(0, Math.min(message.length(), 1800));
        }

        @Override
        public void flush() {
            synchronized (messageBuffer) {
                flushBuffer();
            }
        }

        @Override
        public void close() throws SecurityException {
            flush();
        }
    }
}
