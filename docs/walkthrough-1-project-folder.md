# Walkthrough 1: Project folders and publishing

> **Already applied.** Everything below is already in this project. Read it to learn how the feature works, or use
> it to build the same thing in another codebase. Later features (battles, classes, attack animations,
> music, animated sprites) changed some of these classes since, so the code in `src/` is the current version.

Right now each map keeps its pictures in an `assets` folder next to the map file, and the game plays one map file
at a time. In this walkthrough you give every game its own **project folder**, so all its maps share one set of
pictures. Then you add a script that turns that folder into a program players can run without installing Java.

Do this walkthrough first. [Walkthrough 2](walkthrough-2-party-and-enemies.md) (party members and enemies) saves
its files in the project folder.

## What you'll end up with

In the editor, **File > New Project** makes this:

```
Test Quest/            the project folder
  project.txt          the game's name and the map it starts on
  maps/
    b1f.txt
    b2f.txt
  assets/              every picture, shared by all the maps
    mossy-wall.png
  party.txt            (added in Walkthrough 2)
  enemies.txt          (added in Walkthrough 2)
```

And `publish.cmd "Test Quest"` makes this, ready to zip and share:

```
dist/Test Quest/
  Test Quest.exe       players double-click this
  runtime/             a private copy of Java
  app/
    crawler.jar        the game's code
    game/              a copy of the project folder
```

## The one idea behind it

Maps refer to pictures by paths such as `assets/mossy-wall.png`. A path like that is always relative to some
folder. Today that folder is **the map's own folder**. Inside a project it becomes **the project folder**.

That one change does most of the work:

- Every map in `maps/` can say `assets/mossy-wall.png` and they all find the same file.
- The project folder can move anywhere (another PC, a zip, the published game) without breaking a single path.
- The game only needs to know where the project folder is.

Maps that aren't in a project keep working exactly as before.

---

## Step 1: The project class

Create `src/main/java/crawler/core/GameProject.java`. It goes in `crawler.core`, not `crawler.editor`, because the
game needs it too. Like everything in `core`, it has no Swing code.

```java
package crawler.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A game made with the editor: one folder that holds everything the game needs.
 *
 * <pre>
 * My Game/
 *   project.txt    the game's name and the map it starts on
 *   maps/          the floors
 *   assets/        every image the game uses
 * </pre>
 *
 * Image references in the maps (such as {@code assets/wall.png}) are relative to this
 * folder, so the folder can be copied, zipped or shipped with the game exactly as it is.
 */
public final class GameProject {
    public static final String FILE_NAME = "project.txt";
    public static final String MAPS = "maps";
    public static final String ASSETS = "assets";

    private final Path root;
    private String name;
    /** The first map, relative to the root, with forward slashes. */
    private String startMap;

    private GameProject(Path root, String name, String startMap) {
        this.root = root.toAbsolutePath().normalize();
        this.name = name;
        this.startMap = startMap;
    }

    /** Whether a folder holds a project. */
    public static boolean isProject(Path folder) {
        return folder != null && Files.isRegularFile(folder.resolve(FILE_NAME));
    }

    /**
     * Makes a new project: the folder, its maps and assets folders and project.txt.
     * The start map is {@code maps/b1f.txt}; writing that map is up to the caller.
     */
    public static GameProject create(Path root, String name) throws IOException {
        if (isProject(root)) {
            throw new IOException(root + " already has a project in it.");
        }
        Files.createDirectories(root.resolve(MAPS));
        Files.createDirectories(root.resolve(ASSETS));
        GameProject project = new GameProject(root, name.strip(), MAPS + "/b1f.txt");
        project.save();
        return project;
    }

    /** Reads the project in a folder. */
    public static GameProject open(Path root) throws IOException {
        List<String> lines = Files.readAllLines(root.resolve(FILE_NAME), StandardCharsets.UTF_8);
        String name = root.toAbsolutePath().getFileName().toString();
        String start = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IOException(FILE_NAME + ", line " + (i + 1) + ": expected 'key = value'");
            }
            String key = line.substring(0, eq).strip().toLowerCase();
            String value = line.substring(eq + 1).strip();
            switch (key) {
                case "name" -> name = value;
                case "start" -> start = value;
                default -> throw new IOException(FILE_NAME + ", line " + (i + 1) + ": unknown setting '" + key + "'");
            }
        }
        if (start == null) {
            throw new IOException(FILE_NAME + " doesn't say which map to start on (start = maps/b1f.txt)");
        }
        return new GameProject(root, name, start);
    }

    /** The project a file belongs to: the nearest folder above it with a project.txt, or null. */
    public static Path findRoot(Path file) {
        for (Path folder = file.toAbsolutePath().getParent(); folder != null; folder = folder.getParent()) {
            if (isProject(folder)) {
                return folder;
            }
        }
        return null;
    }

    public void save() throws IOException {
        String text = "; A Dungeon Crawler game. Paths are relative to this folder.\n"
                + "name = " + name.replaceAll("[\\r\\n]+", " ") + "\n"
                + "start = " + startMap + "\n";
        Files.writeString(root.resolve(FILE_NAME), text, StandardCharsets.UTF_8);
    }

    public Path root() {
        return root;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name.strip();
    }

    public Path mapsFolder() {
        return root.resolve(MAPS);
    }

    public Path assetsFolder() {
        return root.resolve(ASSETS);
    }

    public Path startMapFile() {
        return root.resolve(startMap);
    }

    public void setStartMap(Path mapFile) {
        startMap = relative(mapFile);
    }

    /** A file inside the project, written the way project files refer to it: relative, with forward slashes. */
    public String relative(Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    /** Whether a file is somewhere inside this project's folder. */
    public boolean contains(Path file) {
        return file.toAbsolutePath().normalize().startsWith(root);
    }
}
```

