package org.atcgroup.mcupdater.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import me.gb2022.commons.container.Pair;
import org.atcgroup.mcupdater.FileRepository;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class NettyHttpFileServer implements AutoCloseable {
    private final Map<String, FileRepository> buckets = new ConcurrentHashMap<>();
    private final ExecutorService fileExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup workers = new NioEventLoopGroup();

    private final AtomicBoolean started = new AtomicBoolean();

    private Channel serverChannel;

    public void start(int port) throws InterruptedException {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Server already started");
        }

        try {
            ServerBootstrap bootstrap = new ServerBootstrap().group(boss, workers).channel(NioServerSocketChannel.class).childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true).childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast("httpCodec", new HttpServerCodec()).addLast("httpAggregator", new HttpObjectAggregator(16 * 1024)).addLast("fileHandler", new FileHandler());
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
    public void close() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }

        fileExecutor.shutdown();
        boss.shutdownGracefully().syncUninterruptibly();
        workers.shutdownGracefully().syncUninterruptibly();
    }

    /**
     * 非文件数据源扩展接口。
     * <p>
     * 当前版本不实现该接口的读取流程。
     * 后续可用于内存数据、对象存储或自定义数据源。
     */
    public interface NonFileDataSource {
        long size();

        String contentType();

        // 后续可扩展按 offset/length 读取的异步接口。
    }

    /**
     * 可覆盖的非文件数据源路由占位方法。
     */
    protected NonFileDataSource router2source(String requestPath) {
        return null;
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
            FileChannel file = null;

            try {
                if (!ctx.channel().isActive()) {
                    return;
                }

                var pair = router2file(path);

                if (pair == null) {
                    sendError(ctx, NOT_FOUND, keepAlive);
                    return;
                }

                var holder = pair.getLeft();
                var filePath = pair.getRight();

                if (filePath == null) {
                    sendError(ctx, NOT_FOUND, keepAlive);
                    return;
                }

                BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

                long fileSize = attrs.size();

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
                    sendResponse(ctx, response, keepAlive);
                    return;
                }

                file = FileChannel.open(filePath, StandardOpenOption.READ);
                holder.addReadLock(filePath.toFile(), file.toString());

                FileChannel openedFile = file;
                long position = start;
                long count = length;

                // 切回 EventLoop 写响应；DefaultFileRegion 负责异步文件传输。
                ctx.executor().execute(() -> {
                    if (!ctx.channel().isActive()) {
                        closeQuietly(holder, filePath.toFile(), openedFile);
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
                        closeQuietly(holder, filePath.toFile(), openedFile);
                        ctx.close();
                    }
                });

                file = null; // 所有权交给 DefaultFileRegion。
            } catch (NoSuchFileException e) {
                sendError(ctx, NOT_FOUND, keepAlive);
            } catch (IOException e) {
                sendError(ctx, INTERNAL_SERVER_ERROR, keepAlive);
            } catch (RejectedExecutionException e) {
                sendError(ctx, SERVICE_UNAVAILABLE, keepAlive);
            } finally {
                closeQuietly(null, null, file);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }

    private static void sendResponse(ChannelHandlerContext ctx, HttpResponse response, boolean keepAlive) {
        ctx.executor().execute(() -> {
            ChannelFuture future = ctx.writeAndFlush(response);

            future.addListener(f -> {
                if (!f.isSuccess() || !keepAlive) {
                    ctx.close();
                }
            });
        });
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

    private static void closeQuietly(FileRepository holder, File file, FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
            }
            if (file != null && holder != null) {
                holder.removeReadLock(file, channel.toString());
            }
        }
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

    public void registerBucket(String name, FileRepository directory) {
        buckets.put(name, directory);
    }

    /**
     * 将 URL 路径映射到本地文件。
     * <p>
     * 子类可以覆盖此方法实现自定义路由。
     * 返回 null 表示路径不存在。
     */
    protected Pair<FileRepository, Path> router2file(String requestPath) throws IOException {
        // URL 路径由 QueryStringDecoder 解析，不在这里处理查询参数。
        String path = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;

        int slash = path.indexOf('/');

        if (slash <= 0 || slash == path.length() - 1) {
            return null;
        }

        String bucketName = path.substring(0, slash);
        String relativePath = path.substring(slash + 1);

        var bucket = buckets.get(bucketName);
        if (bucket == null) {
            return null;
        }

        // 拒绝路径穿越。
        Path candidate = bucket.getRoot().resolve(relativePath).normalize();

        if (!candidate.startsWith(bucket.getRoot())) {
            return null;
        }

        if (!Files.isRegularFile(candidate)) {
            return null;
        }

        // 防止符号链接逃逸出 bucket 目录。
        Path realPath = candidate.toRealPath();

        if (!realPath.startsWith(bucket.getRoot())) {
            return null;
        }

        return new Pair<>(bucket, realPath);
    }
}