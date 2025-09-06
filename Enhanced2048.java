import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Stack;
import java.util.prefs.Preferences;

public class Enhanced2048{
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EntryWindow());
    }
    // -------- Entry Window with Image, Music, and Centered Start Button --------
    static class EntryWindow extends JFrame {
        Clip introMusic;
        public EntryWindow() {
            setTitle("2048 Cubes.io");
            setUndecorated(true);
            setResizable(false);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setLayout(new BorderLayout());

            // Custom panel with image background
            JPanel imagePanel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    try {
                        Image img = new ImageIcon("entry_image.png").getImage();
                        g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
                    } catch (Exception e) {
                        setBackground(Color.CYAN);
                    }
                }
            };
            imagePanel.setLayout(new GridBagLayout());
            JButton startButton = new JButton("Start Game");
            startButton.setFont(new Font("Comic Sans MS", Font.BOLD, 36));
            startButton.setBackground(new Color(0xF7CA18)); // Yellow style
            startButton.setForeground(Color.WHITE);
            startButton.setFocusPainted(false);
            startButton.setBorder(BorderFactory.createEmptyBorder(20, 80, 20, 80));
            // Center button using GridBag
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.CENTER;
            imagePanel.add(startButton, gbc);
            add(imagePanel, BorderLayout.CENTER);

            pack();
            setVisible(true);

            // Play intro music (replace path if needed)
            try {
                introMusic = playMusic("intro.wav", true);
            } catch (Exception ex) {
                introMusic = null;
            }

            startButton.addActionListener(e -> {
                if (introMusic != null) introMusic.stop();
                dispose();
                new GameWindow();
            });
        }
    }

    // --------- Main Full-Screen Game Window with Color, Sounds, Styling --------
    static class GameWindow extends JFrame {
        private static final int SIZE = 4;
        private int[][] grid = new int[SIZE][SIZE];
        private JLabel[][] gridLabels = new JLabel[SIZE][SIZE];
        private JLabel scoreLabel, bestScoreLabel;
        private int score = 0, bestScore = 0;
        private boolean winReached = false;
        private Stack<int[][]> undoGridStack = new Stack<>();
        private Stack<Integer> undoScoreStack = new Stack<>();
        private Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
        private Clip moveSound, bgMusic;

        public GameWindow() {
            setTitle("2048 Cubes.io");
            setUndecorated(true);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            gd.setFullScreenWindow(this);

            bestScore = prefs.getInt("bestScore", 0);
            setLayout(new BorderLayout(8, 8));
            getContentPane().setBackground(new Color(0x238BD3));

            // Top panel: Score
            JPanel topPanel = new JPanel(new GridLayout(1, 2, 16, 10));
            topPanel.setBackground(getContentPane().getBackground());
            scoreLabel = styledLabel("Score: 0", 34, Color.WHITE, Color.DARK_GRAY);
            bestScoreLabel = styledLabel("Best: " + bestScore, 34, Color.WHITE, Color.DARK_GRAY);
            topPanel.add(scoreLabel); topPanel.add(bestScoreLabel);
            add(topPanel, BorderLayout.NORTH);

            // Center: Game grid
            JPanel gridPanel = new JPanel(new GridLayout(SIZE, SIZE, 16, 16));
            gridPanel.setBackground(new Color(0x238BD3));
            for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) {
                gridLabels[i][j] = styledLabel("", 54, Color.BLACK, new Color(0xcdc1b4));
                gridLabels[i][j].setOpaque(true);
                gridLabels[i][j].setHorizontalAlignment(SwingConstants.CENTER);
                gridLabels[i][j].setBorder(BorderFactory.createLineBorder(Color.WHITE, 5));
                gridPanel.add(gridLabels[i][j]);
            }
            add(gridPanel, BorderLayout.CENTER);

            // Bottom panel: Controls
            JPanel controls = new JPanel();
            controls.setBackground(getContentPane().getBackground());
            JButton undo = styledButton("Undo", 28); 
            JButton restart = styledButton("Restart", 28); 
            JButton exit = styledButton("Exit", 28);
            controls.add(undo); controls.add(restart); controls.add(exit);
            add(controls, BorderLayout.SOUTH);

            undo.addActionListener(e -> undo());
            restart.addActionListener(e -> restart());
            exit.addActionListener(e -> exitGame());

            // Key listener for arrow keys
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (winReached) return;
                    int key = e.getKeyCode();
                    boolean moved = false;
                    switch (key) {
                        case KeyEvent.VK_UP: moved = moveUp(); break;
                        case KeyEvent.VK_DOWN: moved = moveDown(); break;
                        case KeyEvent.VK_LEFT: moved = moveLeft(); break;
                        case KeyEvent.VK_RIGHT: moved = moveRight(); break;
                        case KeyEvent.VK_ESCAPE: exitGame();
                    }
                    if (moved) {
                        playSound(moveSound);
                        addNewTile();
                        updateGrid();
                        updateScore();
                        checkGameStatus();
                    }
                }
            });
            setFocusable(true);

            // Load sounds/music
            moveSound = loadSound("move.wav");
            bgMusic = loadSound("background.wav");
            if(bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY);

            // Setup and show
            initGame();
            setVisible(true);
        }

        private JLabel styledLabel(String txt, int size, Color fg, Color bg) {
            JLabel l = new JLabel(txt, JLabel.CENTER);
            l.setFont(new Font("Arial", Font.BOLD, size));
            l.setOpaque(true);
            l.setForeground(fg); l.setBackground(bg);
            return l;
        }
        private JButton styledButton(String txt, int size) {
            JButton b = new JButton(txt);
            b.setFont(new Font("Comic Sans MS", Font.BOLD, size));
            b.setFocusPainted(false);
            b.setBackground(new Color(0xC0392B));
            b.setForeground(Color.WHITE);
            return b;
        }
        private void initGame() {
            score = 0; winReached = false;
            undoGridStack.clear(); undoScoreStack.clear();
            for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) grid[i][j] = 0;
            addNewTile(); addNewTile();
            updateGrid(); updateScore();
        }
        private void updateGrid() {
            for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) {
                int val = grid[i][j];
                gridLabels[i][j].setText(val == 0 ? "" : String.valueOf(val));
                gridLabels[i][j].setBackground(getBoxColor(val));
                gridLabels[i][j].setForeground(val < 16 ? Color.DARK_GRAY : Color.WHITE);
            }
        }
        private void updateScore() {
            scoreLabel.setText("Score: " + score);
            if (score > bestScore) {
                bestScore = score;
                prefs.putInt("bestScore", bestScore);
            }
            bestScoreLabel.setText("Best: " + bestScore);
        }
        // --------- Movement, Undo, Game Logic ----------
        private void saveState() {
            int[][] copyGrid = new int[SIZE][SIZE];
            for (int i = 0; i < SIZE; i++) System.arraycopy(grid[i], 0, copyGrid[i], 0, SIZE);
            undoGridStack.push(copyGrid); undoScoreStack.push(score);
        }
        private void undo() {
            if (!undoGridStack.isEmpty() && !undoScoreStack.isEmpty()) {
                grid = undoGridStack.pop(); score = undoScoreStack.pop();
                winReached = false; updateGrid(); updateScore();
            } else {
                infoMessage("No move to undo!", "Undo");
            }
        }
        private void restart() {
            int option = confirmMessage("Restart the game?", "Restart");
            if(option == JOptionPane.YES_OPTION) initGame();
        }
        private void exitGame() {
            int option = confirmMessage("Are you sure you want to exit?", "Exit");
            if(option == JOptionPane.YES_OPTION) { if(bgMusic!=null) bgMusic.stop(); System.exit(0);}
        }
        private boolean addNewTile() {
            if (isGridFull()) return false;
            Random r = new Random(); int x, y;
            do { x = r.nextInt(SIZE); y = r.nextInt(SIZE); } while (grid[x][y] != 0);
            grid[x][y] = r.nextInt(10) == 0 ? 4 : 2;
            return true;
        }
        private boolean isGridFull() {
            for (int[] row : grid) for (int val : row) if (val == 0) return false;
            return true;
        }
        private boolean moveUp() { return moveTiles(0,-1); }
        private boolean moveDown() { return moveTiles(0,1); }
        private boolean moveLeft() { return moveTiles(-1,0); }
        private boolean moveRight() { return moveTiles(1,0); }
        // Main move logic with dx, dy
        private boolean moveTiles(int dx, int dy) {
            saveState();
            boolean moved = false;
            for(int i=0; i<SIZE; i++) for(int j=0; j<SIZE; j++) {
                int x = dx==0?j:i, y = dy==0?j:i;
                if(grid[x][y]!=0) {
                    int nx = x, ny = y;
                    while(true) {
                        int tx = nx+dx, ty = ny+dy;
                        if(tx<0||tx>=SIZE||ty<0||ty>=SIZE) break;
                        if(grid[tx][ty]==0) {
                            grid[tx][ty]=grid[nx][ny]; grid[nx][ny]=0;
                            nx=tx; ny=ty; moved=true;
                        } else if(grid[tx][ty]==grid[nx][ny]) {
                            grid[tx][ty]*=2; score+=grid[tx][ty]; grid[nx][ny]=0;
                            if(grid[tx][ty]==2048) winReached=true; moved=true; break;
                        } else break;
                    }
                }
            }
            return moved;
        }
        private void checkGameStatus() {
            if(winReached) {
                if(bgMusic!=null) bgMusic.stop();
                new StyledWinPage(score, this);
                winReached=false;
            } else if(isGameOver()) {
                if(bgMusic!=null) bgMusic.stop();
                new StyledLosePage(score, this);
            }
        }
        private boolean isGameOver() {
            if (!isGridFull()) return false;
            for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE-1; j++)
                if (grid[i][j]==grid[i][j+1]||grid[j][i]==grid[j+1][i]) return false;
            return true;
        }
        // --------- Utility Styling + Audio Helpers ----------
        private Color getBoxColor(int value) {
            switch(value) {
                case 0: return new Color(0xcdc1b4);
                case 2: return new Color(0xeee4da);
                case 4: return new Color(0xede0c8);
                case 8: return new Color(0xf2b179);
                case 16: return new Color(0xf59563);
                case 32: return new Color(0xf67c5f);
                case 64: return new Color(0xf65e3b);
                case 128: return new Color(0xedcf72);
                case 256: return new Color(0xedcc61);
                case 512: return new Color(0xedc850);
                case 1024:return new Color(0xedc53f);
                case 2048:return new Color(0xedc22e);
                default: return new Color(0x3c3a32);
            }
        }
        private void infoMessage(String msg, String title) {
            JOptionPane.showMessageDialog(this, msg, title, JOptionPane.INFORMATION_MESSAGE);
        }
        private int confirmMessage(String msg, String title) {
            return JOptionPane.showConfirmDialog(this, msg, title, JOptionPane.YES_NO_OPTION);
        }
    }
    // --------- Win/Lose Fullscreen Pages Built with Styling and Music ---------
    static class StyledWinPage extends JFrame {
        public StyledWinPage(int score, GameWindow parent) {
            super("You Won!");
            buildScreen(score, parent, "You reached 2048!", true);
        }
    }
    static class StyledLosePage extends JFrame {
        public StyledLosePage(int score, GameWindow parent) {
            super("Game Over");
            buildScreen(score, parent, "No more moves!", false);
        }
        private void buildScreen(int score, GameWindow parent, String msg, boolean win) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            getContentPane().setBackground(win ? new Color(0x1abc9c) : new Color(0xe74c3c));
            setLayout(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx=0; gc.anchor=GridBagConstraints.CENTER; gc.insets=new Insets(20,0,20,0);

            JLabel congrats = new JLabel(win ? "Congratulations!" : "Game Over");
            congrats.setFont(new Font("Comic Sans MS", Font.BOLD, 80));
            congrats.setForeground(Color.WHITE);
            gc.gridy=0; add(congrats,gc);

            JLabel msgLabel = new JLabel(msg + " Your score: " + score);
            msgLabel.setFont(new Font("Arial", Font.PLAIN, 48));
            msgLabel.setForeground(Color.WHITE);
            gc.gridy=1; add(msgLabel,gc);

            JPanel btns = new JPanel();
            btns.setOpaque(false);
            JButton replay = new JButton("Play Again"), exit = new JButton("Exit");
            replay.setFont(new Font("Arial", Font.BOLD, 32));
            exit.setFont(new Font("Arial", Font.BOLD, 32));
            btns.add(replay); btns.add(exit);
            gc.gridy=2; add(btns,gc);

            replay.addActionListener(e -> { dispose(); parent.initGame(); parent.setVisible(true); });
            exit.addActionListener(e -> System.exit(0));
            setVisible(true);

            // Play special SFX for win/lose if wanted
        }
    }
    // --------- Audio Helper Methods -----------
    private static Clip playMusic(String filename, boolean loop) throws Exception {
        Clip c = loadSound(filename);
        if (c != null) {
            c.start();
            if(loop) c.loop(Clip.LOOP_CONTINUOUSLY);
        }
        return c;
    }
    private static Clip loadSound(String filename) {
        try {
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(new File(filename));
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            return clip;
        } catch(Exception e) { return null; }
    }
    private static void playSound(Clip clip) {
        if(clip != null) {
            if(clip.isRunning()) clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }
}