Things to notice:

- `findRoot` walks up the folders above a map file until it finds a `project.txt`. That's how the editor and the
  game tell a project map from a map on its own. It also works for maps in subfolders of `maps/`.
- `relative` writes paths with forward slashes, so `project.txt` reads the same on every computer.
- `project.txt` uses the same `key = value` style as the sections at the bottom of a map file.

## Step 2: Tell the editor which project a map belongs to

All of step 2 and step 3 is in `src/main/java/crawler/editor/EditorFrame.java`.

**2a. Imports.** Add this with the other `crawler.core` imports:

```java
import crawler.core.GameProject;
```

and these with the `java.*` imports:

```java
import java.awt.Desktop;
import java.util.ArrayList;
import java.util.List;
```

**2b. Fields.** Below `private final Action redoAction;` add:

```java
    /** Project menu items that only make sense while a project is open. */
    private final List<Action> projectActions = new ArrayList<>();
    /** The project the open map belongs to, or null for a map on its own. */
    private GameProject project;
```

**2c. Two helpers.** Add these just above `static String suggestedFileName(String mapName) {`. `assetRoot` is the
heart of this walkthrough: it decides which folder picture paths are relative to.

```java
    // ------------------------------------------------------------ projects

    /** Where a map's images are kept: its project's folder, or the map's own folder for a map outside any project. */
    static Path assetRoot(Path mapFile) {
        Path root = GameProject.findRoot(mapFile);
        return root != null ? root : mapFile.toAbsolutePath().getParent();
    }

    /** The project a map file belongs to, or null. */
    private GameProject projectOf(Path mapFile) {
        Path root = mapFile == null ? null : GameProject.findRoot(mapFile);
        if (root == null) {
            return null;
        }
        try {
            return GameProject.open(root);
        } catch (IOException e) {
            status("Couldn't read " + root.resolve(GameProject.FILE_NAME) + ": " + e.getMessage());
            return null;
        }
    }

    GameProject project() {
        return project;
    }
```

**2d. Use them wherever the editor used "the map's folder".** There are four places.

In the **constructor**, replace

```java
        assets = new AssetStore(file == null ? null : file.toAbsolutePath().getParent());
        looks = new ObjectLooks(assets);
        doc = new MapDocument(map, file, assets);
```

with

```java
        assets = new AssetStore(file == null ? null : assetRoot(file));
        looks = new ObjectLooks(assets);
        doc = new MapDocument(map, file, assets);
        project = projectOf(file);
```

In **`openFile`**, replace

```java
            assets.reset(file.toAbsolutePath().getParent());
```

with the lines below. `project` has to be set before `doc.reset(...)`, because resetting the document updates the
window title, and the title shows the project's name.

```java
            project = projectOf(file);
            assets.reset(assetRoot(file));
```

