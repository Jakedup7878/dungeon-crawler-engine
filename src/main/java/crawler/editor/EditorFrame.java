package crawler.editor;

import crawler.core.AttackAnimation;
import crawler.core.CharacterClass;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.EnemyType;
import crawler.core.GameContent;
import crawler.core.GameData;
import crawler.core.GameProject;
import crawler.core.MapLoader;
import crawler.core.MapObject;
import crawler.core.MapWriter;
import crawler.core.PartyMember;
import crawler.core.ProjectSettings;
import crawler.core.SpriteRef;
import crawler.core.Surface;
import crawler.render.ObjectLooks;
import crawler.ui.GameWindow;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The map editor's main window: menus, toolbar, the map, the side panel and a status bar. */
final class EditorFrame extends JFrame {
    private static final String APP = "Map Editor";
    private static final int MENU_KEY = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final AssetStore assets;
    private final ObjectLooks looks;
    private final MapDocument doc;
    private final MapCanvas canvas;
    private final SidePanel side;
    private final JLabel statusMessage = new JLabel(" ");
    private final JLabel statusPosition = new JLabel(" ");
    private final Map<Tool, JToggleButton> toolButtons = new EnumMap<>(Tool.class);
    private final Map<Tool, JRadioButtonMenuItem> toolItems = new EnumMap<>(Tool.class);
    private final Action undoAction;
    private final Action redoAction;
    /** Project menu items that only make sense while a project is open. */
    private final List<Action> projectActions = new ArrayList<>();
    /** The project the open map belongs to, or null for a map on its own. */
    private GameProject project;
    private int previewX;
    private int previewY;
    private Direction previewFacing = Direction.NORTH;

