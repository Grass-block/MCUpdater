package org.atcgroup.mcupdater;

import me.gb2022.commons.file.FilePath;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class ObjectStorage {
    static {
        RocksDB.loadLibrary();
    }


    private final FilePath path;
    private final Options options;
    private final RocksDB db;

    public ObjectStorage(FilePath path) {
        var options = new Options();
        options.setCreateIfMissing(true);
        options.setMinBlobSize(1024 * 1024);
        options.setMinBlobSize(256 * 1024);
        options.setBlobFileSize(256L * 1024 * 1024);
        options.setEnableBlobGarbageCollection(true);

        this.path = path;
        this.options = options;
        try {
            this.db = RocksDB.open(options, this.path.file().getAbsolutePath());
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    public void close(){

        this.options.close();
    }






}
