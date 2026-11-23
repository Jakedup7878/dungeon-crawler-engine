# Dungeon Crawler

A grid-based, first-person dungeon crawler engine in the style of Wizardry and Shin Megami Tensei.
Plain Java 17+ and Swing, no external libraries.

## Running

- **Double-click `run.cmd`**, or from PowerShell: `.\run.ps1`
- Play a project: `.\run.ps1 "C:\Games\My Game"`, or a single map: `.\run.ps1 path\to\my-floor.txt`
- **Map editor:** double-click `editor.cmd`, or `.\run.ps1 -Editor [project folder or map file]`
- **Publish a game:** `.\publish.cmd "C:\Games\My Game"` (see [Projects and publishing](#projects-and-publishing))
- **IntelliJ:** open this folder (it imports `pom.xml`) and run `crawler.Main` (game) or
  `crawler.editor.MapEditor` (editor).

The script finds a JDK 17+ by itself (JAVA_HOME, then IntelliJ's `~/.jdks`, then the usual install folders),
compiles into `out/`, and starts the game. The `java` on your PATH (Java 8) is too old and is not used.

## Map editor

A separate program for building floors without hand-editing text. It saves the same text format the game
reads, so anything you make there runs in the game, and you can still open the files in a text editor.

| Tool              | Left mouse                                   | Right mouse                     |
|-------------------|----------------------------------------------|---------------------------------|
| **1 / R** Room    | Drag a rectangle: a room with walls round it | Fill the rectangle with rock    |
| **2 / C** Corridor| Drag to dig a passage, cell by cell          | Fill cells with rock            |
| **3 / W** Wall    | Click or drag along grid lines: walls        | Knock walls down                |
| **4 / D** Door    | Click a grid line: door                      | Turn a door back into wall      |
| **5 / S** Start   | Place the party's start; click again to turn | Turn the other way              |
| **6 / V** Event   | Click a floor cell: a message when the party steps there | Remove an event, chest or NPC |
| **7 / T** Chest   | Click a floor cell: a chest to open          | Remove an event, chest or NPC   |
| **8 / N** NPC     | Click a floor cell: someone to talk to       | Remove an event, chest or NPC   |

With the Event, Chest or NPC tool, click an object to edit its name, text and images, or drag it to another cell.

- New maps start as solid rock with one open cell, so you dig the dungeon out. Dragging a corridor into a room
  breaks through its wall.
- The panel on the right shows a **live 3D view** from whichever cell you point at, drawn by the game's own
  renderer (Q / E turn it). Below that: map stats and **checks**, such as floor cells the party can't reach
  (also tinted red on the map) and doors that lead into rock.
- **F5** play-tests the map in a game window from the start; **F6** starts from where the 3D view is.
  Esc closes the test window.
- Ctrl+Z / Ctrl+Y undo and redo. Ctrl + mouse wheel zooms, middle-drag pans.
  **Edit > Resize Map** grows or crops the grid on any side. F1 lists every control.

### Your own images

Everything can use your own pictures (PNG, JPEG, GIF or BMP):

- **Textures** (toolbar button, or Ctrl+T): walls, doors, floor and ceiling for this map. Any size works; images are
  stretched to a square, and small pixel-art sizes (16, 32, 64) stay crisp.
- **Events, chests and NPCs**: each has an image of its own. Chests have a second one for when they're open.
  Without one, NPCs are a spinning blue top, chests are a wooden chest, and events are invisible. PNGs with a
  transparent background look best.

When you save, every image the map uses is copied into an `assets` folder, and the map refers to it as
`assets/<name>`. For a map in a project that's the project's `assets` folder, shared by all its maps; for a map on its
own, it's next to the map file.

| Object | In the game |
|--------|-------------|
| Event  | Shows its message when the party steps onto the cell (only the first time, unless you untick that). |
| Chest  | Blocks the cell. Walking into it (or Space) opens it and shows what's inside. |
| NPC    | Blocks the cell. Walking into it (or Space) shows what they say, a page at a time. |

## Projects and publishing

A game is a **project folder**. **File > New Project** in the editor makes one:

```
My Game/
  project.txt     the game's name and the map it starts on
  maps/           the floors
  assets/         every image, shared by all the maps
  party.txt       the starting party (Project > Party Members)
  enemies.txt     the kinds of enemy (Project > Enemies)
```

- **Project > Make This the Starting Map** picks the floor the game starts on.
- **Project > Party Members** and **Project > Enemies** edit the cast: name, class or id, level, HP, MP, attack,
  defense, speed, experience and gold, and a picture. The game doesn't use them yet; they're ready for
  encounters and battles.
- **Publishing:** `.\publish.cmd "C:\Games\My Game"` builds `dist\My Game\My Game.exe`. Players don't need Java: it
  brings its own. Zip the whole `dist\My Game` folder to share it.

Step-by-step explanations of how these work are in [docs/](docs/walkthrough-1-project-folder.md).

## Controls

| Key             | Action                         |
|-----------------|--------------------------------|
| W / Up          | Step forward                   |
| S / Down        | Step back                      |
| A / D           | Strafe left / right            |
| Q / Left        | Turn left                      |
| E / Right       | Turn right                     |
| X               | Turn around                    |
| Space / Enter   | Open the chest or talk to the NPC in front; next page / close a message |
| M               | Reveal the whole automap (debug) |
| F               | Toggle smooth movement (off = instant, Wizardry-style) |

Tap a key for one step. Hold it to keep walking. Walking into a wall bumps once and stops repeating.

## Map format

Maps are ASCII art on graph paper. Walls sit *between* cells (thin walls), so a corridor cell can have a wall on one
side and a door on the other. See [`src/main/resources/maps/b1f.txt`](src/main/resources/maps/b1f.txt).

```
name: Example
; comments start with a semicolon
+---+---+---+
| ^     D   |
+   +-D-+---+
|   |#######|
+---+---+---+
```

- Each cell is 3 characters wide; `+` corners are ignored.
- `---` / `|` = wall, blank = open, `-D-` / `D` = door.
- `###` inside a cell = solid rock.
- `^ > v <` = where the party starts and which way it faces.
- Open edges on the border or next to rock are walled off automatically.

After the grid and a blank line come optional sections (the editor writes these for you):

```
[textures]
wall = assets/red-brick.png

[npc 10,1]
name = Old man
text = Ah, a visitor!\nMind the pillars.
sprite = assets/old-man.png

[chest 0,0]
name = Dusty chest
contents = A rusty key
sprite = assets/chest.png
opened = assets/chest-open.png

[event 3,8]
name = A noise
text = Something skitters away in the dark.
once = yes
```

Cells are `x,y` from the top-left (0,0), and `\n` in text starts a new line. Image paths are relative to the project
folder (or, for a map outside any project, to the map file).

## How it fits together

| Package          | What's in it |
|------------------|--------------|
| `crawler.core`   | Pure game logic, no Swing. `DungeonMap` (cells + shared edges, textures, objects), `MapObject` / `ObjectType`, `Surface`, `Direction`, `Party`, `MovementSystem` (the movement rules), `MoveAction`/`MoveResult`, `Exploration` (automap memory), `MapLoader` / `MapWriter`, `Reachability`, `GameProject` (project folders), `PartyMember` / `EnemyType` / `Stats`, `GameData` and `SectionFile` (party.txt and enemies.txt). |
| `crawler.render` | `DungeonRenderer` (raycaster for thin walls plus billboard sprites, rendered at 320x240 and scaled up), `Camera` (animates steps and turns), `Textures` (generated in code, or the map's own), `Sprite` / `ObjectLooks` / `DefaultSprites` (how objects look), `AssetSource` (where a map's images come from), `AutomapRenderer`. |
| `crawler.game`   | `Game` ties the map, party and systems together, and runs chests, NPCs and events; `GameMessage`, `MessageLog`. |
| `crawler.ui`     | `GameWindow`, `GamePanel` (60 Hz loop, HUD layout), `InputHandler` (key bindings, buffering, hold-to-repeat). |
| `crawler.editor` | The map editor. `MapEdits` (room / corridor / rock operations, no Swing), `MapDocument` (undo, unsaved changes), `MapCheck` (problems), `AssetStore` (imported images, the `assets` folder), `MapCanvas`, `SidePanel`, `EditorFrame`, `ObjectDialog` / `TextureDialog` / `ImagePicker`, `RosterDialog` (any list + form) with `PartyForm` / `EnemyForm` / `StatFields`. |

Movement is logical and instant: `MovementSystem.perform(action)` moves exactly one cell or turns exactly 90 degrees, then
reports a `MoveResult` to every `MovementListener`. The camera animates afterwards, so game rules never depend on frame timing.

## Where to extend

- **React to walking** (random encounters, traps, spinners, events): `game.movement().addListener(result -> ...)`.
  `Game.onMovement` is an example.
- **New wall types** (locked doors, secret doors, one-way walls): add a constant to `EdgeType` with its
  passable / blocks-sight flags, give it a texture in `Textures.forEdge`, a map character in `MapLoader` and
  `MapWriter`, and an editor tool (`Tool`, handled in `MapCanvas.applyEdge`).
- **Teleporters, stairs, pits**: `Party.place(x, y, facing)`, `MovementSystem.setMap(...)`, then `Camera.snapTo(party)`.
- **More kinds of object** (stairs, signs, shops): add a constant to `ObjectType`, its keys to `MapLoader` /
  `MapWriter`, a default look in `ObjectLooks`, what it does in `Game.use` or `Game.onMovement`, and a `Tool`.
- **Your own art**: per map, from the editor (see above). To change the built-in look for every map, put square
  power-of-two PNGs at `src/main/resources/textures/{wall,door,floor,ceiling}.png`.
- **New stats** (luck, ...): `Stats`, the two `DEFAULT_STATS`, the `allow(...)` lists in `GameData`, and `StatFields`.
  Walkthrough 2 lists the exact steps.
- **Feel**: `STEP_SECONDS` / `TURN_SECONDS` in `GamePanel`. Field of view, eye position and fog are constants at the top
  of `DungeonRenderer`.
