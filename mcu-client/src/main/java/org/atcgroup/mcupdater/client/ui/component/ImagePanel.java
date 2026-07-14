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
    }
}