In **`saveTo`**, replace

```java
            Path folder = file.toAbsolutePath().getParent();
```

with

```java
            Path folder = assetRoot(file);
```

and just above `doc.markSaved(file.toAbsolutePath());` add:

```java
        project = projectOf(file); // saving into a project folder makes the map part of that project
```

In **`newMap`**, replace `assets.reset(null);` with:

```java
        assets.reset(project == null ? null : project.root()); // a new map in a project shares its images
```

**2e. Start file dialogs in the project's maps folder.** In `startDirectory()`, right after the first `if` block, add:

```java
        if (project != null) {
            return project.mapsFolder();
        }
```

**2f. Show the project in the title, and switch the Project menu on and off.** In `documentChanged()`, replace

```java
        setTitle((doc.isDirty() ? "*" : "") + fileName + " - " + map.name() + " - " + APP);
```

with the lines below. `documentChanged` runs after every change, including opening and saving, so the menu always
matches the map that's open.

```java
        String projectName = project == null ? "" : " - " + project.name();
        setTitle((doc.isDirty() ? "*" : "") + fileName + " - " + map.name() + projectName + " - " + APP);
        for (Action action : projectActions) {
            action.setEnabled(project != null);
        }
```

## Step 3: New Project, Open Project and a Project menu

**3a. Menu entries.** In `menuBar()`, right after `JMenu file = menu("File", KeyEvent.VK_F);` add:

```java
        file.add(action("New Project...", null, this::newProject));
        file.add(action("Open Project...", null, this::openProject));
        file.addSeparator();
```

and just above `JMenu test = menu("Test", KeyEvent.VK_S);` add:

```java
        JMenu projectMenu = menu("Project", KeyEvent.VK_P);
        projectMenu.add(projectAction("Make This the Starting Map", this::makeStartMap));
        projectMenu.add(projectAction("Show Project Folder", this::showProjectFolder));
        bar.add(projectMenu);
```

**3b. A helper for project-only menu items.** Add it next to `toolButton`:

```java
    /** A menu action that is only enabled while a project is open. */
    private Action projectAction(String name, Runnable run) {
        Action action = action(name, null, run);
        projectActions.add(action);
        return action;
    }
```

**3c. The commands.** Add these below `project()` from step 2c:

```java
    private void newProject() {
        if (!confirmDiscard()) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "What is the game called?", "New project",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose where to put the \"" + name.strip() + "\" folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showDialog(this, "Create Here") != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String folderName = name.strip().replaceAll("[\\\\/:*?\"<>|]", "").strip();
        createProject(chooser.getSelectedFile().toPath().resolve(folderName.isEmpty() ? "Game" : folderName), name);
    }

    /** Makes a project folder with a first, blank map, and opens that map. */
    boolean createProject(Path root, String name) {
        try {
            if (Files.isDirectory(root)) {
                try (var contents = Files.list(root)) {
                    if (contents.findAny().isPresent()) {
                        JOptionPane.showMessageDialog(this, root + " already has files in it.\nChoose an empty or new folder.",
                                APP, JOptionPane.WARNING_MESSAGE);
                        return false;
                    }
                }
            }
            GameProject created = GameProject.create(root, name);
            MapWriter.save(MapEdits.blank("B1F", 20, 20), created.startMapFile());
            if (!openFile(created.startMapFile())) {
                return false;
            }
            status("Created the project in " + created.root() + ". Its maps go in 'maps' and its images in 'assets'.");
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not create the project:\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void openProject() {
        if (!confirmDiscard()) {
            return;
        }
        JFileChooser chooser = new JFileChooser(project == null ? null : project.root().getParent().toFile());
        chooser.setDialogTitle("Choose a project folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path root = chooser.getSelectedFile().toPath();
        if (!GameProject.isProject(root)) {
            JOptionPane.showMessageDialog(this, root + " isn't a project: it has no " + GameProject.FILE_NAME + ".",
                    APP, JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            openFile(GameProject.open(root).startMapFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not open the project:\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The game starts on this map. */
    private void makeStartMap() {
        if (doc.file() == null || !project.contains(doc.file())) {
            status("Save this map inside the project folder first.");
            return;
        }
        try {
            project.setStartMap(doc.file());
            project.save();
            status("The game now starts on " + project.relative(doc.file()) + ".");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save " + GameProject.FILE_NAME + ":\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showProjectFolder() {
        try {
            Desktop.getDesktop().open(project.root().toFile());
        } catch (IOException | UnsupportedOperationException e) {
            status("The project is in " + project.root());
        }
    }
```

