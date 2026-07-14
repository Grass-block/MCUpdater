package org.atcgroup.mcupdater.client;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;

public final class Config {
    private final Properties dom = new Properties();

    public boolean bool(String key,boolean def) {
        if(this.dom.containsKey(key)) {
            return Boolean.parseBoolean(this.dom.getProperty(key));
        }

        this.dom.setProperty(key,Boolean.toString(def));
        return def;
    }

    public String string(String key,String def) {
        if(this.dom.containsKey(key)) {
            return this.dom.getProperty(key);
        }

        this.dom.setProperty(key,def);
        return def;
    }

    public int integer(String s, int i) {
        if(this.dom.containsKey(s)) {
            return Integer.parseInt(this.dom.getProperty(s));
        }

        this.dom.setProperty(s,Integer.toString(i));
        return i;
    }

    public String brand(){
        return this.dom.getProperty("brand");
    }

    public InetSocketAddress service(){
        var data = this.dom.getProperty("service").split(":");

        var ip = data[0];
        int port = Integer.parseInt(data[1]);

        return new InetSocketAddress(ip,port);
    }

    public boolean load() {
        var file = ClientFilePath.UPDATER.append("mcu-client.properties").file();

        if(!file.exists()||file.length()==0) {
            return false;
        }

        try(var i = new FileInputStream(file)) {
            this.dom.load(i);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
