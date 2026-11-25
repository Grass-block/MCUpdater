package me.gb2022.mcupdater.server.test;

import java.nio.file.Path;

/** Separate-process RTP benchmark server. Its working directory is the benchmark run directory. */
public interface BenchmarkServer {
    static void main(String[] args) throws Exception {
        if (args.length != 1 || (!args[0].equals("bftp") && !args[0].equals("http"))) {
            throw new IllegalArgumentException("Usage: BenchmarkServer <bftp|http>");
        }
        BenchmarkClient.runServer(Path.of(System.getProperty("user.dir")), args[0].equals("http"));
    }
}