Why it's split up like this:

- `newProject` only asks questions. The real work is in `createProject`, which you can call from a test without any
  dialogs popping up.
- `createProject` refuses a folder that already has files in it, so a game never gets mixed into an existing folder.
- Opening a project just opens its starting map. Step 2 already takes care of everything else (the images folder,
  the title, the menu).

## Step 4: Open a project from the command line

In `src/main/java/crawler/editor/MapEditor.java`, add the import:

```java
import crawler.core.GameProject;
```

Then in `main`, replace

```java
                Path requested = Path.of(args[0]).toAbsolutePath();
                if (Files.exists(requested)) {
```

with

```java
                Path requested = Path.of(args[0]).toAbsolutePath();
                if (GameProject.isProject(requested)) {
                    // A project folder: open the map the game starts on.
                    try {
                        requested = GameProject.open(requested).startMapFile();
                    } catch (IOException e) {
                        error = "Could not open the project:\n" + e.getMessage();
                    }
                }
                if (error != null) {
                    // Keep the new, empty map; the error is shown below.
                } else if (Files.exists(requested)) {
```

Now `editor.cmd "C:\Games\Test Quest"` opens the project.

## Step 5: Teach the game to play a project

Replace `src/main/java/crawler/Main.java` with this:

```java
package crawler;

import crawler.core.DungeonMap;
import crawler.core.GameProject;
import crawler.core.MapLoader;
import crawler.render.AssetSource;
import crawler.ui.GameWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point.
 *
 * <pre>
 *   java crawler.Main                plays the published game (see below), or the built-in sample floor
 *   java crawler.Main "My Game"      plays a project folder, starting on its starting map
 *   java crawler.Main my-map.txt     plays one map file
 * </pre>
 *
 * A published game keeps its project in a folder called {@code game} next to the program,
 * and runs it when started with no arguments.
 *
 * The map editor is a separate program: {@link crawler.editor.MapEditor}.
 */
public final class Main {
    private static final String DEFAULT_MAP = "/maps/b1f.txt";
    /** Where a published game keeps its project, next to the program. */
    public static final String BUNDLED_FOLDER = "game";

    private Main() {
    }

    /** What to play: a map, where its images come from, and the window title. */
    public record Loaded(DungeonMap map, AssetSource assets, String title) {
    }

    public static void main(String[] args) {
        Loaded game;
        try {
            game = load(args.length > 0 ? Path.of(args[0]) : bundledGame(programFolder()));
        } catch (IOException | IllegalArgumentException e) {
            // A published game has no console, so say it in a window too.
            System.err.println("Could not start the game: " + e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage(), "Could not start the game", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> GameWindow.open(game.map(), game.assets(), game.title(), true));
    }

    /**
     * Loads a project folder, a single map file, or (for null) the built-in sample.
     * Images are looked up relative to the project folder, or for a map on its own, the map's folder.
     */
    public static Loaded load(Path target) throws IOException {
        if (target == null) {
            DungeonMap map = MapLoader.loadResource(DEFAULT_MAP);
            AssetSource assets = AssetSource.classpath(DEFAULT_MAP.substring(0, DEFAULT_MAP.lastIndexOf('/') + 1));
            return new Loaded(map, assets, "Dungeon Crawler - " + map.name());
        }
        target = target.toAbsolutePath();
        if (Files.isDirectory(target)) {
            if (!GameProject.isProject(target)) {
                throw new IOException(target + " isn't a game project: it has no " + GameProject.FILE_NAME + ".");
            }
            GameProject project = GameProject.open(target);
            DungeonMap map = MapLoader.load(project.startMapFile());
            return new Loaded(map, AssetSource.directory(project.root()), project.name() + " - " + map.name());
        }
        DungeonMap map = MapLoader.load(target);
        Path root = GameProject.findRoot(target);
        return new Loaded(map, AssetSource.directory(root != null ? root : target.getParent()),
                "Dungeon Crawler - " + map.name());
    }

    /** The project shipped with a published game, or null if there isn't one. */
    public static Path bundledGame(Path programFolder) {
        if (programFolder == null) {
            return null;
        }
        Path folder = programFolder.resolve(BUNDLED_FOLDER);
        return GameProject.isProject(folder) ? folder : null;
    }

    /** The folder holding the game's jar (or its classes folder, when run from the build). */
    private static Path programFolder() {
        try {
            Path code = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(code) ? code : code.getParent();
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            return null;
        }
    }
}
```