    EditorFrame(DungeonMap map, Path file) {
        super(APP);
        assets = new AssetStore(file == null ? null : assetRoot(file));
        looks = new ObjectLooks(assets);
        doc = new MapDocument(map, file, assets);
        project = projectOf(file);
        canvas = new MapCanvas(doc, looks);
        side = new SidePanel(doc, assets, looks);
        applyProject();

        undoAction = action("Undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU_KEY), doc::undo);
        redoAction = action("Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, MENU_KEY), doc::redo);

        JScrollPane scroll = new JScrollPane(canvas);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(12, 13, 18));

        setJMenuBar(menuBar());
        add(toolBar(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        add(statusBar(), BorderLayout.SOUTH);
        bindExtraKeys();

        canvas.setListener(new MapCanvas.Listener() {
            @Override
            public void hoverChanged(int cellX, int cellY, EdgeRef edge) {
                onHover(cellX, cellY, edge);
            }

            @Override
            public void message(String text) {
                status(text);
            }

            @Override
            public void objectClicked(int cellX, int cellY) {
                placeOrEditObject(cellX, cellY);
            }
        });
        doc.addListener(this::documentChanged);

        resetPreview();
        selectTool(Tool.ROOM);
        documentChanged();
        updatePosition(-1, -1, null);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        setSize(1280, 840);
        setMinimumSize(new Dimension(1040, 680));
        setLocationRelativeTo(null);
    }

    /** Shows the window and fits the map into it. */
    void showWindow() {
        setVisible(true);
        SwingUtilities.invokeLater(canvas::zoomToFit);
        status(doc.file() == null
                ? "Drag with the Room tool to carve your first room. F1 shows all the controls."
                : "Opened " + doc.file() + ".");
    }

    MapDocument document() {
        return doc;
    }

    MapCanvas canvas() {
        return canvas;
    }

    AssetStore assets() {
        return assets;
    }

    // ------------------------------------------------------------ layout

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = menu("File", KeyEvent.VK_F);
        file.add(action("New Project...", null, this::newProject));
        file.add(action("Open Project...", null, this::openProject));
        file.addSeparator();
        file.add(action("New...", KeyStroke.getKeyStroke(KeyEvent.VK_N, MENU_KEY), this::newMap));
        file.add(action("Open...", KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU_KEY), this::openMap));
        file.addSeparator();
        file.add(action("Save", KeyStroke.getKeyStroke(KeyEvent.VK_S, MENU_KEY), this::save));
        file.add(action("Save As...", KeyStroke.getKeyStroke(KeyEvent.VK_S, MENU_KEY | InputEvent.SHIFT_DOWN_MASK),
                this::saveAs));
        file.addSeparator();
        file.add(action("Exit", null, this::exit));
        bar.add(file);

        JMenu edit = menu("Edit", KeyEvent.VK_E);
        edit.add(undoAction);
        edit.add(redoAction);
        edit.addSeparator();
        edit.add(action("Rename Map...", null, this::renameMap));
        edit.add(action("Resize Map...", null, this::resizeMap));
        edit.add(action("Textures...", KeyStroke.getKeyStroke(KeyEvent.VK_T, MENU_KEY), this::editTextures));
        edit.add(action("Map Settings (Music and Battles)...", KeyStroke.getKeyStroke(KeyEvent.VK_M, MENU_KEY),
                this::editMapSettings));
        bar.add(edit);

        JMenu tools = menu("Tools", KeyEvent.VK_T);
        ButtonGroup group = new ButtonGroup();
        for (Tool tool : Tool.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(tool.label, Icons.tool(tool));
            item.setAccelerator(KeyStroke.getKeyStroke(tool.numberKey, 0));
            item.addActionListener(e -> selectTool(tool));
            group.add(item);
            tools.add(item);
            toolItems.put(tool, item);
        }
        bar.add(tools);

        JMenu view = menu("View", KeyEvent.VK_V);
        view.add(action("Zoom In", KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, MENU_KEY), () -> canvas.zoomBy(1, null)));
        view.add(action("Zoom Out", KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, MENU_KEY), () -> canvas.zoomBy(-1, null)));
        view.add(action("Fit Map in Window", KeyStroke.getKeyStroke(KeyEvent.VK_0, MENU_KEY), canvas::zoomToFit));
        view.addSeparator();
        JCheckBoxMenuItem unreachable = new JCheckBoxMenuItem("Show Unreachable Cells in Red", true);
        unreachable.addActionListener(e -> canvas.setShowUnreachable(unreachable.isSelected()));
        view.add(unreachable);
        view.addSeparator();
        view.add(action("Turn Preview Left", KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0),
                () -> setPreview(previewX, previewY, previewFacing.turnLeft())));
        view.add(action("Turn Preview Right", KeyStroke.getKeyStroke(KeyEvent.VK_E, 0),
                () -> setPreview(previewX, previewY, previewFacing.turnRight())));
        bar.add(view);

        JMenu projectMenu = menu("Project", KeyEvent.VK_P);
        projectMenu.add(projectAction("Settings...", this::editProjectSettings));
        projectMenu.add(projectAction("Make This the Starting Map", this::makeStartMap));
        projectMenu.add(projectAction("Show Project Folder", this::showProjectFolder));
        projectMenu.addSeparator();
        projectMenu.add(projectAction("Party Members...", this::editParty));
        projectMenu.add(projectAction("Classes...", this::editClasses));
        projectMenu.add(projectAction("Enemies...", this::editEnemies));
        projectMenu.add(projectAction("Attack Animations...", this::editAnimations));
        bar.add(projectMenu);

        JMenu test = menu("Test", KeyEvent.VK_S);
        Action play = action("Play Test from Start", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), () -> playTest(false));
        play.putValue(Action.SMALL_ICON, Icons.play());
        test.add(play);
        test.add(action("Play Test from Preview", KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), () -> playTest(true)));
        bar.add(test);

        JMenu help = menu("Help", KeyEvent.VK_H);
        help.add(action("Controls...", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), () -> MapDialogs.showHelp(this)));
        bar.add(help);
        return bar;
    }

    private JToolBar toolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        ButtonGroup group = new ButtonGroup();
        for (Tool tool : Tool.values()) {
            JToggleButton button = new JToggleButton(tool.label, Icons.tool(tool));
            button.setToolTipText(tool.label + " (" + tool.shortcut() + ") - " + tool.primaryHint);
            button.setFocusable(false);
            button.addActionListener(e -> selectTool(tool));
            group.add(button);
            bar.add(button);
            toolButtons.put(tool, button);
        }
        bar.addSeparator(new Dimension(18, 0));
        JButton textures = toolButton(action("Textures", null, this::editTextures),
                "Use your own images for walls, doors, floor and ceiling (Ctrl+T)");
        textures.setIcon(Icons.textures());
        bar.add(textures);
        bar.addSeparator(new Dimension(18, 0));
        bar.add(toolButton(undoAction, "Undo (Ctrl+Z)"));
        bar.add(toolButton(redoAction, "Redo (Ctrl+Y)"));
        bar.addSeparator(new Dimension(18, 0));
        JButton play = toolButton(action("Play Test", null, () -> playTest(false)), "Walk the map in the game (F5)");
        play.setIcon(Icons.play());
        bar.add(play);
        return bar;
    }

    private JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 205)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        bar.add(statusMessage, BorderLayout.CENTER);
        bar.add(statusPosition, BorderLayout.EAST);
        return bar;
    }

    /** Letter shortcuts for the tools, and the usual alternatives for redo and zoom. */
    private void bindExtraKeys() {
        JComponent root = getRootPane();
        for (Tool tool : Tool.values()) {
            bind(root, KeyStroke.getKeyStroke(tool.letterKey, 0), "tool-" + tool, () -> selectTool(tool));
        }
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU_KEY | InputEvent.SHIFT_DOWN_MASK), "redo", doc::redo);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_ADD, MENU_KEY), "zoom-in", () -> canvas.zoomBy(1, null));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, MENU_KEY | InputEvent.SHIFT_DOWN_MASK), "zoom-in-plus",
                () -> canvas.zoomBy(1, null));
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, MENU_KEY), "zoom-out", () -> canvas.zoomBy(-1, null));
    }

    private static void bind(JComponent component, KeyStroke key, String name, Runnable run) {
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        });
    }

    private static JMenu menu(String name, int mnemonic) {
        JMenu menu = new JMenu(name);
        menu.setMnemonic(mnemonic);
        return menu;
    }

    private static Action action(String name, KeyStroke key, Runnable run) {
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        };
        if (key != null) {
            action.putValue(Action.ACCELERATOR_KEY, key);
        }
        return action;
    }

    /** A menu action that is only enabled while a project is open. */
    private Action projectAction(String name, Runnable run) {
        Action action = action(name, null, run);
        projectActions.add(action);
        return action;
    }

    private static JButton toolButton(Action action, String tip) {
        JButton button = new JButton(action);
        button.setToolTipText(tip);
        button.setFocusable(false);
        return button;
    }

    // ------------------------------------------------------------ state

    void selectTool(Tool tool) {
        canvas.setTool(tool);
        side.setTool(tool);
        toolButtons.get(tool).setSelected(true);
        toolItems.get(tool).setSelected(true);
    }

    private void documentChanged() {
        DungeonMap map = doc.map();
        String fileName = doc.file() == null ? "Untitled" : doc.file().getFileName().toString();
        String projectName = project == null ? "" : " - " + project.name();
        setTitle((doc.isDirty() ? "*" : "") + fileName + " - " + map.name() + projectName + " - " + APP);
        for (Action action : projectActions) {
            action.setEnabled(project != null);
        }
        undoAction.setEnabled(doc.canUndo());
        redoAction.setEnabled(doc.canRedo());
        if (!map.inBounds(previewX, previewY)) {
            resetPreview();
        }
    }

    private void onHover(int x, int y, EdgeRef edge) {
        DungeonMap map = doc.map();
        if (map.inBounds(x, y) && map.cell(x, y).walkable) {
            setPreview(x, y, previewFacing);
        }
        updatePosition(x, y, edge);
    }

    private void updatePosition(int x, int y, EdgeRef edge) {
        DungeonMap map = doc.map();
        StringBuilder text = new StringBuilder();
        if (edge != null) {
            EdgeType type = edge.get(map);
            text.append(type == EdgeType.OPEN ? "Open" : type == EdgeType.WALL ? "Wall" : "Door")
                    .append(" on the ").append(edge.describe(map));
        } else if (map.inBounds(x, y)) {
            text.append("Cell ").append(x).append(", ").append(y)
                    .append(map.cell(x, y).walkable ? " (floor)" : " (rock)");
            MapObject object = map.object(x, y);
            if (object != null) {
                text.append(": ").append(object.type().displayName);
                if (!object.name().isEmpty()) {
                    text.append(" \"").append(object.name()).append('"');
                }
            }
        }
        if (!text.isEmpty()) {
            text.append("     ");
        }
        text.append("Zoom ").append(canvas.zoom()).append(" px");
        statusPosition.setText(text.toString());
    }

    private void resetPreview() {
        DungeonMap map = doc.map();
        setPreview(map.startX(), map.startY(), map.startFacing());
    }

    private void setPreview(int x, int y, Direction facing) {
        previewX = x;
        previewY = y;
        previewFacing = facing;
        side.setPreview(x, y, facing);
        canvas.setPreviewFacing(facing);
    }

    void status(String text) {
        statusMessage.setText(text);
        updatePosition(-1, -1, null);
    }

    // ------------------------------------------------------------ commands

    private void newMap() {
        if (!confirmDiscard()) {
            return;
        }
        MapDialogs.NewMap spec = MapDialogs.askNewMap(this);
        if (spec == null) {
            return;
        }
        assets.reset(project == null ? null : project.root()); // a new map in a project shares its images
        looks.clear();
        side.assetsChanged();
        doc.reset(MapEdits.blank(spec.name(), spec.width(), spec.height()), null);
        afterLoad("New " + spec.width() + " x " + spec.height()
                + " map. Drag with the Room or Corridor tool to start digging around the yellow arrow.");
    }

    private void openMap() {
        if (!confirmDiscard()) {
            return;
        }
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile().toPath());
        }
    }

    /** Opens a map file, reporting problems in a dialog. */
    boolean openFile(Path file) {
        try {
            DungeonMap map = MapLoader.load(file);
            project = projectOf(file);
            assets.reset(assetRoot(file));
            looks.clear();
            applyProject();
            doc.reset(map, file.toAbsolutePath());
            afterLoad("Opened " + file.toAbsolutePath() + ".");
            return true;
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Could not open " + file.getFileName() + ":\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void afterLoad(String message) {
        resetPreview();
        SwingUtilities.invokeLater(canvas::zoomToFit);
        status(message);
    }

    private boolean save() {
        return doc.file() == null ? saveAs() : saveTo(doc.file());
    }

    private boolean saveAs() {
        JFileChooser chooser = chooser();
        chooser.setSelectedFile(doc.file() != null
                ? doc.file().toFile()
                : new File(chooser.getCurrentDirectory(), suggestedFileName(doc.map().name())));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        Path file = chooser.getSelectedFile().toPath().toAbsolutePath();
        if (!file.getFileName().toString().contains(".")) {
            file = file.resolveSibling(file.getFileName() + ".txt");
        }
        if (Files.exists(file) && !file.equals(doc.file())) {
            int answer = JOptionPane.showConfirmDialog(this, file.getFileName() + " already exists. Replace it?",
                    APP, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        return saveTo(file);
    }

    /** Writes the map to {@code file}; refuses maps the game could not load. */
    boolean saveTo(Path file) {
        MapCheck check = doc.check();
        if (check.floorCells() == 0) {
            JOptionPane.showMessageDialog(this,
                    "This map has no floor yet, so the game couldn't load it.\nCarve a room or dig a corridor first.",
                    APP, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        try {
            // Pictures first, so the map file never points at images that aren't there.
            Path folder = assetRoot(file);
            Map<String, String> renamed = assets.saveImages(doc.map(), folder);
            if (!renamed.isEmpty()) {
                doc.replace(doc.map().withRenamedAssets(renamed));
            }
            MapWriter.save(doc.map(), file);
            assets.setFolder(folder);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save " + file + ":\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
            return false;
        }
        project = projectOf(file); // saving into a project folder makes the map part of that project
        applyProject();
        doc.markSaved(file.toAbsolutePath());
        status("Saved " + file.toAbsolutePath() + "."
                + (check.problems().isEmpty() ? "" : "  Note: " + check.problems().get(0)));
        return true;
    }

    private JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser(startDirectory().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Map files (*.txt)", "txt"));
        return chooser;
    }

    /** The current file's folder, else the project's maps folder, else the working directory. */
    private Path startDirectory() {
        if (doc.file() != null && doc.file().getParent() != null) {
            return doc.file().getParent();
        }
        if (project != null) {
            return project.mapsFolder();
        }
        Path working = Path.of(System.getProperty("user.dir"));
        Path maps = working.resolve(Path.of("src", "main", "resources", "sample", "maps"));
        return Files.isDirectory(maps) ? maps : working;
    }

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

    private void editParty() {
        try {
            List<PartyMember> party = GameData.loadParty(project.root());
            List<PartyMember> edited = RosterDialog.show(this, "Party - " + project.name(),
                    "The party the game starts with, in marching order (up to " + GameData.MAX_PARTY + "). "
                            + "Saved in party.txt when you press OK.",
                    party, new PartyForm(assets, classes()), GameData.MAX_PARTY);
            if (edited == null) {
                return;
            }
            // Copy any new portraits into the project's assets folder, then save the list.
            Map<String, String> renamed = assets.saveImages(
                    edited.stream().map(PartyMember::portrait).filter(Objects::nonNull).toList(), project.root());
            edited = edited.stream().map(m -> m.withPortrait(renamed.getOrDefault(m.portrait(), m.portrait()))).toList();
            GameData.saveParty(project.root(), edited);
            status("Saved " + edited.size() + " party members in " + GameData.PARTY_FILE + ".");
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Could not edit the party:\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editEnemies() {
        try {
            List<EnemyType> enemies = GameData.loadEnemies(project.root());
            List<EnemyType> edited = RosterDialog.show(this, "Enemies - " + project.name(),
                    "Every kind of enemy in the game. Encounters will refer to them by id. "
                            + "Saved in enemies.txt when you press OK.",
                    enemies, new EnemyForm(assets, animations()), Integer.MAX_VALUE);
            if (edited == null) {
                return;
            }
            Map<String, String> renamed = saveFiles(edited.stream().map(EnemyType::sprite)
                    .filter(Objects::nonNull).map(SpriteRef::image).toList());
            edited = edited.stream().map(e -> e.withSprite(renamed(e.sprite(), renamed))).toList();
            GameData.saveEnemies(project.root(), edited);
            applyProject();
            status("Saved " + edited.size() + " kinds of enemy in " + GameData.ENEMIES_FILE + ".");
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Could not edit the enemies:\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editClasses() {
        try {
            List<CharacterClass> classes = GameData.loadClasses(project.root());
            List<CharacterClass> edited = RosterDialog.show(this, "Classes - " + project.name(),
                    "The classes party members can belong to. With none, the game uses Fighter, Mage, Priest and Thief. "
                            + "Saved in classes.txt when you press OK.",
                    classes, new ClassForm(assets, animations()), Integer.MAX_VALUE);
            if (edited == null) {
                return;
            }
            Map<String, String> renamed = saveFiles(edited.stream().map(CharacterClass::portrait)
                    .filter(Objects::nonNull).toList());
            edited = edited.stream().map(c -> c.withPortrait(renamed(c.portrait(), renamed))).toList();
            GameData.saveClasses(project.root(), edited);
            status("Saved " + edited.size() + " classes in " + GameData.CLASSES_FILE + ".");
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Could not edit the classes:\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editAnimations() {
        try {
            List<AttackAnimation> animations = GameData.loadAnimations(project.root());
            List<AttackAnimation> edited = RosterDialog.show(this, "Attack Animations - " + project.name(),
                    "How attacks look and sound. Give them to classes and enemies in their own lists. "
                            + "Saved in animations.txt when you press OK.",
                    animations, new AnimationForm(assets), Integer.MAX_VALUE);
            if (edited == null) {
                return;
            }
            Map<String, String> renamed = saveFiles(edited.stream().flatMap(a -> Stream.of(
                    a.image() == null ? null : a.image().image(), a.sound())).filter(Objects::nonNull).toList());
            edited = edited.stream().map(a -> a.withFiles(renamed(a.image(), renamed), renamed(a.sound(), renamed))).toList();
            GameData.saveAnimations(project.root(), edited);
            status("Saved " + edited.size() + " attack animations in " + GameData.ANIMATIONS_FILE + ".");
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Could not edit the animations:\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editProjectSettings() {
        SettingsDialogs.ProjectChoices choices = SettingsDialogs.projectSettings(this, project, assets);
        if (choices == null) {
            return;
        }
        try {
            ProjectSettings s = choices.settings();
            Map<String, String> renamed = saveFiles(s.assetReferences());
            project.setName(choices.name());
            project.setSettings(new ProjectSettings(renamed(s.npcSprite(), renamed), renamed(s.chestSprite(), renamed),
                    renamed(s.chestOpenSprite(), renamed), renamed(s.battleMusic(), renamed),
                    renamed(s.victoryMusic(), renamed), s.partyCreation(), s.partySize(), s.startingGold()));
            project.save();
            applyProject();
            documentChanged();
            status("Saved the project settings in " + GameProject.FILE_NAME + ".");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save the settings:\n" + e.getMessage(),
                    APP, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editMapSettings() {
        List<EnemyType> enemies = project == null ? List.of() : enemiesOrEmpty();
        SettingsDialogs.MapSettings chosen = SettingsDialogs.mapSettings(this, doc.map(), assets, enemies);
        if (chosen == null) {
            return;
        }
        DungeonMap next = doc.map().copy();
        next.setMusic(chosen.music());
        next.setEncounters(chosen.encounters());
        doc.replace(next);
        doc.refresh();
        status(chosen.encounters().active()
                ? "Random battles: " + chosen.encounters().rate() + "% a step. F5 to try them. Ctrl+Z undoes it."
                : "Map settings updated. Ctrl+Z undoes it.");
    }

    // ------------------------------------------------------------ project content

    /** Uses the open project's default looks, and tells the checks which enemies exist. */
    private void applyProject() {
        looks.setDefaults(project == null ? ProjectSettings.DEFAULT : project.settings());
        doc.setKnownEnemies(project == null ? null
                : enemiesOrEmpty().stream().map(EnemyType::id).collect(Collectors.toSet()));
        side.assetsChanged();
    }

    private List<EnemyType> enemiesOrEmpty() {
        try {
            return GameData.loadEnemies(project.root());
        } catch (IOException | IllegalArgumentException e) {
            status("Couldn't read " + GameData.ENEMIES_FILE + ": " + e.getMessage());
            return List.of();
        }
    }

    /** The project's classes, or the built-in ones. */
    private List<CharacterClass> classes() {
        try {
            List<CharacterClass> classes = GameData.loadClasses(project.root());
            return classes.isEmpty() ? CharacterClass.builtIn() : classes;
        } catch (IOException | IllegalArgumentException e) {
            return CharacterClass.builtIn();
        }
    }

    private List<AttackAnimation> animations() {
        try {
            return GameData.loadAnimations(project.root());
        } catch (IOException | IllegalArgumentException e) {
            return List.of();
        }
    }

    /** Everything the play test needs besides the map; empty for a map outside any project. */
    private GameContent content() throws IOException {
        return project == null ? GameContent.EMPTY : GameContent.load(project);
    }

    /** Copies just-imported files into the project's assets folder; returns any that had to be renamed. */
    private Map<String, String> saveFiles(Collection<String> references) throws IOException {
        return assets.saveImages(Set.copyOf(references), project.root());
    }

    private static String renamed(String reference, Map<String, String> renamed) {
        return reference == null ? null : renamed.getOrDefault(reference, reference);
    }

    private static SpriteRef renamed(SpriteRef sprite, Map<String, String> renamed) {
        return sprite == null ? null : sprite.withImage(renamed.getOrDefault(sprite.image(), sprite.image()));
    }

    private void showProjectFolder() {
        try {
            Desktop.getDesktop().open(project.root().toFile());
        } catch (IOException | UnsupportedOperationException e) {
            status("The project is in " + project.root());
        }
    }

    static String suggestedFileName(String mapName) {
        String slug = mapName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return (slug.isEmpty() ? "map" : slug) + ".txt";
    }

    /** If there are unsaved changes, asks whether to save them. False means "cancel". */
    private boolean confirmDiscard() {
        if (!doc.isDirty()) {
            return true;
        }
        String name = doc.file() == null ? "this new map" : doc.file().getFileName().toString();
        int answer = JOptionPane.showConfirmDialog(this, "Save your changes to " + name + "?", APP,
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            return save();
        }
        return answer == JOptionPane.NO_OPTION;
    }

    private void exit() {
        if (confirmDiscard()) {
            dispose();
            System.exit(0);
        }
    }

    private void renameMap() {
        String name = MapDialogs.askName(this, doc.map().name());
        if (name != null) {
            DungeonMap renamed = doc.map().copy();
            renamed.setName(name);
            doc.replace(renamed);
        }
    }

    private void resizeMap() {
        MapDialogs.Resize resize = MapDialogs.askResize(this, doc.map());
        if (resize != null) {
            doc.replace(doc.map().resized(resize.width(), resize.height(), resize.offsetX(), resize.offsetY()));
            status("Map is now " + resize.width() + " x " + resize.height() + ". Undo (Ctrl+Z) puts it back.");
        }
    }

    private void editTextures() {
        Map<Surface, String> chosen = TextureDialog.show(this, doc.map(), assets);
        if (chosen == null) {
            return;
        }
        DungeonMap next = doc.map().copy();
        chosen.forEach(next::setTexture);
        doc.replace(next);
        doc.refresh();
        status(next.textures().isEmpty() ? "Using the built-in textures."
                : "Textures updated. The 3D preview shows them; Ctrl+Z undoes it.");
    }

    /** With an object tool: a click on an empty floor cell adds an object, a click on an object edits it. */
    void placeOrEditObject(int x, int y) {
        DungeonMap map = doc.map();
        MapObject existing = map.object(x, y);
        MapObject edited;
        if (existing != null) {
            edited = ObjectDialog.show(this, existing, false, assets, looks, enemyIds());
        } else {
            Tool tool = canvas.tool();
            if (!tool.placesObjects()) {
                return;
            }
            String problem = MapEdits.objectProblem(map, tool.objectType, x, y);
            if (problem != null) {
                status(problem);
                return;
            }
            edited = ObjectDialog.show(this, MapObject.create(tool.objectType, x, y), true, assets, looks, enemyIds());
        }
        if (edited == null || edited.equals(existing)) {
            return;
        }
        doc.beginEdit();
        doc.map().putObject(edited);
        doc.endEdit();
        doc.refresh();
        status((existing == null ? "Added " : "Updated ") + "the " + edited.type().lowerName() + " at " + x + ", " + y + ".");
    }

    private List<String> enemyIds() {
        return project == null ? List.of() : enemiesOrEmpty().stream().map(EnemyType::id).toList();
    }

    /** Opens the map in a game window, from the start or from where the 3D preview stands. */
    void playTest(boolean fromPreview) {
        DungeonMap copy = doc.map().copy();
        if (fromPreview) {
            copy.setStart(previewX, previewY, previewFacing);
        }
        if (!copy.cell(copy.startX(), copy.startY()).walkable || copy.isBlocked(copy.startX(), copy.startY())) {
            JOptionPane.showMessageDialog(this,
                    "The start is inside solid rock, so there's nowhere to stand.\n"
                            + "Use the Start tool (5) to put it on a floor cell.",
                    "Play test", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        GameContent content;
        try {
            content = content();
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "The project's files have a problem, so the play test can't start:\n"
                    + e.getMessage(), "Play test", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // From the start, the play test offers party creation (if the project does); from the preview it doesn't.
        GameWindow.open(copy, content, assets, "Play test - " + copy.name() + "   (Esc to close)", false, !fromPreview);
        status("Play test running in its own window. It uses the map as it was when you pressed Play.");
    }
}
