package me.gb2022.mcupdater.rtp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import me.gb2022.mcupdater.storage.CASResourceStorage;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

public final class HttpResourceServer implements AutoCloseable {
    private final CASResourceStorage storage;
    private final ExecutorService fileExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime()
                                                                                                 .availableProcessors() / 2));
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup workers = new NioEventLoopGroup();

    private final AtomicBoolean started = new AtomicBoolean();
    private boolean closed;

    private volatile Channel serverChannel;

    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public java.net.InetSocketAddress getLocalAddress() {
        if (!isRunning()) throw new IllegalStateException("HTTP server not running");
        return (java.net.InetSocketAddress) serverChannel.localAddress();
    }

    public HttpResourceServer(CASResourceStorage storage) {
        this.storage = storage;
    }

    public synchronized void start(int port) throws InterruptedException {
        if (this.closed) throw new IllegalStateException("HTTP server is closed");
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Server already started");
        }

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                              .addLast("httpCodec", new HttpServerCodec())
                              .addLast("httpAggregator", new HttpObjectAggregator(16 * 1024))
                              .addLast("fileHandler", new FileHandler());
                        }
                    });

            serverChannel = bootstrap.bind(port).sync().channel();
        } catch (InterruptedException | RuntimeException e) {
            started.set(false);
            throw e;
        }
    }

    public void await() throws InterruptedException {
        if (serverChannel == null) {
            throw new IllegalStateException("Server not started");
        }

        serverChannel.closeFuture().sync();
    }

    @Override
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;
        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close().syncUninterruptibly();
            }
        } finally {
            fileExecutor.shutdown();
            try {
                boss.shutdownGracefully().syncUninterruptibly();
            } finally {
                workers.shutdownGracefully().syncUninterruptibly();
            }
        }
    }

    public CASResourceStorage getStorage() {
        return storage;
    }

    private final class FileHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            if (!request.decoderResult().isSuccess()) {
                sendError(ctx, BAD_REQUEST, false);
                return;
            }

            HttpMethod method = request.method();

            if (!method.equals(HttpMethod.GET) && !method.equals(HttpMethod.HEAD)) {
                sendError(ctx, METHOD_NOT_ALLOWED, HttpUtil.isKeepAlive(request));
                return;
            }

            final String path;

            try {
                path = new QueryStringDecoder(request.uri()).path();
            } catch (IllegalArgumentException e) {
                sendError(ctx, BAD_REQUEST, HttpUtil.isKeepAlive(request));
                return;
            }

            final boolean keepAlive = HttpUtil.isKeepAlive(request);
            final boolean headOnly = method.equals(HttpMethod.HEAD);
            final String rangeHeader = request.headers().get(RANGE);

            try {
                fileExecutor.execute(() -> openAndSend(ctx, path, rangeHeader, keepAlive, headOnly));
            } catch (RejectedExecutionException e) {
                sendError(ctx, SERVICE_UNAVAILABLE, keepAlive);
            }
        }

        private void openAndSend(ChannelHandlerContext ctx, String path, String rangeHeader, boolean keepAlive, boolean headOnly) {
            FileChannel channel = null;

            try {
                if (!ctx.channel().isActive()) {
                    return;
                }

                var filePath = getFileFor(path);

                if (filePath == null) {
                    sendError(ctx, NOT_FOUND, keepAlive);
                    return;
                }

                var attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
                var fileSize = attrs.size();

                ByteRange range;
                try {
                    range = parseRange(rangeHeader, fileSize);
                } catch (InvalidRangeException e) {
                    sendRangeError(ctx, fileSize, keepAlive);
                    return;
                }

                long start = range == null ? 0 : range.start;
                long length = range == null ? fileSize : range.end - range.start + 1;

                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                HttpResponseStatus status = range == null ? OK : PARTIAL_CONTENT;

                DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);

                response.headers().set(CONTENT_TYPE, contentType);
                response.headers().set(CONTENT_LENGTH, length);
                response.headers().set(ACCEPT_RANGES, "bytes");
                response.headers().set(CACHE_CONTROL, "private, max-age=0");

                if (range != null) {
                    response.headers().set(CONTENT_RANGE, "bytes " + range.start + "-" + range.end + "/" + fileSize);
                }

                if (keepAlive) {
                    response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                } else {
                    response.headers().set(CONNECTION, HttpHeaderValues.CLOSE);
                }

                // HEAD 只返回响应头，不发送正文。
                if (headOnly || length == 0) {
                    ctx.executor().execute(() -> {
                        ctx.write(response);
                        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(sent -> {
                            if (!sent.isSuccess() || !keepAlive) ctx.close();
                        });
                    });
                    return;
                }

                channel = FileChannel.open(filePath, StandardOpenOption.READ);

                FileChannel openedFile = channel;
                long position = start;
                long count = length;

                // 切回 EventLoop 写响应；DefaultFileRegion 负责异步文件传输。
                ctx.executor().execute(() -> {
                    if (!ctx.channel().isActive()) {
                        closeQuietly(filePath.toFile(), openedFile);
                        return;
                    }

                    try {
                        ctx.write(response);
                        ctx.writeAndFlush(new DefaultFileRegion(openedFile, position, count));
                        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(f -> {
                            if (!f.isSuccess()) {
                                ctx.close();
                            } else if (!keepAlive) {
                                ctx.close();
                            }
                        });
                    } catch (Throwable t) {
                        closeQuietly(filePath.toFile(), openedFile);
                        ctx.close();
                    }
                });

                channel = null; // 所有权交给 DefaultFileRegion。
            } catch (NoSuchFileException e) {
                sendError(ctx, NOT_FOUND, keepAlive);
            } catch (IOException e) {
                sendError(ctx, INTERNAL_SERVER_ERROR, keepAlive);
            } catch (RejectedExecutionException e) {
                sendError(ctx, SERVICE_UNAVAILABLE, keepAlive);
            } finally {
                closeQuietly(null, channel);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, boolean keepAlive) {
        ctx.executor().execute(() -> {
            byte[] body = (status.code() + " " + status.reasonPhrase() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(body));

            response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(CONTENT_LENGTH, body.length);

            if (keepAlive) {
                response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                response.headers().set(CONNECTION, HttpHeaderValues.CLOSE);
            }

            ctx.writeAndFlush(response).addListener(f -> {
                if (!f.isSuccess() || !keepAlive) {
                    ctx.close();
                }
            });
        });
    }

    private static void sendRangeError(ChannelHandlerContext ctx, long fileSize, boolean keepAlive) {
        ctx.executor().execute(() -> {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, REQUESTED_RANGE_NOT_SATISFIABLE, Unpooled.EMPTY_BUFFER);

            response.headers().set(CONTENT_LENGTH, 0);
            response.headers().set(CONTENT_RANGE, "bytes */" + fileSize);
            response.headers().set(ACCEPT_RANGES, "bytes");

            if (keepAlive) {
                response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            } else {
                response.headers().set(CONNECTION, HttpHeaderValues.CLOSE);
            }

            ctx.writeAndFlush(response).addListener(f -> {
                if (!f.isSuccess() || !keepAlive) {
                    ctx.close();
                }
            });
        });
    }

    private static ByteRange parseRange(String header, long fileSize) throws InvalidRangeException {
        if (header == null || header.isBlank()) {
            return null;
        }

        if (!header.startsWith("bytes=") || header.contains(",")) {
            throw new InvalidRangeException();
        }

        String value = header.substring("bytes=".length()).trim();
        int dash = value.indexOf('-');

        if (dash < 0) {
            throw new InvalidRangeException();
        }

        try {
            String left = value.substring(0, dash).trim();
            String right = value.substring(dash + 1).trim();

            long start;
            long end;

            if (left.isEmpty()) {
                // bytes=-N：文件末尾 N 个字节。
                long suffixLength = Long.parseLong(right);

                if (suffixLength <= 0 || fileSize == 0) {
                    throw new InvalidRangeException();
                }

                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? fileSize - 1 : Long.parseLong(right);

                if (start < 0 || start >= fileSize || end < start) {
                    throw new InvalidRangeException();
                }

                end = Math.min(end, fileSize - 1);
            }

            if (fileSize == 0 || start >= fileSize || end < start) {
                throw new InvalidRangeException();
            }

            return new ByteRange(start, end);
        } catch (NumberFormatException e) {
            throw new InvalidRangeException();
        }
    }

    private void closeQuietly(File file, FileChannel channel) {
        if (channel != null && file != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
        }
    }

    private Path getFileFor(String requestPath) throws IOException {
        // URL 路径由 QueryStringDecoder 解析，不在这里处理查询参数。
        var path = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        var handle = this.storage.getResource(path);

        return handle.map(File::toPath).orElse(null);
    }

    private static final class ByteRange {
        final long start;
        final long end;

        ByteRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class InvalidRangeException extends Exception {
    }
}
