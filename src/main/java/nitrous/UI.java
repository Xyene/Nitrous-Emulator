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
import java.util.List;
import java.util.concurrent.Semaphore;

public class UI
{
    //public static VRAMViewer debugger;

    public static void main(String[] argv) throws IOException, ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException
    {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        System.setProperty("sun.java2d.opengl", "false");
//        System.setProperty("sun.java2d.xrender", "false");
        System.setProperty("sun.java2d.d3d", "true");


        File rom = null;
        if (argv.length > 0)
        {
            rom = new File(argv[0]);
            if (!rom.exists())
            {
                System.err.println(rom + " does not exist");
                rom = null;
            }
        }

        if (rom == null)
        {
            rom = selectROM();
        }

        if (rom == null)
        {
            System.err.println("No ROM provided, exiting...");
            return;
        }

        FileInputStream in = new FileInputStream(rom);
        byte[] buf = new byte[(int) rom.length()];
        // TODO fix
        in.read(buf);
        in.close();


        Cartridge cartridge = new Cartridge(buf);
        Emulator core = new Emulator(cartridge);

        core.savefile = new File(core.cartridge.gameTitle + ".sav");

        if (core.mmu.hasBattery())
        {
            try
            {
                core.mmu.load(new FileInputStream(core.savefile));
            } catch (Exception ignored)
            {

            }
        }

        initUI(core, Settings.isFullScreen(), Settings.getMagnification());
    }

