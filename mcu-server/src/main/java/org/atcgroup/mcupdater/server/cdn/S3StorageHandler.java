package org.atcgroup.mcupdater.server.cdn;

import me.gb2022.commons.file.FilePath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atcgroup.mcupdater.FileRepository;
import org.atcgroup.mcupdater.UpdateHeader;
import org.atcgroup.mcupdater.data.FileInfo;
import org.atcgroup.mcupdater.data.HttpDownloadInfo;
import org.bukkit.configuration.ConfigurationSection;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.multipart.ParallelConfiguration;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class S3StorageHandler implements CDNHandler {
    public static final Logger LOGGER = LogManager.getLogger("S3-StorageHandler");

    public static final AtomicBoolean WORKING = new AtomicBoolean(false);
    private final S3Client client;
    private final AwsCredentialsProvider credentialsProvider;
    private final String endpoint;
    private final String bucket;
    private final String downloadEndpoint;
    private final String resourcePrefix;
    private final Region region;

    public S3StorageHandler(ConfigurationSection config) {
        var dom = config.getConfigurationSection("s3-cdn");

        if (dom == null) {
            throw new IllegalArgumentException("No Aws-S3 config specified in config!");
        }

        this.endpoint = dom.getString("endpoint");
        this.bucket = dom.getString("bucket");
        this.downloadEndpoint = dom.getString("download-endpoint");
        this.resourcePrefix = dom.getString("resource-prefix");
        this.region = Region.of(dom.getString("region"));

        this.credentialsProvider = () -> new AwsCredentials() {
            @Override
            public String accessKeyId() {
                return dom.getString("access-key-id");
            }

            @Override
            public String secretAccessKey() {
                return dom.getString("access-key-secret");
            }
        };

        this.client = S3Client.builder().endpointOverride(URI.create(this.endpoint)).credentialsProvider(this.credentialsProvider).region(this.region).requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED).build();
    }

    @Override
    public void sync(FileRepository repository) {
        if (WORKING.get()) {
            return;
        }

        new Thread(() -> {
            WORKING.set(true);

            try (var asyncClient = S3AsyncClient.builder()
                    .overrideConfiguration(c -> c.retryStrategy(b -> b.maxAttempts(6)).apiCallAttemptTimeout(Duration.ofSeconds(15)).apiCallTimeout(Duration.ofSeconds(100)))
                    .multipartConfiguration(b -> b.parallelConfiguration(ParallelConfiguration.builder().maxInFlightParts(1).build()))
                    .endpointOverride(URI.create(this.endpoint))
                    .credentialsProvider(this.credentialsProvider)
                    .region(this.region)
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .multipartEnabled(true)
                    .build();
                 var transferManager = S3TransferManager.builder().s3Client(asyncClient).build()) {
                var files = new HashSet<FileInfo>();
                Arrays.stream(Objects.requireNonNull(FilePath.RUNTIME.append("packs").file().listFiles())).filter((f) -> !f.getName().endsWith(".sum")).filter((f) -> !f.getName().endsWith(".meta")).filter(File::isFile).map(File::getName).forEach((s) -> {
                    var info = repository.getFileInfo(s);
                    if (info == null) {
                        LOGGER.error("Failed to fetch file info: {}", s);
                    }
                    files.add(info);
                });

                var pending = new ArrayList<Map.Entry<FileUpload, FileInfo>>();

                for (var fi : files) {
                    if (fi == null) {
                        return;
                    }

                    var remote = checkRemote(fi.getName());
                    if (remote.isPresent() && fi.equals(remote.get())) {
                        continue;
                    }

                    pending.add(new AbstractMap.SimpleEntry<>(upload(transferManager, repository.getFile(fi.getName())), fi));
                }

                for (var entry : pending) {
                    try {
                        entry.getKey().completionFuture().join();
                        uploadMeta(entry.getValue());
                    } catch (Exception e) {
                        LOGGER.warn("Failed to upload {}, meta skipped.", entry.getValue().getName(), e);
                    }
                }
            } finally {
                WORKING.set(false);
            }
        }).start();
    }

    private FileUpload upload(S3TransferManager transferManager, File file) {
        return transferManager.uploadFile(UploadFileRequest.builder().putObjectRequest(b -> b.bucket(this.bucket).key(this.resourcePrefix + "/" + file.getName())).addTransferListener(new TransferListener() {
            @Override
            public void transferInitiated(Context.TransferInitiated context) {
                LOGGER.info("TransferInitiated: {}", file.getName());
            }

            @Override
            public void bytesTransferred(Context.BytesTransferred context) {
                var p = context.progressSnapshot().transferredBytes() / 1024;
                var a = context.progressSnapshot().totalBytes().orElse(1) / 1024;
                LOGGER.info("TransferProgress: {} {}/{}KiB", file.getName(), p, a);
            }

            @Override
            public void transferComplete(Context.TransferComplete context) {
                LOGGER.info("TransferComplete: {}", file.getName());
            }
        }).source(file.toPath()).build());
    }

    private void uploadMeta(FileInfo info) {
        var content = info.getLength() + "/" + info.getSha1() + "/" + info.getSha256();

        this.client.putObject(PutObjectRequest.builder().bucket(this.bucket).key(this.resourcePrefix + "/" + info.getName() + ".meta").build(), RequestBody.fromString((content)));
    }

    @Override
    public void handle(UpdateHeader data, FileRepository repository) {
        var executor = Executors.newFixedThreadPool(6);
        var results = new HashSet<Future<?>>();

        for (var resId : new HashSet<>(data.getServerDownloads().keySet())) {
            results.add(executor.submit(() -> {
                var remote = checkRemote(resId);
                var local = repository.getFileInfo(resId);

                if (remote.isEmpty()) {
                    return;
                }

                var d = remote.get();

                if (d.getLength() != local.getLength() || !Objects.equals(d.getSha1(), local.getSha1())) {
                    return;
                }

                data.getServerDownloads().remove(resId);
                data.getExternDownloads().add(new HttpDownloadInfo(this.downloadEndpoint + this.resourcePrefix + "/" + resId, local.getName(), local.getSha1(), local.getLength()));
            }));
        }

        for (var future : results) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        executor.shutdown();
        executor.close();
    }

    private Optional<FileInfo> checkRemote(String name) {
        try {
            var meta = this.client.getObjectAsBytes(GetObjectRequest.builder().bucket(this.bucket).key(this.resourcePrefix + "/" + name + ".meta").build()).asUtf8String();

            var dom = meta.split("/");
            var len = Long.parseLong(dom[0]);
            var sha1 = dom[1];
            var sha256 = dom[2];

            return Optional.of(new FileInfo(name, len, sha1, sha256));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                System.out.println("status = " + e.statusCode());
                System.out.println("code   = " + e.awsErrorDetails().errorCode());
                System.out.println("msg    = " + e.awsErrorDetails().errorMessage());
                System.out.println("reqId  = " + e.requestId());
                throw e;
            }
            return Optional.empty();
        }
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getBucket() {
        return bucket;
    }
}