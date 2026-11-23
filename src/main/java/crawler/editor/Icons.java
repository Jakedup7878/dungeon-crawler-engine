package crawler.editor;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/** Small toolbar icons, drawn in code so the editor needs no image files. */
final class Icons {
    private static final int SIZE = 18;
    private static final Color FLOOR = new Color(150, 178, 222);
    private static final Color WALL = new Color(40, 50, 76);
    private static final Color DOOR = new Color(226, 150, 46);
    private static final Color START = new Color(245, 196, 50);
    private static final Color PLAY = new Color(46, 160, 70);

    private Icons() {
    }

    static Icon tool(Tool tool) {
        return switch (tool) {
            case ROOM -> painted(g -> {
                g.setColor(FLOOR);
                g.fillRect(3, 3, 12, 12);
                g.setColor(WALL);
                g.setStroke(new BasicStroke(2f));
                g.drawRect(3, 3, 12, 12);
            });
            case CORRIDOR -> painted(g -> {
                Polygon path = new Polygon(new int[]{2, 2, 11, 11, 16, 16, 7, 7},
                        new int[]{16, 10, 10, 2, 2, 7, 7, 16}, 8);
                g.setColor(FLOOR);
                g.fillPolygon(path);
                g.setColor(WALL);
                g.setStroke(new BasicStroke(1.5f));
                g.drawPolygon(path);
            });
            case WALL -> painted(g -> {
                g.setColor(new Color(150, 156, 170));
                for (int i = 3; i <= 15; i += 6) {
                    g.fillRect(i - 1, 3, 2, 2);
                    g.fillRect(i - 1, 14, 2, 2);
                }
                g.setColor(WALL);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                g.drawLine(3, 9, 15, 9);
            });
            case DOOR -> painted(g -> {
                g.setColor(WALL);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                g.drawLine(2, 9, 16, 9);
                g.setColor(DOOR);
                g.fillRect(5, 6, 8, 6);
                g.setColor(WALL);
                g.setStroke(new BasicStroke(1f));
                g.drawRect(5, 6, 8, 6);
            });
            case START -> painted(g -> {
                Polygon arrow = new Polygon(new int[]{9, 15, 9, 3}, new int[]{2, 15, 12, 15}, 4);
                g.setColor(START);
                g.fillPolygon(arrow);
                g.setColor(WALL);
                g.setStroke(new BasicStroke(1.2f));
                g.drawPolygon(arrow);
            });
            case EVENT -> painted(g -> {
                g.setColor(new Color(176, 76, 220));
                g.fillOval(2, 2, 14, 14);
                g.setColor(new Color(60, 20, 80));
                g.drawOval(2, 2, 14, 14);
                g.setColor(Color.WHITE);
                g.fillRect(8, 5, 2, 6);
                g.fillRect(8, 12, 2, 2);
            });
            case CHEST -> painted(g -> {
                g.setColor(new Color(150, 92, 42));
                g.fillRoundRect(2, 4, 14, 6, 5, 5);
                g.setColor(new Color(120, 72, 32));
                g.fillRect(2, 9, 14, 7);
                g.setColor(new Color(60, 36, 14));
                g.drawRoundRect(2, 4, 14, 6, 5, 5);
                g.drawRect(2, 9, 14, 7);
                g.setColor(new Color(236, 190, 70));
                g.fillRect(8, 8, 3, 4);
            });
            case NPC -> painted(g -> {
                Polygon body = new Polygon(new int[]{2, 16, 9}, new int[]{7, 7, 17}, 3);
                g.setColor(new Color(40, 90, 220));
                g.fillOval(2, 4, 14, 6);
                g.fillPolygon(body);
                g.setColor(new Color(140, 195, 255));
                g.drawLine(6, 8, 9, 14);
                g.drawLine(11, 7, 12, 11);
                g.setColor(new Color(60, 80, 140));
                g.fillRect(8, 1, 3, 4);
            });
        };
    }

    /** A small patch of brickwork. */
    static Icon textures() {
        return painted(g -> {
            g.setColor(new Color(150, 156, 176));
            g.fillRect(2, 3, 14, 12);
            g.setColor(WALL);
            g.setStroke(new BasicStroke(1f));
            g.drawRect(2, 3, 14, 12);
            g.drawLine(2, 7, 16, 7);
            g.drawLine(2, 11, 16, 11);
            g.drawLine(9, 3, 9, 7);
            g.drawLine(5, 7, 5, 11);
            g.drawLine(13, 7, 13, 11);
            g.drawLine(9, 11, 9, 15);
        });
    }

    static Icon play() {
        return painted(g -> {
            Polygon triangle = new Polygon(new int[]{5, 15, 5}, new int[]{3, 9, 15}, 3);
            g.setColor(PLAY);
            g.fillPolygon(triangle);
        });
    }

    private static Icon painted(Consumer<Graphics2D> painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.translate(x, y);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    painter.accept(g);
                } finally {
                    g.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return SIZE;
            }

            @Override
            public int getIconHeight() {
                return SIZE;
            }
        };
    }
}