How it decides what to play:

- **A folder** is a project. The game plays its starting map, and pictures are found from the project folder.
- **A map file** plays just that map. If the map is inside a project, pictures are still found from the project folder.
- **No argument**: the game looks for a folder called `game` next to its own program file. That's where publishing
  puts the project (step 6). If there isn't one, you get the built-in sample floor, as before.
- A published game has no console window, so errors are also shown in a message box. Otherwise the game would
  quietly fail to start.

## Step 6: Publishing

**6a. Share the JDK finder.** `publish.ps1` needs the same "find a JDK" code as `run.ps1`. Rather than copying it,
move it into its own file. Create `jdk.ps1` in the project folder:

```powershell
# Finds a JDK 17 or newer. Shared by run.ps1 and publish.ps1 (dot-source it: . .\jdk.ps1).

function Get-JdkVersion([string]$jdkHome) {
    $release = Join-Path $jdkHome 'release'
    if (-not (Test-Path $release)) { return 0 }
    $match = Select-String -Path $release -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
    if (-not $match) { return 0 }
    $version = $match.Matches[0].Groups[1].Value
    if ($version.StartsWith('1.')) { return [int]$version.Split('.')[1] }
    return [int]($version -split '[.+-]')[0]
}

function Find-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe')) -and (Get-JdkVersion $env:JAVA_HOME) -ge 17) {
        return $env:JAVA_HOME
    }
    $roots = @(
        "$env:USERPROFILE\.jdks",
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Amazon Corretto",
        "$env:ProgramFiles\Zulu",
        "$env:ProgramFiles\BellSoft"
    )
    $best = $roots |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem $_ -Directory } |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
        ForEach-Object { [pscustomobject]@{ Path = $_.FullName; Version = (Get-JdkVersion $_.FullName) } } |
        Where-Object { $_.Version -ge 17 } |
        Sort-Object Version -Descending |
        Select-Object -First 1
    if ($best) { return $best.Path }
    return $null
}
```

Then in `run.ps1`, delete the `Get-JdkVersion` and `Find-Jdk` functions and put this in their place, right after
`$root = $PSScriptRoot`:

```powershell
. (Join-Path $root 'jdk.ps1')
```

**6b. The publish script.** Create `publish.ps1`:

