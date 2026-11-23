package crawler.editor;

import crawler.core.DungeonMap;
import crawler.core.Surface;
import crawler.render.Textures;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.EnumMap;
import java.util.Map;

/** Chooses the map's own wall, door, floor and ceiling images. */
final class TextureDialog {
    private TextureDialog() {
    }

    /** Shows the dialog; returns the texture reference for each surface (null = built-in), or null if cancelled. */
    static Map<Surface, String> show(Component parent, DungeonMap map, AssetStore assets) {
        Map<Surface, ImagePicker> pickers = new EnumMap<>(Surface.class);
        JComponent[] rows = new JComponent[Surface.values().length * 2];
        int i = 0;
        for (Surface surface : Surface.values()) {
            ImagePicker picker = new ImagePicker(assets, map.texture(surface),
                    Textures.defaults().get(surface).toImage(), "Built-in " + surface.displayName.toLowerCase());
            pickers.put(surface, picker);
            rows[i++] = new JLabel(surface.displayName);
            rows[i++] = picker;
        }
        String note = "Any image works. It is stretched to a square, so square images look best; "
                + "small pixel-art sizes (16, 32 or 64 pixels) stay crisp. Images are copied into an "
                + "'assets' folder next to the map when you save.";
        int answer = JOptionPane.showConfirmDialog(parent, new Object[]{MapDialogs.form(rows), ObjectDialog.hint(note)},
                "Textures for " + map.name(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        Map<Surface, String> result = new EnumMap<>(Surface.class);
        pickers.forEach((surface, picker) -> result.put(surface, picker.reference()));
        return result;
    }
}
