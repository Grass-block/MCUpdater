package org.atcgroup.mcupdater.client.ui.component;

import javax.swing.*;
import java.awt.*;

public final class ImagePanel extends JPanel {
    private Image image = null;

    public ImagePanel(Image background) {
        this.paintImage(background);
    }

    public ImagePanel() {

    }

    public void paintImage(Image image) {
        this.image = image;
        this.repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(this.image, 0, 0, this.getWidth(), this.getHeight(), null);

        var panelWidth = getWidth();
        var panelHeight = getHeight();

        var imageWidth = this.image.getWidth(null);
        var imageHeight = this.image.getHeight(null);

        var scale = Math.max((double) panelWidth / imageWidth, (double) panelHeight / imageHeight);

        var drawWidth = (int) (imageWidth * scale);
        var drawHeight = (int) (imageHeight * scale);

        var x = (panelWidth - drawWidth) / 2;
        var y = (panelHeight - drawHeight) / 2;

        g.drawImage(this.image, x, y, drawWidth, drawHeight, null);
    }
}
