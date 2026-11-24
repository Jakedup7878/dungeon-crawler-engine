# Dungeon Crawler

A grid-based, first-person dungeon crawler engine in the style of Wizardry and Shin Megami Tensei, with a map editor
for building your own games. Plain Java 17+ and Swing; the game itself uses no outside libraries.

## Running

- **Double-click `run.cmd`** to play the sample game, or from PowerShell: `.\run.ps1`
- Play a project: `.\run.ps1 "C:\Games\My Game"`, or a single map: `.\run.ps1 path\to\my-floor.txt`
- **Map editor:** double-click `editor.cmd`, or `.\run.ps1 -Editor [project folder or map file]`
- **Map editor as a program:** double-click `build-editor.cmd`; it makes
  `dist\Dungeon Crawler Editor\Dungeon Crawler Editor.exe` (see [The editor as a program](#the-editor-as-a-program))
- **Publish a game:** **Project > Publish Game** in the editor, or `.\publish.cmd "C:\Games\My Game"`
  (see [Projects and publishing](#projects-and-publishing))
- **Tests:** double-click `test.cmd`, or `.\test.ps1` (see [Tests](#tests))
- **IntelliJ:** open this folder (it imports `pom.xml`) and run `crawler.Main` (game) or
  `crawler.editor.MapEditor` (editor). The JUnit tests in `src/test/java` run from IntelliJ too.

The scripts find a JDK 17+ by themselves (JAVA_HOME, then IntelliJ's `~/.jdks`, then the usual install folders),
compile into `out/`, and start the program. The `java` on your PATH (Java 8) is too old and is not used.

The sample game (`src/main/resources/sample`) is a small project showing everything off: party creation, random
battles, an NPC, a chest, an event, and a boss behind the closet door.

## Controls

| Key             | Exploring                      | In battle                    |
|-----------------|--------------------------------|------------------------------|
| W / Up          | Step forward                   | Choose (up)                  |
| S / Down        | Step back                      | Choose (down)                |
| A / D           | Strafe left / right            | Choose a target              |
| Q / E, Left / Right | Turn left / right          | Choose a target              |
| X               | Turn around                    |                              |
| Space / Enter   | Open or talk to what's in front; take the stairs in front; next page / close a message | Confirm; hurry the animations |
| Esc / Backspace |                                | Back to the previous member  |
| I / Tab         | Items and equipment            |                              |
| M               | Reveal the whole automap (debug) |                            |
| F               | Smooth movement on / off       |                              |
| V               | Sound on / off                 | Sound on / off               |

Tap a key for one step. Hold it to keep walking. Walking into a wall bumps once and stops repeating.

## Battles

Random battles happen as you walk (each map sets how often and which enemies), and an event can start a fixed battle,
such as a boss, that can't be run from. Battles are turn-based, Wizardry style:

1. Each party member gets an order: **Attack** (choose a target), **Defend** (half damage this round), **Item** or **Flee**.
2. Everyone acts, fastest first. Each attack plays its **attack animation** and sound over whoever is hit.
3. Winning shares **experience** among the survivors and adds **gold**. Enough experience raises a member's level,
   adding their class's growth to their stats. If the whole party falls, it's game over; Enter starts again.

Damage is attack x (85% to 115%) minus half the target's defense, at least 1. The rules are in `crawler.battle.Battle`.

## Floors

Stairs join the maps of a project. They go on a wall of a cell (the picture covers the wall, and the cell stays free)
or in the middle of the floor (filling the cell). Walking into them takes the party to another floor, and each floor remembers what
has been explored, opened and triggered. In the editor, add another floor (**Project > Add Floor**), then place **Stairs** (tool 9) and choose the floor they lead to and where the party arrives: that floor's
start, in front of its stairs, or any cell. Put stairs on both floors to go there and back. Stairs to the same floor work as
a teleporter. A floor that's missing, or an arrival cell the party can't stand on, is reported before the game starts.

## Enemy tiers

Tag enemies with a **tier**, such as Common, Elite or Boss. **Project > Enemy Tiers** sets each tier's name and colour
(shown in battle), how likely its enemies are in random battles, percentages that change their HP, attack, defense,
speed, experience and gold, and whether battles with them can be run from. Give an enemy its tier in
**Project > Enemies**. Random battles and event battles can name a whole tier, such as `tier:elite`, and item scripts
can read `target.tier` (1 for the lowest tier, 0 for none). With no tiers of its own, a project has Common, Elite and
Boss.

## Items

The party carries items (up to 99 of each) and wears a weapon, shield, helmet, body armor and accessory. Press **I** to
use and equip things; in battle, use them with **Item**. Make your own in the editor, **Project > Items**: potions,
weapons, armor, accessories and key items, with stat bonuses, who can equip them, a picture, an attack animation, and a
**script** for whatever else they do: heal, revive, poison, stun, burn, drain, thorns, boosts and so on. The script
language is explained in [docs/item-scripts.md](docs/item-scripts.md).

The party gets items from chests, from **Starting items** in Project Settings, and from the **Equipment** of classes and
party members.

## Map editor

A separate program for building games without hand-editing text. It saves the same text formats the game reads,
so anything you make there runs in the game, and you can still open the files in a text editor.

| Tool              | Left mouse                                   | Right mouse                     |
|-------------------|----------------------------------------------|---------------------------------|
| **1 / R** Room    | Drag a rectangle: a room with walls round it | Fill the rectangle with rock    |
| **2 / C** Corridor| Drag to dig a passage, cell by cell          | Fill cells with rock            |
| **3 / W** Wall    | Click or drag along grid lines: walls        | Knock walls down                |
| **4 / D** Door    | Click a grid line: door                      | Turn a door back into wall      |
| **5 / S** Start   | Place the party's start; click again to turn | Turn the other way              |
| **6 / V** Event   | Click a floor cell: a message (or battle) when the party steps there | Remove an event, chest or NPC |
| **7 / T** Chest   | Click a floor cell: a chest to open          | Remove an event, chest or NPC   |
| **8 / N** NPC     | Click a floor cell: someone to talk to; untick "Players can walk through" to make them block the way | Remove an event, chest or NPC   |
| **9 / U** Stairs  | Click a floor cell: stairs to another floor  | Remove an event, chest, NPC or stairs |

### Several floors

**Project > Floors** lists the project's floors in order: open one, add one, move it, or make it the starting floor.
**Project > Add Floor** makes a new one before or after the open floor, blank or with the same rooms and walls.
Ctrl+Up and Ctrl+Down open the previous and next floor.

**View > Floor Guide** shows the previous (or next) floor faintly underneath the one you're drawing, cell for cell, so
shafts, towers and stairwells line up. Ctrl+G switches the previous floor's guide on and off. Pink areas are its rooms,
dashed lines its walls, triangles its stairs, and rings where those stairs come out on this floor. The guide shows the
other floor as last saved, and is never part of the map.

With the Event, Chest, NPC or Stairs tool, click an object to edit it, or drag it to another cell.

- New maps start as solid rock with one open cell, so you dig the dungeon out. Dragging a corridor into a room
  breaks through its wall.
- The panel on the right shows a **live 3D view** from whichever cell you point at, drawn by the game's own
  renderer (Q / E turn it). Below that: map stats and **checks**, such as floor cells the party can't reach
  (also tinted red on the map), doors into rock, missing files, and battles naming enemies that don't exist.
- **F5** play-tests the map from the start (with party creation, if the project has it); **F6** starts from where
  the 3D view is. Esc closes the test window.
- Ctrl+Z / Ctrl+Y undo and redo. Ctrl + mouse wheel zooms, middle-drag pans.
  **Edit > Resize Map** grows or crops the grid on any side. F1 lists every control.

### Per map

- **Edit > Textures** (Ctrl+T): walls, doors, floor and ceiling pictures.
- **Edit > Map Settings** (Ctrl+M): background **music**, and **random battles**: the chance per step, how many
  enemies at once, and which enemies can appear.
- **Events**: a message, an optional picture, and an optional **battle** (enemy ids, such as `rat-king, giant-rat`).

### For the whole game (the Project menu)

| Item | What you set |
|------|--------------|
| **Settings** | Game name; whether players **create their own party**, and its size; starting gold and **items**; the default **NPC look** (instead of the spinning top) and chest looks; **battle music** and **victory music** |
| **Party Members** | The ready-made party: name, class, level, stats, portrait |
| **Classes** | Fighter, Mage...: starting stats, **growth per level**, attack animation, portrait. With none, the game uses Fighter, Mage, Priest and Thief |
| **Enemy Tiers** | Tags such as Elite or Boss, with their colour, likelihood and stat changes |
| **Enemies** | Name, id, **tier**, stats, experience, gold, **picture** (still or animated) and **attack animation** |
| **Items** | Potions, weapons, armor, accessories and key items, each with a [script](docs/item-scripts.md) |
| **Attack Animations** | An animation strip, its size and a **sound**, with a live preview |
| **Publish Game** | Makes the game into a program players can run, with its files encrypted (see below) |

### Your own pictures and sounds

Everything can use your own files. Pictures: PNG, JPEG, GIF or BMP (PNGs with a transparent background look best).
Sounds: WAV or MIDI (and AIFF or AU). Convert MP3 or OGG to WAV with any audio tool.

**Animations** are one image with the frames side by side, left to right, all the same width. Set **Frames** to how
many there are and **Speed** to frames per second. NPCs, chests, event pictures and enemies loop their animation;
attack animations play once per hit. Textures are always still.

When you save, every file is copied into the project's `assets` folder, and the game refers to it as `assets/<name>`.
(For a map outside any project, the `assets` folder goes next to the map file.)

## Projects and publishing

A game is a **project folder**. **File > New Project** in the editor makes one:

```
My Game/
  project.txt      the game's name, the map it starts on, and the Project Settings
  maps/            the floors
  assets/          every picture and sound, shared by all the maps
  party.txt        the ready-made party        (Project > Party Members)
  classes.txt      the classes                 (Project > Classes)
  enemies.txt      the kinds of enemy          (Project > Enemies)
  tiers.txt        the enemy tiers             (Project > Enemy Tiers)
  items.txt        the items and their scripts (Project > Items)
  animations.txt   the attack animations       (Project > Attack Animations)
```

- **Project > Make This the Starting Map** picks the floor the game starts on.

### Publishing

**Project > Publish Game** (or `.\publish.cmd "C:\Games\My Game" [output folder]`) makes the game into a program:

```
My Game/
  My Game.exe      players double-click this
  runtime/         a private copy of Java, so players don't need to install it
  app/
    crawler.jar    the engine
    game.pak       the whole project, encrypted
```

Zip the whole `My Game` folder to share it. Before writing anything, the publisher packs the game and loads it back
from the pack, so a project with a problem (a missing map, a typo in a data file) is reported instead of published.
Publishing again to the same place replaces the earlier copy; a folder that isn't a published game is never deleted.

**Encryption.** Every map, picture, sound and data file goes into `game.pak`, encrypted with AES-256-GCM. Players
can't open, browse or copy the files, and a `game.pak` that has been changed refuses to load. Your project folder
stays as it is, so you keep editing normally. How far this goes: the game has to decrypt its own files to run, so the
key travels with it. That stops casual copying and tampering, but someone determined who studies this engine's code
(it's public) could still get the files out. No game that runs offline can do better.

### The editor as a program

`build-editor.cmd` builds `dist\Dungeon Crawler Editor\`, which runs on any Windows computer without Java or this
source code. Copy or zip the folder to install it elsewhere. It holds:

- `Dungeon Crawler Editor.exe`, the editor.
- `Sample Quest.exe`, which plays the sample game. Publish Game copies this program for every game it makes, so the
  packaged editor publishes in a few seconds and needs no JDK. (From source, the publisher first builds it with the
  JDK's `jpackage`.)
- `Sample Quest\`, the sample project, to open in the editor and learn from.

Step-by-step explanations of how project folders, party members and enemies were added are in
[docs/](docs/walkthrough-1-project-folder.md).

## File formats

Maps are ASCII art on graph paper. Walls sit *between* cells (thin walls), so a corridor cell can have a wall on one
side and a door on the other. See [`src/main/resources/sample/maps/b1f.txt`](src/main/resources/sample/maps/b1f.txt).

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
[settings]
music = assets/cellar.mid
encounters = slime, giant-rat
encounter_rate = 7
group = 1-3

[textures]
wall = assets/red-brick.png

[npc 10,1]
name = Old man
text = Ah, a visitor!\nMind the pillars.
sprite = assets/old-man.png, 4 frames, 6 fps

[chest 0,0]
name = Dusty chest
contents = A rusty key
items = potion x2, iron-sword
sprite = assets/chest.png
opened = assets/chest-open.png

[stairs 9,9]
to = maps/b2f.txt
arrive = 0,1,east
wall = west
way = down

[event 10,9]
name = The Rat King
text = Red eyes glint in the dark!
once = yes
battle = rat-king, giant-rat
```

Cells are `x,y` from the top-left (0,0), and `\n` in text starts a new line. A picture is a path, optionally followed
by `, N frames, F fps` for an animation strip. Paths are relative to the project folder (or, for a map outside any
project, to the map file).

`party.txt`, `classes.txt`, `enemies.txt`, `tiers.txt`, `items.txt` and `animations.txt` use the same `[section]` and `key = value` style;
open the sample project's files to see them. Mistakes in any of these files are reported with the file and line.

## Tests

`src/test/java` holds JUnit 6 tests for the map and project formats, movement, the battle rules, encounters and
events, sound files, sprites and rendering, the editor's rules, and the game screens (driven by key presses, without
a window). Run them with `test.cmd` / `.\test.ps1`, which downloads JUnit's test runner from Maven Central into `lib/`
the first time (checked against Maven Central's checksum), or from IntelliJ. `.\test.ps1 -Filter Battle` runs only
the test classes with "Battle" in their name.

## How it fits together

| Package          | What's in it |
|------------------|--------------|
| `crawler.core`   | Pure game logic and file formats, no Swing. `DungeonMap` (cells + shared edges, textures, objects, music, `Encounters`), `MapObject` / `ObjectType`, `SpriteRef`, `Surface`, `Direction`, `Party`, `MovementSystem`, `MoveAction` / `MoveResult`, `Exploration`, `MapLoader` / `MapWriter`, `Reachability`, `GameProject` / `ProjectSettings`, `PartyMember` / `CharacterClass` / `EnemyType` / `AttackAnimation` / `Stats`, `GameData` / `SectionFile` / `GameContent`, `ProjectFiles` (a project's files in a folder, a pack or the classpath), `GamePack` (encrypted game packs). |
| `crawler.publish` | `Publisher` (makes a game program from a template plus the encrypted pack) and `PublishTool` (the same from the command line, for `publish.ps1`). |
| `crawler.battle` | The battle rules, no Swing: `Battle` (rounds, turn order, damage, fleeing, rewards), `Fighter` (HP, levels), `BattleEvent`. |
| `crawler.script` | The item script language: `Script` and `ScriptParser`. It knows nothing of the game; `Effects` in `crawler.battle` carries out its commands. |
| `crawler.audio`  | `MusicPlayer` (looping music and sound effects, WAV through a Clip and MIDI through the Sequencer) and `AudioFiles` (checking files). Sound never stops the game: problems are logged and it stays quiet. |
| `crawler.render` | `DungeonRenderer` (raycaster for thin walls plus billboard sprites, rendered at 320x240 and scaled up), `Camera`, `Textures`, `Sprite` (stills and animation strips) / `ObjectLooks` / `DefaultSprites` (how everything looks), `AssetSource` (where a game's files come from), `AutomapRenderer`. |
| `crawler.game`   | `Game` ties the map, party and systems together: chests, NPCs, events, encounters, gold; `GameMessage`, `MessageLog`. |
| `crawler.ui`     | `GameWindow`, `GamePanel` (60 Hz loop, layout, which screen gets the keys), `BattleScreen`, `PartyCreationScreen`, `Hud` (shared drawing), `InputHandler`. |
| `crawler.editor` | The editor. `MapEdits`, `MapDocument` (undo), `MapCheck` (problems), `AssetStore` (imported files), `MapCanvas`, `SidePanel`, `EditorFrame`, the dialogs (`ObjectDialog`, `TextureDialog`, `SettingsDialogs`, `PublishDialog`, `RosterDialog` with `PartyForm` / `ClassForm` / `EnemyForm` / `AnimationForm`), and pickers (`ImagePicker`, `SoundPicker`, `AnimationChooser`). |

Movement is logical and instant: `MovementSystem.perform(action)` moves exactly one cell or turns exactly 90 degrees, then
reports a `MoveResult` to every `MovementListener`. The camera animates afterwards, so game rules never depend on frame timing.
Battles work the same way: `Battle.playRound` decides everything at once, and `BattleScreen` plays the events back.

## Where to extend

- **More script commands**: add it to `ScriptParser.COMMANDS` and to `Effects.apply`.
- **More battle commands** (spells): a new `Battle.Command`, what it does in `Battle.playRound`, a
  `BattleEvent` for it, and a line in `BattleScreen` (its menu and how the event is shown).
- **Smarter enemies**: enemies pick a random target in `Battle.playRound`; give `EnemyType` a behaviour and use it there.
- **New wall types** (locked doors, secret doors, one-way walls): add a constant to `EdgeType` with its
  passable / blocks-sight flags, give it a texture in `Textures.forEdge`, a map character in `MapLoader` and
  `MapWriter`, and an editor tool (`Tool`, handled in `MapCanvas.applyEdge`).
- **Pits, lifts and other ways between floors**: see `Game.travel`, `Destination` and `Floors`.
- **More kinds of object** (signs, shops): add a constant to `ObjectType`, its keys to `MapLoader` /
  `MapWriter`, a default look in `ObjectLooks`, what it does in `Game.use` or `Game.onMovement`, and a `Tool`.
- **New stats** (luck, ...): `Stats` (and its `read` / `write`), the `DEFAULT_STATS`, the `allow(...)` lists in
  `GameData`, and `StatFields`. Walkthrough 2 lists the steps.
- **Feel**: `STEP_SECONDS` / `TURN_SECONDS` in `GamePanel`. Field of view, eye position and fog are constants at the top
  of `DungeonRenderer`. Battle pacing is at the top of `BattleScreen`.
