package org.atcgroup.mcupdater.network.handler.test;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TimingHandler extends ChannelDuplexHandler {
    private static final Logger LOGGER = LogManager.getLogger("NetworkTiming");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        long last = System.currentTimeMillis();
        super.channelRead(ctx, msg);
        LOGGER.info("[{}] IN: {}", ctx.channel().remoteAddress(), System.currentTimeMillis() - last);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        long last = System.currentTimeMillis();

        promise.addListener((f)-> LOGGER.info("[{}] OUT: {}", ctx.channel().remoteAddress(), System.currentTimeMillis() - last));

        super.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        long last = System.currentTimeMillis();
        ctx.flush();
        LOGGER.info("[{}] OUT-WRITE: {}", ctx.channel().remoteAddress(), System.currentTimeMillis() - last);
    }
}
