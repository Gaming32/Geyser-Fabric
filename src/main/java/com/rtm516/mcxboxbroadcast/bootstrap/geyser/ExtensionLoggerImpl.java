package com.rtm516.mcxboxbroadcast.bootstrap.geyser;

import com.rtm516.mcxboxbroadcast.core.Logger;

import org.geysermc.geyser.GeyserLogger;

public class ExtensionLoggerImpl implements Logger {
    private GeyserLogger logger;

    public ExtensionLoggerImpl(GeyserLogger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warning(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void error(String message, Throwable ex) {
        logger.error(message, ex);
    }

    @Override
    public void debug(String message) {
        logger.debug(message);
    }
}
