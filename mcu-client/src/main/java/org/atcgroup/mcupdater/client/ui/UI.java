package org.atcgroup.mcupdater.client.ui;

import org.atcgroup.mcupdater.client.ClientFilePath;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.swing.FontIcon;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public interface UI {
    NumberFormat NUMBER_FORMAT = new DecimalFormat("###.##");

    static Icon icon(Ikon icon) {
        var i = new FontIcon();

        i.setIkon(icon);
        i.setIconColor(Color.WHITE);
        i.setIconSize(20);

        return i;
    }

    static Image image(String path) {
        var file = ClientFilePath.UPDATER.append(path).file();

        if (file.exists() && file.length() > 0) {
            try (var res = new FileInputStream(file)) {
                return ImageIO.read(res);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try (var res = UI.class.getResourceAsStream(path)) {
            if (res != null) {
                return ImageIO.read(res);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    static GridBagConstraints gbc(int x, int y, int fill) {
        var gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.fill = fill;

        return gbc;
    }

    static Image background() {
        return image("/background.png");
    }
}
