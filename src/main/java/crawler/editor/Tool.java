package crawler.editor;

import crawler.core.ObjectType;

import java.awt.event.KeyEvent;

/** The editing tools. Left mouse does the main thing; right mouse undoes it. */
enum Tool {
    ROOM("Room", KeyEvent.VK_1, KeyEvent.VK_R, null,
            "Drag: carve out a walled room.",
            "Right-drag: fill an area with rock."),
    CORRIDOR("Corridor", KeyEvent.VK_2, KeyEvent.VK_C, null,
            "Drag: dig a passage cell by cell. Dragging into a room breaks through its wall.",
            "Right-drag: fill cells with rock."),
    WALL("Wall", KeyEvent.VK_3, KeyEvent.VK_W, null,
            "Click or drag along grid lines: build walls.",
            "Right: knock walls down."),
    DOOR("Door", KeyEvent.VK_4, KeyEvent.VK_D, null,
            "Click a grid line: put a door there.",
            "Right: turn a door back into wall."),
    START("Start", KeyEvent.VK_5, KeyEvent.VK_S, null,
            "Click a floor cell: the party starts here. Click it again to turn.",
            "Right-click the start: turn the other way."),
    EVENT("Event", KeyEvent.VK_6, KeyEvent.VK_V, ObjectType.EVENT,
            "Click a floor cell: add an event, a message that appears when the party steps there. "
                    + "Click an event to edit it, or drag it to move it.",
            "Right-click: remove an event, chest, NPC or stairs."),
    CHEST("Chest", KeyEvent.VK_7, KeyEvent.VK_T, ObjectType.CHEST,
            "Click a floor cell: add a chest. The party opens it by walking into it. "
                    + "Click a chest to edit it, or drag it to move it.",
            "Right-click: remove an event, chest, NPC or stairs."),
    NPC("NPC", KeyEvent.VK_8, KeyEvent.VK_N, ObjectType.NPC,
            "Click a floor cell: add a character. The party talks to it with Space. "
                    + "Click one to edit it, or drag it to move it.",
            "Right-click: remove an event, chest, NPC or stairs."),
    STAIRS("Stairs", KeyEvent.VK_9, KeyEvent.VK_U, ObjectType.STAIRS,
            "Click a floor cell: add stairs to another floor, on one of its walls or in the middle. Walking into them takes the party there. "
                    + "Click them to edit, or drag them to move them.",
            "Right-click: remove an event, chest, NPC or stairs.");

    final String label;
    /** Number key that selects the tool. */
    final int numberKey;
    /** Letter key that also selects the tool. */
    final int letterKey;
    /** What the tool places, or null for the tools that shape the map. */
    final ObjectType objectType;
    final String primaryHint;
    final String secondaryHint;

    Tool(String label, int numberKey, int letterKey, ObjectType objectType, String primaryHint, String secondaryHint) {
        this.label = label;
        this.numberKey = numberKey;
        this.letterKey = letterKey;
        this.objectType = objectType;
        this.primaryHint = primaryHint;
        this.secondaryHint = secondaryHint;
    }

    /** Whether the tool works on the grid lines between cells rather than on cells. */
    boolean editsEdges() {
        return this == WALL || this == DOOR;
    }

    boolean placesObjects() {
        return objectType != null;
    }

    String shortcut() {
        return KeyEvent.getKeyText(numberKey) + " / " + KeyEvent.getKeyText(letterKey);
    }
}
