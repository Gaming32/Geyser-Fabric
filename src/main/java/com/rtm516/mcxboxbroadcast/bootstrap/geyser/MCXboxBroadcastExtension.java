package com.rtm516.mcxboxbroadcast.bootstrap.geyser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.steveice10.mc.protocol.codec.MinecraftCodec;
import com.rtm516.mcxboxbroadcast.core.Logger;
import com.rtm516.mcxboxbroadcast.core.SessionInfo;
import com.rtm516.mcxboxbroadcast.core.SessionManager;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionCreationException;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionUpdateException;

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;

public class MCXboxBroadcastExtension {
    private final GeyserImpl geyser;
    private final GeyserLogger geyserLogger;
    private final Path dataFolder;

    Logger logger;
    SessionManager sessionManager;
    SessionInfo sessionInfo;
    ExtensionConfig config;

    public MCXboxBroadcastExtension(GeyserImpl geyser, Path dataFolder) {
        this.geyser = geyser;
        this.geyserLogger = geyser.getLogger();
        this.dataFolder = dataFolder;
    }

    public void onPostInitialize() {
        logger = new ExtensionLoggerImpl(geyserLogger);
        sessionManager = new SessionManager(this.dataFolder().toString(), logger);

        File configFile = this.dataFolder().resolve("config.yml").toFile();

        // Create the config file if it doesn't exist
        if (!configFile.exists()) {
            try (FileWriter writer = new FileWriter(configFile)) {
                try (InputStream input = getClass().getResourceAsStream("/mcxbox-broadcast/config.yml")) {
                    byte[] bytes = new byte[input.available()];

                    input.read(bytes);

                    writer.write(new String(bytes).toCharArray());

                    writer.flush();
                }
            } catch (IOException e) {
                logger.error("Failed to create config", e);
                return;
            }
        }

        try {
            config = new ObjectMapper(new YAMLFactory()).readValue(configFile, ExtensionConfig.class);
        } catch (IOException e) {
            logger.error("Failed to load config", e);
            return;
        }

        // Pull onto another thread so we don't hang the main thread
        new Thread(() -> {
            logger.info("Setting up Xbox session...");

            // Get the ip to broadcast
            String ip = config.remoteAddress;
            if (ip.equals("auto")) {
                // Taken from core Geyser code
                ip = geyser.getConfig().getBedrock().getAddress();
                try {
                    // This is the most reliable for getting the main local IP
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress("geysermc.org", 80));
                    ip = socket.getLocalAddress().getHostAddress();
                } catch (IOException e1) {
                    try {
                        // Fallback to the normal way of getting the local IP
                        ip = InetAddress.getLocalHost().getHostAddress();
                    } catch (UnknownHostException ignored) {
                    }
                }
            }

            // Get the port to broadcast
            int port = geyser.getConfig().getBedrock().getPort();
            if (!config.remotePort.equals("auto")) {
                port = Integer.parseInt(config.remotePort);
            }

            // Create the session information based on the Geyser config
            sessionInfo = new SessionInfo();
            sessionInfo.setHostName(geyser.getConfig().getBedrock().getMotd1());
            sessionInfo.setWorldName(geyser.getConfig().getBedrock().getMotd2());
            sessionInfo.setVersion(MinecraftCodec.CODEC.getMinecraftVersion());
            sessionInfo.setProtocol(MinecraftCodec.CODEC.getProtocolVersion());
            sessionInfo.setPlayers(geyser.getSessionManager().getAllSessions().size());
            sessionInfo.setMaxPlayers(geyser.getConfig().getMaxPlayers());

            sessionInfo.setIp(ip);
            sessionInfo.setPort(port);

            // Create the Xbox session
            try {
                sessionManager.createSession(sessionInfo);
                logger.info("Created Xbox session!");
            } catch (SessionCreationException | SessionUpdateException e) {
                logger.error("Failed to create xbox session!", e);
                return;
            }

            // Start the update timer
            GeyserImpl.getInstance().getScheduledThread().scheduleWithFixedDelay(this::tick, config.updateInterval, config.updateInterval, TimeUnit.SECONDS);
        }).start();
    }

    private void tick() {
        // Make sure the connection is still active
        sessionManager.checkConnection();

        // Update the player count for the session
        try {
            sessionInfo.setPlayers(geyser.getSessionManager().getAllSessions().size());
            sessionManager.updateSession(sessionInfo);
        } catch (SessionUpdateException e) {
            logger.error("Failed to update session information!", e);
        }
    }

    private Path dataFolder() {
        return dataFolder;
    }
}
