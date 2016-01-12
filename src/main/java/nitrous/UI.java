package nitrous;

import nitrous.lcd.Interpolator;
import nitrous.renderer.IRenderManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class UI
{
    public static final BufferedImage screenBuffer = new BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB);
    public static Panel display;
    //public static VRAMViewer debugger;

    public static void main(String[] argv) throws IOException, ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException
    {
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        System.setProperty("sun.java2d.opengl", "false");
//        System.setProperty("sun.java2d.xrender", "false");
        System.setProperty("sun.java2d.d3d", "true");

//        JFrame x = new JFrame("AAA");
//        x.add(new JButton("Cliq me") {
//            {
//                setPreferredSize(new Dimension(100, 100));
//            }
//        });
//        x.pack();
//        x.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        x.setLocationRelativeTo(null);
//        x.setVisible(true);

//        if(true) return;

        //    System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream("emu.log"))));

        File f = new File(argv[0]);
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        // TODO fix
        in.read(buf);
        in.close();


        Cartridge cartridge = new Cartridge(buf);

        Emulator core = new Emulator(cartridge);

        //    debugger = new VRAMViewer(core);

        File savefile = new File(cartridge.gameTitle + ".sav");

        if (core.mmu.hasBattery())
        {
            try
            {
                core.mmu.load(new FileInputStream(savefile));
            } catch (Exception ignored)
            {

            }
        }

        Thread codeExecutionThread = new Thread(core::exec);

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(() -> {
            JFrame disp = new JFrame(cartridge.gameTitle);
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
                            add(display = new Panel()
                            {
                                {
                                    int mag = 2;
                                    setBackground(Color.BLACK);
                                    setMaximumSize(new Dimension(160 * mag, 144 * mag));
                                    setMinimumSize(new Dimension(160 * mag, 144 * mag));
                                    setSize(new Dimension(160 * mag, 144 * mag));
                                    setPreferredSize(new Dimension(160 * mag, 144 * mag));
//                    setBackground(Color.GREEN);
                                    KeyListener toggler = new KeyAdapter()
                                    {
                                        private void toggle(KeyEvent e, boolean to)
                                        {
                                            switch (e.getKeyCode())
                                            {
                                                case KeyEvent.VK_RIGHT:
                                                    core.buttonRight = to;
                                                    break;
                                                case KeyEvent.VK_LEFT:
                                                    core.buttonLeft = to;
                                                    break;
                                                case KeyEvent.VK_UP:
                                                    core.buttonUp = to;
                                                    break;
                                                case KeyEvent.VK_DOWN:
                                                    core.buttonDown = to;
                                                    break;
                                                case KeyEvent.VK_A:
                                                    core.buttonA = to;
                                                    break;
                                                case KeyEvent.VK_B:
                                                    core.buttonB = to;
                                                    break;
                                                case KeyEvent.VK_X:
                                                    core.buttonStart = to;
                                                    break;
                                                case KeyEvent.VK_Y:
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
                                    addMouseListener(new MouseAdapter()
                                    {
                                        @Override
                                        public void mouseReleased(MouseEvent e)
                                        {
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
                                                                if (interpolator == core.lcd.interpolator)
                                                                    group.setSelected(getModel(), true);
                                                                addActionListener((e) -> {
                                                                    core.lcd.interpolator = interpolator;
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
                                                        add(new JCheckBoxMenuItem("Channel " + i, core.sound.isChannelEnabled(channel))
                                                        {
                                                            {
                                                                addActionListener((x) ->
                                                                {
                                                                    core.sound.setChannelEnabled(channel, !core.sound.isChannelEnabled(channel));
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
                                            menu.add(new JCheckBoxMenuItem("Mute", core.sound.muted)
                                            {
                                                {
                                                    addActionListener((x) ->
                                                    {
                                                        core.sound.muted = !core.sound.muted;
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
                                                                    core.clockSpeed = speed.clockSpeed;
                                                                    core.sound.updateClockSpeed(speed.clockSpeed);
                                                                    group.setSelected(getModel(), true);
                                                                });
                                                            }
                                                        });
                                                    }
                                                }
                                            });

                                            menu.add(new JSeparator());
                                            menu.add(new JMenuItem("Options"));

                                            if (SwingUtilities.isRightMouseButton(e))
                                                menu.show(e.getComponent(), e.getX(), e.getY());
                                        }
                                    });
                                    addKeyListener(toggler);
                                    disp.addKeyListener(toggler);
                                    setIgnoreRepaint(true);
                                }

                                @Override
                                public void paint(Graphics g)
                                {
                                    throw new RuntimeException();
                                    //System.err.println("a");
                                    //super.paintComponent(g);
//                       ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                    //   g.drawImage(screenBuffer, 0, 0, getWidth(), getHeight(), null);
                                }
                            });
                            add(Box.createHorizontalGlue());
                        }
                    });

                    add(Box.createVerticalGlue());
                }
            });


            boolean fullscreen = false;
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
                        FileOutputStream f = new FileOutputStream(savefile);
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
//            disp.setIgnoreRepaint(true);


            core.lcd.initializeRenderers();
            //          core.lcd.currentRenderer.getGraphics().getDeviceConfiguration().getDevice().setFullScreenWindow(disp);

            codeExecutionThread.start();
        });

        System.err.println(cartridge.gameTitle);
        System.err.println(cartridge);
        System.out.flush();
    }
}
