package org.atcgroup.mcupdater.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ErrorCatchHandler extends ChannelInboundHandlerAdapter {
    public static final Logger LOGGER = LogManager.getLogger("NetworkErrorHandler");

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("An network error occurred in network pipeline {}", ctx.channel());
        LOGGER.catching(cause);
    }
}