```powershell
<#
.SYNOPSIS
    Turns a project made with the editor into a game anyone can run: a folder with an .exe,
    the game's files and its own copy of Java, so players don't need to install anything.
.EXAMPLE
    .\publish.ps1 "C:\Games\My Game"
#>
param([Parameter(Mandatory = $true)][string]$Project)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
. (Join-Path $root 'jdk.ps1')

$projectDir = (Resolve-Path $Project).Path
$projectFile = Join-Path $projectDir 'project.txt'
if (-not (Test-Path $projectFile)) {
    Write-Error "$projectDir has no project.txt. Make a project with File > New Project in the editor."
}
$nameLine = Select-String -Path $projectFile -Pattern '^\s*name\s*=\s*(.+)$' | Select-Object -First 1
$gameName = if ($nameLine) { $nameLine.Matches[0].Groups[1].Value.Trim() } else { Split-Path $projectDir -Leaf }
# The program's file name: letters, digits, spaces, dashes and underscores only.
$appName = ($gameName -replace '[^A-Za-z0-9 _-]', '').Trim()
if (-not $appName) { $appName = 'Game' }

$jdk = Find-Jdk
if (-not $jdk) {
    Write-Error "No JDK 17 or newer found. Install one (for example: winget install EclipseAdoptium.Temurin.21.JDK) or point JAVA_HOME at it."
}
Write-Host "Using JDK at $jdk"

$work = Join-Path $root 'out\publish'
$classes = Join-Path $work 'classes'
$stage = Join-Path $work 'stage'
$dist = Join-Path $root 'dist'
foreach ($dir in @($classes, $stage)) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Force $dir | Out-Null
}

# 1. Compile the game and pack it into one jar.
$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& (Join-Path $jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $root 'src\main\resources\*') $classes -Recurse -Force
& (Join-Path $jdk 'bin\jar.exe') --create --file (Join-Path $stage 'crawler.jar') --main-class crawler.Main -C $classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 2. The project goes next to the jar in a folder called "game"; Main plays it when started.
Copy-Item $projectDir (Join-Path $stage 'game') -Recurse

# 3. Wrap it up with a Java runtime and an .exe.
New-Item -ItemType Directory -Force $dist | Out-Null
$appDir = Join-Path $dist $appName
if (Test-Path $appDir) { Remove-Item $appDir -Recurse -Force }
& (Join-Path $jdk 'bin\jpackage.exe') --type app-image --name $appName --input $stage `
    --main-jar crawler.jar --main-class crawler.Main --add-modules java.desktop --dest $dist
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Published '$gameName' to $appDir"
Write-Host "Players run $appName.exe. Zip the whole '$appName' folder to share it."
```

What each part does:

1. It compiles the game and packs it into `crawler.jar` with `jar`, which comes with the JDK.
2. It copies the project folder next to the jar as `game`. That's where `Main` looks in step 5.
3. `jpackage`, also part of the JDK, wraps everything into a folder with an `.exe` and its own Java runtime.
   `--add-modules java.desktop` tells it the game only needs Java's desktop parts (Swing and image loading).
   That keeps the copy of Java small.

**6c. A double-click version.** Create `publish.cmd`. The `pause` keeps the window open so you can read the result.

```bat
@echo off
rem Publishes a project made with the editor. Usage: publish.cmd "C:\Games\My Game"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish.ps1" %*
pause
```

**6d.** Add `dist/` to `.gitignore`, so published copies stay out of version control.

## Step 7: Try it

1. Start the editor (`editor.cmd`). Choose **File > New Project**, call it *Test Quest*, and pick where it goes (this guide uses `C:\Games`).
   The editor opens `maps/b1f.txt`, and the title ends in *Test Quest - Map Editor*.
2. Carve a room, then press Ctrl+T and choose a wall picture. Save, then choose **Project > Show Project Folder**. The
   picture is in `Test Quest\assets\`, not next to the map.
3. Choose **File > New**, draw a second floor and save it. The save dialog opens in `maps`. Then choose
   **Project > Make This the Starting Map**.
4. Play the whole project:

   ```powershell
   .\run.ps1 "C:\Games\Test Quest"
   ```

5. Publish it:

   ```powershell
   .\publish.cmd "C:\Games\Test Quest"
   ```

   Then run `dist\Test Quest\Test Quest.exe`.

## How this was checked

I built every step above in a copy of your project and tested it there; your own project's code wasn't changed. 51
automated checks passed. They covered project files, picture paths for maps inside and outside a project, the game
loading a project, the editor's new commands and the dialogs from Walkthrough 2.

I also published a test project. It took 7 seconds and made a 74 MB folder. Its `.exe` opened the game window,
titled *Test Quest - B1F*. Running `run.ps1` with a project folder worked for both the game and the editor.

## If something goes wrong

- **"isn't a game project"**: you pointed the game at a folder with no `project.txt`. Point it at the project folder
  itself, not at `maps`.
- **Pictures show in the editor but not in the game**: the map was probably saved outside the project. When a map is
  in a project, the editor's title shows the project name. Use **Save As** to save it into the project's `maps` folder.
- **`jpackage` complains about the name**: the publish script already strips unusual characters from the game's name.
  If it still complains, rename the game in `project.txt` to plain letters and spaces.
- **Windows SmartScreen warns about the .exe**: that's normal for unsigned programs. Signing the program is a
  separate job, worth doing before a public release.

## Ideas for later

- **An installer instead of a folder**: use `--type msi` instead of `--type app-image` in `publish.ps1`. That needs
  the free WiX Toolset installed.
- **Your own icon**: add `--icon path\to\game.ico` to the `jpackage` line.
- **Stop players editing the game files**: put the project inside the jar and load it with
  `AssetSource.classpath(...)` and `MapLoader.loadResource(...)`. That's how the built-in sample floor works.
