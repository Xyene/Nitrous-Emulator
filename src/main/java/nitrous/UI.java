package nitrous;

import nitrous.cpu.Emulator;
import nitrous.cpu.R;
import nitrous.lcd.Interpolator;
import nitrous.renderer.IRenderManager;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Main emulator UI.
 */
public class UI
{
    /**
     * The main entry point.
     *
     * @param argv command line arguments
     * @throws Exception
     */
    public static void main(String[] argv) throws Exception
    {
        // Use system-default look and feel.
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        // Disable lightweight popup because our panel is heavy weight.
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        // Disable OpenGL and enable Direct3D is the only way Direct3D renderer can work.
        // The OpenGL renderer doesn't seem to care if it is disabled. It works just fine.
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.d3d", "true");

        // Attempt to find the ROM file.
        File rom = null;

        // If there was a command line argument, attempt to load that.
        if (argv.length > 0)
        {
            rom = new File(argv[0]);

            // If it doesn't exist though, we try something else.
            if (!rom.isFile())
            {
                System.err.println(rom + " does not exist");
                rom = null;
            }
        }

        // Try invoking the ROM selection window.
        if (rom == null)
        {
            rom = selectROM();
        }

        // If we still can't get a ROM, give up.
        if (rom == null)
        {
            System.err.println("No ROM provided, exiting...");
            return;
        }

        // Load the ROM.
        Cartridge cartridge = new Cartridge(Files.readAllBytes(rom.toPath()));

        // Create the Emulator.
        Emulator core = new Emulator(cartridge);

        // Specify the save file.
        core.savefile = new File(core.cartridge.gameTitle + ".sav");

        // If there is a battery on the cartridge, then we attempt to load the save file.
        if (core.mmu.hasBattery())
        {
            try
            {
                core.mmu.load(new FileInputStream(core.savefile));
            } catch (Exception ignored)
            {
                // There is nothing you can do if the file fails to load.
            }
        }

        // Initialize the UI with stored full screen and magnification settings.
        initUI(core, Settings.isFullScreen(), Settings.getMagnification());
    }