    public static File selectROM() {
        Semaphore selectLock = new Semaphore(1);

        JFrame dialog = new JFrame("NOx Emulator") {
            {
                setTitle("NOx Emulator");
                setSize(new Dimension(R.W * 2, R.H * 2));
                setLayout(new BorderLayout());
                addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        selectLock.release();
                    }
                });
            }
        };

        class FileReference
        {
            File ref;
        }
        FileReference target = new FileReference();

        selectLock.acquireUninterruptibly();

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

        class ROMDropTarget extends DropTarget
        {
            public void drop(DropTargetDropEvent e)
            {
                try
                {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>)
                            e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    File f = droppedFiles.get(0);
                    if (!acceptor.accept(f))
                    {
                        JOptionPane.showMessageDialog(null,
                                "File must be a " + acceptor.getDescription() + "!", "No ROM image provided",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    target.ref = f;
                    dialog.dispose();
                    selectLock.release();
                } catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        };

        JPanel welcome = new JPanel()
        {
            {
                Font verdana = new Font("Verdana", Font.PLAIN, 14);
                setDropTarget(new ROMDropTarget());
                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

                //add(Box.createVerticalStrut(20));
                add(new JPanel()
                {
                    {
                        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                        setDropTarget(new ROMDropTarget());

                        add(Box.createHorizontalGlue());
                        add(new LabelBuilder()
                                .setFont(verdana)
                                .append("Drag and drop or ").action("select", (e) -> {
                                    JFileChooser chooser = new JFileChooser("Choose a game...");
                                    chooser.setVisible(true);
                                    chooser.setFileFilter(acceptor);
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                                    {
                                        target.ref = chooser.getSelectedFile();
                                        dialog.dispose();
                                        selectLock.release();
                                    }
                                }).append(" a game to start").create());
                        add(Box.createHorizontalGlue());
                    }
                });

                //add(Box.createVerticalStrut(20));
                add(new JLabel("Recent ROMs (double-click to run):") {
                    {
                        setAlignmentX(CENTER_ALIGNMENT);
                        setFont(verdana);
                        setDropTarget(new ROMDropTarget());
                    }
                });

                class FileNameDisplay {
                    File file;

                    FileNameDisplay(File file) {
                        this.file = file;
                    }

                    @Override
                    public String toString() {
                        return file.getName().replaceFirst("[.][^.]+$", "");
                    }
                }

                add(new JScrollPane(new JList<FileNameDisplay>()
                {
                    {
                        setDropTarget(new ROMDropTarget());
                        setFont(verdana);

                        DefaultListModel<FileNameDisplay> romModel = new DefaultListModel<>();

                        for (File rom : Settings.rom.mostUsed(10))
                            romModel.addElement(new FileNameDisplay(rom));

                        setModel(romModel);

                        addMouseListener(new MouseAdapter() {
                            public void mouseClicked(MouseEvent evt) {
                                if (evt.getClickCount() == 2) {
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

        dialog.add(welcome, BorderLayout.CENTER);

        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        dialog.setVisible(true);

        selectLock.acquireUninterruptibly();

        Settings.rom.usedROM(target.ref.getAbsolutePath());
        return target.ref;
    }

    private static void initUI(Emulator core, boolean fullscreen, int mag)
    {
        HeavyDisplayPanel display = new HeavyDisplayPanel(core, mag);
        JFrame disp = new JFrame(core.cartridge.gameTitle);

        //    debugger = new VRAMViewer(core);

        KeyListener toggler = new KeyAdapter()
        {
            private void toggle(KeyEvent e, boolean to)
            {
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
                toggle(e, false);
            }

            @Override
            public void keyPressed(KeyEvent e)
            {
                toggle(e, true);
            }
        };
        display.addKeyListener(toggler);

        display.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent e)
            {

                if (!SwingUtilities.isRightMouseButton(e))
                    return;

                JPopupMenu menu = new JPopupMenu();
                menu.add(new JMenu("Renderer")
                {
                    {
                        ButtonGroup group = new ButtonGroup();

                        for (IRenderManager renderer : core.lcd.renderers)
                        {
                            JRadioButtonMenuItem menuItem = renderer.getRadioMenuItem(core.lcd);
                            group.add(menuItem);
                            if (renderer == core.lcd.currentRenderer)
                                group.setSelected(menuItem.getModel(), true);
                            add(menuItem);
                        }
                    }
                });
                menu.add(new JMenu("Filter")
                {
                    {
                        ButtonGroup group = new ButtonGroup();
                        for (final Interpolator interpolator : Interpolator.values())
                        {
                            add(new JRadioButtonMenuItem(interpolator.name)
                            {
                                {
                                    group.add(this);

                                    if (interpolator == Settings.getInterpolator())
                                        group.setSelected(getModel(), true);

                                    addActionListener((e) -> {
                                        Settings.setInterpolator(interpolator);
                                        group.setSelected(getModel(), true);
                                    });
                                }
                            });
                        }
                    }
                });
                menu.add(new JMenu("Sound")
                {
                    {
                        for (int i = 1; i < 5; i++)
                        {
                            int channel = i;
                            add(new JCheckBoxMenuItem("Channel " + i, Settings.isChannelOn(channel))
                            {
                                {
                                    addActionListener((x) ->
                                    {
                                        Settings.setChannelOn(channel, !Settings.isChannelOn(channel));
                                    });
                                }
                            });
                        }
                    }
                });
                menu.add(new JSeparator());
                menu.add(new JCheckBoxMenuItem("Pause", core.isPaused())
                {
                    {
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
                menu.add(new JCheckBoxMenuItem("Mute", Settings.isMuted())
                {
                    {
                        addActionListener((x) ->
                        {
                            Settings.setMuted(!Settings.isMuted());
                        });
                    }
                });
                menu.add(new JMenu("Speed")
                {
                    {
                        ButtonGroup group = new ButtonGroup();
                        for (final EmulateSpeed speed : EmulateSpeed.values())
                        {
                            add(new JRadioButtonMenuItem(speed.name)
                            {
                                {
                                    group.add(this);
                                    if (core.clockSpeed == speed.clockSpeed)
                                        group.setSelected(getModel(), true);
                                    addActionListener(e -> {
                                        Settings.setSpeed(speed);
                                        group.setSelected(getModel(), true);
                                    });
                                }
                            });
                        }
                    }
                });

                menu.add(new JMenu("Volume")
                {
                    {
                        add(new JSlider(0, 100, Settings.getVolume())
                        {
                            {
                                setLabelTable(createStandardLabels(50));
                                setSnapToTicks(false);
                                setFocusable(false);
                                setPaintTicks(true);
                                setPaintLabels(true);
                                setPaintTrack(true);

                                addChangeListener((e) -> {
                                    Settings.setVolume(getValue());
                                });

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

                menu.add(new JCheckBoxMenuItem("Fullscreen", Settings.isFullScreen())
                {
                    {
                        addActionListener((e) -> {
                            boolean wasPaused = core.isPaused();
                            core.setPaused(true);
                            disp.setVisible(false);

                            Settings.setFullScreen(!Settings.isFullScreen());
                            initUI(core, Settings.isFullScreen(), Settings.getMagnification());
                            core.setPaused(wasPaused);

                            // I'm not sure why this has to be invoked later, but if it's not, stuff breaks
                            SwingUtilities.invokeLater(() -> {
                                core.lcd.currentRenderer = null;
                                core.lcd.initializeRenderers();
                            });
                        });
                    }
                });

                menu.add(new JMenu("Magnification")
                {
                    {
                        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

                        for (int mag = 1; mag * R.W < screen.width && mag * R.H < screen.height; mag++)
                        {
                            final int magnification = mag;
                            add(new JCheckBoxMenuItem(mag + "x", mag == display.magnification)
                            {
                                {
                                    addActionListener((e) -> {
                                        boolean wasPaused = core.isPaused();
                                        core.setPaused(true);
                                        disp.setVisible(false);

                                        Settings.setMagnification(magnification);
                                        initUI(core, Settings.isFullScreen(), magnification);
                                        core.setPaused(wasPaused);

                                        // I'm not sure why this has to be invoked later, but if it's not, stuff breaks
                                        SwingUtilities.invokeLater(() -> {
                                            core.lcd.currentRenderer = null;
                                            core.lcd.initializeRenderers();
                                        });
                                    });
                                }
                            });
                        }
                    }
                });

                menu.add(new JSeparator());
                menu.add(new JMenuItem("Keybindings...")
                {
                    {
                        addActionListener((e) -> {
                            new JDialog(disp, "Keybindings...")
                            {
                                {
                                    add(new KeybindingSelectionPanel(Settings.keys));
                                    pack();
                                    setResizable(false);
                                    setLocationRelativeTo(disp);
                                    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                                }
                            }.setVisible(true);
                        });
                    }
                });

                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        Thread codeExecutionThread = new Thread(core::exec);

        SwingUtilities.invokeLater(() -> {
            if (!fullscreen)
                disp.setContentPane(display);
            else
                disp.setContentPane(new Panel()
                {
                    {
                        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
                        add(Box.createVerticalGlue());
                        setBackground(Color.BLACK);
                        setIgnoreRepaint(true);

                        add(new Panel()
                        {
                            {
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


            if (fullscreen)
            {
                disp.setUndecorated(true);
                disp.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
            disp.pack();
            disp.setResizable(false);
            disp.setLocationRelativeTo(null);
            disp.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            disp.addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowClosing(WindowEvent evt)
                {
                    System.err.println(core.cycle);
                    try
                    {
                        FileOutputStream f = new FileOutputStream(core.savefile);
                        if (core.mmu.hasBattery())
                        {
                            System.err.println("Saving cart ram");
                            core.mmu.save(f);
                        }
                        f.close();
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            });

            disp.setVisible(true);
            display.requestFocus();

            core.lcd.initializeRenderers();

            codeExecutionThread.start();
        });

        System.err.println(core.cartridge.gameTitle);
        System.err.println(core.cartridge);
        System.out.flush();
    }
}