    /**
     * ROM selection UI.
     *
     * @return the selected ROM
     */
    public static File selectROM() {
        // We use this Semaphore to wait until the user selects a ROM.
        Semaphore selectLock = new Semaphore(1);

        // Create the frame.
        JFrame dialog = new JFrame("NOx Emulator") {
            {
                // Layout of the Frame.
                setTitle("NOx Emulator");
                setSize(new Dimension(R.W * 2, R.H * 2));
                setLayout(new BorderLayout());

                // Stop the waiting if the window is closed.
                addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        selectLock.release();
                    }
                });
            }
        };

        /**
         * A wrapper class that stores a {@link File} object.
         *
         * We need to put the {@link File} object into another class, because subclasses can only
         * access variables declared final, and you can't change final variables.
         */
        class FileReference
        {
            File ref;
        }
        FileReference target = new FileReference();

        // Acquire the semaphore such that the next acquire blocks until release.
        selectLock.acquireUninterruptibly();

        // Create a file filter so that only ROM files can be loaded.
        FileFilter acceptor = new FileFilter()
        {
            @Override
            public boolean accept(File f)
            {
                if (f.isDirectory()) return true;
                String name = f.getName();
                return name.endsWith(".gb") || name.endsWith(".gbc") || name.endsWith(".rom");
            }

            @Override
            public String getDescription()
            {
                return "Gameboy ROM (*.gb, *.gbc, *.rom)";
            }
        };

        /**
         * Creates a drag and drop target for the ROM.
         */
        class ROMDropTarget extends DropTarget
        {
            public void drop(DropTargetDropEvent e)
            {
                try
                {
                    e.acceptDrop(DnDConstants.ACTION_COPY);

                    // Get the dropped file.
                    List<File> droppedFiles = (List<File>)
                            e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    File f = droppedFiles.get(0);

                    // Notify the user if the file is not accepted.
                    if (!acceptor.accept(f))
                    {
                        JOptionPane.showMessageDialog(null,
                                "File must be a " + acceptor.getDescription() + "!", "No ROM image provided",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    // If it's good, set the file, close the window, and release the wait.
                    target.ref = f;
                    dialog.dispose();
                    selectLock.release();
                } catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        };

        // Create the welcome panel.
        JPanel welcome = new JPanel()
        {
            {
                // Create our font.
                Font verdana = new Font("Verdana", Font.PLAIN, 14);

                // Set drop target and layout.
                setDropTarget(new ROMDropTarget());
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

                // Show the drag and drop notification, and a link to the file selection dialog.
                add(new JPanel()
                {
                    {
                        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                        setDropTarget(new ROMDropTarget());

                        add(Box.createHorizontalGlue());
                        add(new LabelBuilder()
                                .setFont(verdana)
                                .append("Drag and drop or ").action("select", (e) -> {
                                    // Display the file chooser.
                                    JFileChooser chooser = new JFileChooser("Choose a game...");
                                    chooser.setVisible(true);
                                    chooser.setFileFilter(acceptor);
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                                    {
                                        // If a file is chosen, set the file, close the window,
                                        // and release the wait.
                                        target.ref = chooser.getSelectedFile();
                                        dialog.dispose();
                                        selectLock.release();
                                    }
                                }).append(" a game to start").create());
                        add(Box.createHorizontalGlue());
                    }
                });

                // Add the recent ROMs label.
                add(new JLabel("Recent ROMs (double-click to run):") {
                    {
                        // Layout and set drop target.
                        setAlignmentX(CENTER_ALIGNMENT);
                        setFont(verdana);
                        setDropTarget(new ROMDropTarget());
                    }
                });

                /**
                 * Helper class to wrap a {@link File} object such that only the filename,
                 * no extension, is displayed when converted to string.
                 */
                class FileNameDisplay {
                    File file;

                    FileNameDisplay(File file) {
                        this.file = file;
                    }

                    @Override
                    public String toString() {
                        // Get the file name and remove the extension.
                        return file.getName().replaceFirst("[.][^.]+$", "");
                    }
                }

                // Create a list display control.
                add(new JScrollPane(new JList<FileNameDisplay>()
                {
                    {
                        // Set drop target and layout.
                        setDropTarget(new ROMDropTarget());
                        setFont(verdana);

                        // Create data model to store the information.
                        DefaultListModel<FileNameDisplay> romModel = new DefaultListModel<>();

                        // Find the 10 top used ROMs and wrap them in FileNameDisplay.
                        for (File rom : Settings.rom.mostUsed(10))
                            romModel.addElement(new FileNameDisplay(rom));

                        // Set the data model.
                        setModel(romModel);

                        // Mouse listener for double click.
                        addMouseListener(new MouseAdapter() {
                            public void mouseClicked(MouseEvent evt) {
                                if (evt.getClickCount() == 2) {
                                    // When a ROM is double clicked, set the file, close the window,
                                    // and release the wait.
                                    target.ref = romModel.elementAt(locationToIndex(evt.getPoint())).file;
                                    dialog.dispose();
                                    selectLock.release();
                                }
                            }
                        });
                    }
                }));
            }
        };

        // Add the welcome panel to the dialog.
        dialog.add(welcome, BorderLayout.CENTER);

        // More dialog settings, before displaying it.
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        dialog.setVisible(true);

        // Wait for a ROM to be selected.
        selectLock.acquireUninterruptibly();

        // Update ROM usage frequency.
        Settings.rom.usedROM(target.ref);

        // Return the selected ROM.
        return target.ref;
    }

    /**
     * Create the emulator UI.
     *
     * @param core the {@link Emulator}
     * @param fullscreen whether to run full screen
     * @param mag the magnification to run on
     */
    private static void initUI(Emulator core, boolean fullscreen, int mag)
    {
        // Create display panel and frame.
        HeavyDisplayPanel display = new HeavyDisplayPanel(core, mag);
        JFrame disp = new JFrame(core.cartridge.gameTitle);

        // Add key listener.
        display.addKeyListener(new KeyAdapter()
        {
            /**
             * Method to update the button states.
             *
             * @param e the {@link KeyEvent}
             * @param to {@literal true} if the key was pressed.
             */
            private void toggle(KeyEvent e, boolean to)
            {
                // Map key code to button, and update button pressed state.
                switch (Settings.keys.getKey(e.getKeyCode()))
                {
                    case Keybinding.KEY_RIGHT:
                        core.buttonRight = to;
                        break;
                    case Keybinding.KEY_LEFT:
                        core.buttonLeft = to;
                        break;
                    case Keybinding.KEY_UP:
                        core.buttonUp = to;
                        break;
                    case Keybinding.KEY_DOWN:
                        core.buttonDown = to;
                        break;
                    case Keybinding.KEY_A:
                        core.buttonA = to;
                        break;
                    case Keybinding.KEY_B:
                        core.buttonB = to;
                        break;
                    case Keybinding.KEY_START:
                        core.buttonStart = to;
                        break;
                    case Keybinding.KEY_SELECT:
                        core.buttonSelect = to;
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                // Handle releasing.
                toggle(e, false);
            }

            @Override
            public void keyPressed(KeyEvent e)
            {
                // Handle pressing.
                toggle(e, true);
            }
        });

        // Add mouse listener.
        display.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent e)
            {
                // Only show meny on right click.
                if (!SwingUtilities.isRightMouseButton(e))
                    return;

                // Create the menu.
                JPopupMenu menu = new JPopupMenu();

                // Add renderer switching.
                menu.add(new JMenu("Renderer")
                {
                    {
                        // A button group for our radio buttons.
                        ButtonGroup group = new ButtonGroup();

                        // Loop over renderers.
                        for (IRenderManager renderer : core.lcd.renderers)
                        {
                            // Get the menu radio item for each renderer.
                            JRadioButtonMenuItem menuItem = renderer.getRadioMenuItem(core.lcd);

                            // Add the radio button to the group so they are exclusive.
                            group.add(menuItem);

                            // Make the current renderer selected.
                            if (renderer == core.lcd.currentRenderer)
                                group.setSelected(menuItem.getModel(), true);

                            add(menuItem);
                        }
                    }
                });

                // Add filter switching.
                menu.add(new JMenu("Filter")
                {
                    {
                        // A button group for our radio buttons.
                        ButtonGroup group = new ButtonGroup();

                        // Loop over interpolators.
                        for (final Interpolator interpolator : Interpolator.values())
                        {
                            // Create the radio button.
                            add(new JRadioButtonMenuItem(interpolator.name)
                            {
                                {
                                    // Add the radio button to the group so they are exclusive.
                                    group.add(this);

                                    // Make the current interpolator selected.
                                    if (interpolator == Settings.getInterpolator())
                                        group.setSelected(getModel(), true);

                                    // Alter settings on click.
                                    addActionListener((e) -> {
                                        Settings.setInterpolator(interpolator);
                                        group.setSelected(getModel(), true);
                                    });
                                }
                            });
                        }
                    }
                });

                // Add sound channel enable toggle submenu.
                menu.add(new JMenu("Sound")
                {
                    {
                        // Loop from channels 1 to 4 inclusive.
                        for (int i = 1; i < 5; i++)
                        {
                            // Use a smaller scoped variable so that listeners don't
                            // capture the variable i, which would be 5 after we are done.
                            int channel = i;

                            // Create the checkbox item.
                            add(new JCheckBoxMenuItem("Channel " + i, Settings.isChannelOn(channel))
                            {
                                {
                                    // Alter settings on click.
                                    addActionListener((x) ->
                                    {
                                        Settings.setChannelOn(channel, !Settings.isChannelOn(channel));
                                    });
                                }
                            });
                        }
                    }
                });

                // Add a separator.
                menu.add(new JSeparator());

                // Add the pause menu checkbox item.
                menu.add(new JCheckBoxMenuItem("Pause", core.isPaused())
                {
                    {
                        // On click, toggle the pause state by controlling the execution lock.
                        addActionListener((x) ->
                        {
                            if (core.isPaused())
                                core.executeLock().release();
                            else
                                core.executeLock().acquireUninterruptibly();
                            core.setPaused(!core.isPaused());
                        });
                    }
                });

                // Add a checkbox for the mute setting.
                menu.add(new JCheckBoxMenuItem("Mute", Settings.isMuted())
                {
                    {
                        // On click, toggle the setting.
                        addActionListener((x) ->
                        {
                            Settings.setMuted(!Settings.isMuted());
                        });
                    }
                });

                // Add a submeny for emulation speed settings.
                menu.add(new JMenu("Speed")
                {
                    {
                        // A button group for our radio buttons.
                        ButtonGroup group = new ButtonGroup();

                        // Loop over all supported speeds.
                        for (final EmulateSpeed speed : EmulateSpeed.values())
                        {
                            // Create and add a radio button for each speed.
                            add(new JRadioButtonMenuItem(speed.name)
                            {
                                {
                                    // Add to button group so that radio buttons are exclusive.
                                    group.add(this);

                                    // Select the current speed.
                                    if (core.clockSpeed == speed.clockSpeed)
                                        group.setSelected(getModel(), true);

                                    // On click, update the speed.
                                    addActionListener(e -> {
                                        Settings.setSpeed(speed);
                                        group.setSelected(getModel(), true);
                                    });
                                }
                            });
                        }
                    }
                });

                // Add volume control submenu.
                menu.add(new JMenu("Volume")
                {
                    {
                        // Add the slider to control volume.
                        add(new JSlider(0, 100, Settings.getVolume())
                        {
                            {
                                // Alter slider layout.
                                setLabelTable(createStandardLabels(50));
                                setSnapToTicks(false);
                                setFocusable(false);
                                setPaintTicks(true);
                                setPaintLabels(true);
                                setPaintTrack(true);

                                // Update volume on slider change.
                                addChangeListener((e) -> {
                                    Settings.setVolume(getValue());
                                });

                                // Only save the volume once the slider is released.
                                addMouseListener(new MouseAdapter()
                                {
                                    @Override
                                    public void mouseReleased(MouseEvent e)
                                    {
                                        Settings.saveVolume();
                                    }
                                });
                            }
                        });
                    }
                });

                // Add full screen checkbox.
                menu.add(new JCheckBoxMenuItem("Fullscreen", Settings.isFullScreen())
                {
                    {
                        // On click, switch full screen state.
                        addActionListener((e) -> {
                            // Pause the game while switching full screen.
                            boolean wasPaused = core.isPaused();
                            core.setPaused(true);

                            // Destroy the window as we are creating a new one.
                            disp.dispose();

                            // Alter full screen setting.
                            Settings.setFullScreen(!Settings.isFullScreen());

                            // Recreate the frame with new settings.
                            initUI(core, Settings.isFullScreen(), Settings.getMagnification());

                            // Resume the game if it wasn't paused before.
                            core.setPaused(wasPaused);

                            // I'm not sure why this has to be invoked later, but if it's not, stuff breaks
                            SwingUtilities.invokeLater(() -> {
                                // Reinitialize the renderers.
                                core.lcd.currentRenderer = null;
                                core.lcd.initializeRenderers();
                            });
                        });
                    }
                });

                // Add magnification selection submenu.
                menu.add(new JMenu("Magnification")
                {
                    {
                        // Get screen so we can find its size.
                        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

                        // Loop over all valid magnification sizes, i.e. the ones that don't go off screen.
                        for (int mag = 1; mag * R.W < screen.width && mag * R.H < screen.height; mag++)
                        {
                            // Again, use another variable so that subclasses don't capture the changing
                            // loop variable.
                            final int magnification = mag;

                            // Create a checkbox element and selecting it if it is the current state.
                            add(new JCheckBoxMenuItem(mag + "x", mag == display.magnification)
                            {
                                {
                                    // On click, switch to new setting.
                                    addActionListener((e) -> {
                                        // Pause the game while switching magnification.
                                        boolean wasPaused = core.isPaused();
                                        core.setPaused(true);

                                        // Destroy the window as we are creating a new one.
                                        disp.dispose();

                                        // Alter magnification setting.
                                        Settings.setMagnification(magnification);

                                        // Recreate the frame with new settings.
                                        initUI(core, Settings.isFullScreen(), magnification);

                                        // Resume the game if it wasn't paused before.
                                        core.setPaused(wasPaused);

                                        // I'm not sure why this has to be invoked later, but if it's not, stuff breaks
                                        SwingUtilities.invokeLater(() -> {
                                            // Reinitialize the renderers.
                                            core.lcd.currentRenderer = null;
                                            core.lcd.initializeRenderers();
                                        });
                                    });
                                }
                            });
                        }
                    }
                });

                // Add a separator.
                menu.add(new JSeparator());

                // Menu item to open the keybinding selection dialog.
                menu.add(new JMenuItem("Keybindings...")
                {
                    {
                        // On click, create and show the dialog.
                        addActionListener((e) -> {
                            new JDialog(disp, "Keybindings...")
                            {
                                {
                                    // Add the panel.
                                    add(new KeybindingSelectionPanel(Settings.keys));

                                    // Pack and show the dialog, disposing it on close.
                                    pack();
                                    setResizable(false);
                                    setLocationRelativeTo(disp);
                                    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                                }
                            }.setVisible(true);
                        });
                    }
                });

                // Show the menu at the mouse position.
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        // Continuing setting up the frame.
        SwingUtilities.invokeLater(() -> {
            // If not full screen, we just use our panel.
            if (!fullscreen)
            {
                disp.setContentPane(display);
            } else
            {
                // Otherwise, we center the panel as to not stretch it.
                disp.setContentPane(new Panel()
                {
                    {
                        // Use BoxLayout to center, and set background to black.
                        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                        add(Box.createVerticalGlue());
                        setBackground(Color.BLACK);
                        setIgnoreRepaint(true);

                        add(new Panel()
                        {
                            {
                                // Use BoxLayout to center, and set background to black.
                                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                                setBackground(Color.BLACK);
                                setIgnoreRepaint(true);

                                add(Box.createHorizontalGlue());
                                add(display);
                                add(Box.createHorizontalGlue());
                            }
                        });

                        add(Box.createVerticalGlue());
                    }
                });
            }

            // Undecorate and maximize if full screen.
            if (fullscreen)
            {
                disp.setUndecorated(true);
                disp.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }

            // Pack the frame, set location, and declare exit on close.
            disp.pack();
            disp.setResizable(false);
            disp.setLocationRelativeTo(null);
            disp.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Add window listener for window close.
            disp.addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowClosing(WindowEvent evt)
                {
                    // Print the amount of CPU cycles executed.
                    System.err.println(core.cycle);

                    // Save the cartridge memory if it has a battery.
                    try (FileOutputStream f = new FileOutputStream(core.savefile))
                    {
                        if (core.mmu.hasBattery())
                        {
                            System.err.println("Saving cart ram");
                            core.mmu.save(f);
                        }
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            });

            // Show the frame.
            disp.setVisible(true);

            // Focus onto the display.
            display.requestFocus();

            // Initialize renderers.
            core.lcd.initializeRenderers();

            // Create code execution thread is not already done.
            if (core.codeExecutionThread.getState() == Thread.State.NEW)
                core.codeExecutionThread.start();
        });

        // Print debug information.
        System.err.println(core.cartridge.gameTitle);
        System.err.println(core.cartridge);
        System.out.flush();
    }
}
