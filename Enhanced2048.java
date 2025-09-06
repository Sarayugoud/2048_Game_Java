import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Random;
import java.util.Stack;
import java.util.prefs.Preferences;
import javax.sound.sampled.*;
import javax.swing.*;

public class Enhanced2048 {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EntryWindow());
    }

    // Entry Page with background image, music, best score, Start and Reset buttons
    static class EntryWindow extends JFrame {
        private Clip introMusic;
        private JLabel bestScoreLabel;
        private Preferences prefs = Preferences.userRoot().node(this.getClass().getName());

        public EntryWindow() {
            setTitle("2048 Cubes.io - Entry");
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setLayout(new BorderLayout());

            // Background panel paints image
            JPanel backgroundPanel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Image img = new ImageIcon("entry_image.png").getImage();
                    g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
                }
            };
            backgroundPanel.setLayout(new GridBagLayout());

            // Best score from preferences
            int bestScore = prefs.getInt("bestScore", 0);
            bestScoreLabel = new JLabel("Best Score: " + bestScore);
            bestScoreLabel.setFont(new Font("Arial Rounded MT Bold", Font.BOLD, 40));
            bestScoreLabel.setForeground(Color.WHITE);

            JButton startButton = new JButton("Start Game");
            startButton.setFont(new Font("Comic Sans MS", Font.BOLD, 48));
            startButton.setBackground(new Color(0x1abc9c));
            startButton.setForeground(Color.WHITE);
            startButton.setFocusPainted(false);
            startButton.setPreferredSize(new Dimension(280, 80));

            JButton resetBestButton = new JButton("Reset Best Score");
            resetBestButton.setFont(new Font("Comic Sans MS", Font.BOLD, 30));
            resetBestButton.setBackground(new Color(0xe74c3c));
            resetBestButton.setForeground(Color.WHITE);
            resetBestButton.setFocusPainted(false);
            resetBestButton.setPreferredSize(new Dimension(280, 60));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(20, 20, 20, 20);
            gbc.gridx = 0; gbc.gridy = 0; backgroundPanel.add(bestScoreLabel, gbc);
            gbc.gridy = 1; backgroundPanel.add(startButton, gbc);
            gbc.gridy = 2; backgroundPanel.add(resetBestButton, gbc);

            add(backgroundPanel, BorderLayout.CENTER);

            try {
                introMusic = playMusic("into_music.wav", true);
                if (introMusic == null) System.out.println("Intro music failed to load.");
            } catch (Exception ex) {
                ex.printStackTrace();
                introMusic = null;
            }

            startButton.addActionListener(e -> {
                if (introMusic != null) introMusic.stop();
                dispose();
                new GameWindow(prefs, bestScoreLabel);
            });

            resetBestButton.addActionListener(e -> {
                prefs.putInt("bestScore", 0);
                bestScoreLabel.setText("Best Score: 0");
            });

            setVisible(true);
        }
    }

    // Game Window with 4x4 grid, scoring, sounds, and gameplay
    static class GameWindow extends JFrame {
        private static final int SIZE = 4;
        private int[][] grid = new int[SIZE][SIZE];
        private JLabel[][] gridLabels = new JLabel[SIZE][SIZE];
        private JLabel scoreLabel, bestScoreLabel;
        private int score = 0, bestScore;
        private boolean winReached = false;
        private Stack<int[][]> undoGridStack = new Stack<>();
        private Stack<Integer> undoScoreStack = new Stack<>();
        private Preferences prefs;
        private Clip moveSound, bgMusic;
        private JLabel externalBestScoreLabel; // To update on Entry page

        public GameWindow(Preferences prefs, JLabel bestScoreLabel) {
            this.prefs = prefs;
            this.externalBestScoreLabel = bestScoreLabel;
            this.bestScore = prefs.getInt("bestScore", 0);

            setTitle("2048 Cubes.io - Game");
            setUndecorated(true);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            gd.setFullScreenWindow(this);

            setLayout(new BorderLayout(8, 8));
            getContentPane().setBackground(new Color(0x238BD3));

            // Top panel: score and best score
            JPanel topPanel = new JPanel(new GridLayout(1, 2, 20, 10));
            topPanel.setBackground(getContentPane().getBackground());
            scoreLabel = styledLabel("Score: 0", 32, Color.WHITE, Color.DARK_GRAY);
            bestScoreLabel = styledLabel("Best: " + bestScore, 32, Color.WHITE, Color.DARK_GRAY);
            topPanel.add(scoreLabel);
            topPanel.add(bestScoreLabel);
            add(topPanel, BorderLayout.NORTH);

            // Center: grid panel
            JPanel gridPanel = new JPanel(new GridLayout(SIZE, SIZE, 15, 15));
            gridPanel.setBackground(new Color(0x238BD3));
            for(int i=0; i<SIZE; i++) {
                for(int j=0; j<SIZE; j++) {
                    gridLabels[i][j] = styledLabel("", 48, Color.BLACK, new Color(0xcdc1b4));
                    gridLabels[i][j].setOpaque(true);
                    gridLabels[i][j].setHorizontalAlignment(SwingConstants.CENTER);
                    gridLabels[i][j].setBorder(BorderFactory.createLineBorder(Color.WHITE, 5));
                    gridPanel.add(gridLabels[i][j]);
                }
            }
            add(gridPanel, BorderLayout.CENTER);

            // Bottom panel with Undo, Restart, Exit
            JPanel bottomPanel = new JPanel();
            bottomPanel.setBackground(getContentPane().getBackground());
            JButton undoButton = styledButton("Undo", 28);
            JButton restartButton = styledButton("Restart", 28);
            JButton exitButton = styledButton("Exit", 28);
            bottomPanel.add(undoButton);
            bottomPanel.add(restartButton);
            bottomPanel.add(exitButton);
            add(bottomPanel, BorderLayout.SOUTH);

            undoButton.addActionListener(e -> undo());
            restartButton.addActionListener(e -> {
                int res = JOptionPane.showConfirmDialog(this, "Restart game?", "Confirm Restart", JOptionPane.YES_NO_OPTION);
                if(res == JOptionPane.YES_OPTION) initGame();
            });
            exitButton.addActionListener(e -> {
                int res = JOptionPane.showConfirmDialog(this, "Exit game?", "Confirm Exit", JOptionPane.YES_NO_OPTION);
                if(res == JOptionPane.YES_OPTION){
                    if(bgMusic != null) bgMusic.stop();
                    System.exit(0);
                }
            });

            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if(winReached) return;
                    boolean moved = false;
                    switch(e.getKeyCode()) {
                        case KeyEvent.VK_UP: moved = moveUp(); break;
                        case KeyEvent.VK_DOWN: moved = moveDown(); break;
                        case KeyEvent.VK_LEFT: moved = moveLeft(); break;
                        case KeyEvent.VK_RIGHT: moved = moveRight(); break;
                    }
                    if(moved) {
                        playSound(moveSound);
                        addNewTile();
                        updateGrid();
                        updateScore();
                        checkGameStatus();
                    }
                }
            });

            setFocusable(true);

            moveSound = loadSound("move_sound.wav");
            bgMusic = loadSound("background_music.wav");
            if(bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY);

            initGame();
            setVisible(true);
        }

        private JLabel styledLabel(String text, int size, Color fg, Color bg) {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setFont(new Font("Arial", Font.BOLD, size));
            label.setOpaque(true);
            label.setForeground(fg);
            label.setBackground(bg);
            return label;
        }

        private JButton styledButton(String text, int size) {
            JButton button = new JButton(text);
            button.setFont(new Font("Comic Sans MS", Font.BOLD, size));
            button.setFocusable(false);
            button.setBackground(new Color(0xC0392B));
            button.setForeground(Color.WHITE);
            return button;
        }

        private void initGame() {
            score = 0;
            winReached = false;
            undoGridStack.clear();
            undoScoreStack.clear();

            for(int i=0; i<SIZE; i++) for(int j=0; j<SIZE; j++) grid[i][j] = 0;
            addNewTile();
            addNewTile();

            updateGrid();
            updateScore();
        }

        private void updateGrid() {
            for(int i=0; i<SIZE; i++) {
                for(int j=0; j<SIZE; j++) {
                    int val = grid[i][j];
                    gridLabels[i][j].setText(val == 0 ? "" : String.valueOf(val));
                    gridLabels[i][j].setBackground(getBoxColor(val));
                    gridLabels[i][j].setForeground(val < 16 ? Color.DARK_GRAY : Color.WHITE);
                }
            }
        }

        private void updateScore() {
            scoreLabel.setText("Score: " + score);
            if(score > bestScore) {
                bestScore = score;
                prefs.putInt("bestScore", bestScore);
                bestScoreLabel.setText("Best: " + bestScore);
                externalBestScoreLabel.setText("Best Score: " + bestScore);
            }
        }

        // Tracks previous states for Undo
        private void saveState() {
            int[][] copy = new int[SIZE][SIZE];
            for(int i=0; i<SIZE; i++) System.arraycopy(grid[i], 0, copy[i], 0, SIZE);
            undoGridStack.push(copy);
            undoScoreStack.push(score);
        }

        private void undo() {
            if(!undoGridStack.isEmpty() && !undoScoreStack.isEmpty()) {
                grid = undoGridStack.pop();
                score = undoScoreStack.pop();
                winReached = false;
                updateGrid();
                updateScore();
            } else {
                JOptionPane.showMessageDialog(this, "No moves to undo.", "Undo", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        private boolean addNewTile() {
            if(isGridFull()) return false;
            Random rnd = new Random();
            int x, y;
            do {
                x = rnd.nextInt(SIZE);
                y = rnd.nextInt(SIZE);
            } while(grid[x][y] != 0);
            grid[x][y] = rnd.nextInt(10) == 0 ? 4 : 2;
            return true;
        }

        private boolean isGridFull() {
            for(int[] row : grid) for(int val : row) if(val == 0) return false;
            return true;
        }

        // Movement methods, with iteration order adapted for direction
        private boolean moveUp() {
            saveState();
            boolean moved = false;
            for(int col=0; col < SIZE; col++) {
                for(int row=1; row < SIZE; row++) {
                    if(grid[row][col] != 0) {
                        int r = row;
                        while(r > 0 && grid[r-1][col] == 0) {
                            grid[r-1][col] = grid[r][col];
                            grid[r][col] = 0;
                            r--;
                            moved = true;
                        }
                        if(r > 0 && grid[r-1][col] == grid[r][col]) {
                            grid[r-1][col] *= 2;
                            score += grid[r-1][col];
                            grid[r][col] = 0;
                            moved = true;
                            if(grid[r-1][col] == 2048) winReached = true;
                        }
                    }
                }
            }
            return moved;
        }

        private boolean moveDown() {
            saveState();
            boolean moved = false;
            for(int col=0; col < SIZE; col++) {
                for(int row=SIZE-2; row >= 0; row--) {
                    if(grid[row][col] != 0) {
                        int r = row;
                        while(r < SIZE-1 && grid[r+1][col] == 0) {
                            grid[r+1][col] = grid[r][col];
                            grid[r][col] = 0;
                            r++;
                            moved = true;
                        }
                        if(r < SIZE-1 && grid[r+1][col] == grid[r][col]) {
                            grid[r+1][col] *= 2;
                            score += grid[r+1][col];
                            grid[r][col] = 0;
                            moved = true;
                            if(grid[r+1][col] == 2048) winReached = true;
                        }
                    }
                }
            }
            return moved;
        }

        private boolean moveLeft() {
            saveState();
            boolean moved = false;
            for(int row=0; row < SIZE; row++) {
                for(int col=1; col < SIZE; col++) {
                    if(grid[row][col] != 0) {
                        int c = col;
                        while(c > 0 && grid[row][c-1] == 0) {
                            grid[row][c-1] = grid[row][c];
                            grid[row][c] = 0;
                            c--;
                            moved = true;
                        }
                        if(c > 0 && grid[row][c-1] == grid[row][c]) {
                            grid[row][c-1] *= 2;
                            score += grid[row][c-1];
                            grid[row][c] = 0;
                            moved = true;
                            if(grid[row][c-1] == 2048) winReached = true;
                        }
                    }
                }
            }
            return moved;
        }

        private boolean moveRight() {
            saveState();
            boolean moved = false;
            for(int row=0; row < SIZE; row++) {
                for(int col=SIZE-2; col >= 0; col--) {
                    if(grid[row][col] != 0) {
                        int c = col;
                        while(c < SIZE-1 && grid[row][c+1] == 0) {
                            grid[row][c+1] = grid[row][c];
                            grid[row][c] = 0;
                            c++;
                            moved = true;
                        }
                        if(c < SIZE-1 && grid[row][c+1] == grid[row][c]) {
                            grid[row][c+1] *= 2;
                            score += grid[row][c+1];
                            grid[row][c] = 0;
                            moved = true;
                            if(grid[row][c+1] == 2048) winReached = true;
                        }
                    }
                }
            }
            return moved;
        }

        private void checkGameStatus() {
            if(winReached) {
                if(bgMusic != null) bgMusic.stop();
                new StyledWinPage(score, this, prefs, externalBestScoreLabel);
                winReached = false;
            } else if(isGameOver()) {
                if(bgMusic != null) bgMusic.stop();
                new StyledLosePage(score, this, prefs, externalBestScoreLabel);
            }
        }

        private boolean isGameOver() {
            if(!isGridFull()) return false;
            for(int row=0; row < SIZE; row++) {
                for(int col=0; col < SIZE-1; col++) {
                    if(grid[row][col] == grid[row][col+1]) return false;
                }
            }
            for(int col=0; col < SIZE; col++) {
                for(int row=0; row < SIZE-1; row++) {
                    if(grid[row][col] == grid[row+1][col]) return false;
                }
            }
            return true;
        }

        private Color getBoxColor(int value) {
            switch(value) {
                case 0: return new Color(0xE8CF15);
                case 2: return new Color(0x1699F8);
                case 4: return new Color(0x683085);
                case 8: return new Color(0xE51C0B);
                case 16: return new Color(0x7D2910);
                case 32: return new Color(0x107D29);
                case 64: return new Color(0x066612);
                case 128: return new Color(0x8779C7);
                case 256: return new Color(0xedcc61);
                case 512: return new Color(0xedc850);
                case 1024: return new Color(0xedc53f);
                case 2048: return new Color(0xedc22e);
                default: return new Color(0x3c3a32);
            }
        }
    }

    static class StyledWinPage extends JFrame {
        Clip winMusic;
        Preferences prefs;
        JLabel externalBestScoreLabel;
        GameWindow parent;

        public StyledWinPage(int score, GameWindow parent, Preferences prefs, JLabel externalBestScoreLabel) {
            super("You Won!");
            this.parent = parent;
            this.prefs = prefs;
            this.externalBestScoreLabel = externalBestScoreLabel;

            if(parent.bgMusic != null) parent.bgMusic.stop();

            try {
                winMusic = loadSound("win.wav");
                if(winMusic != null) winMusic.loop(Clip.LOOP_CONTINUOUSLY);
            } catch(Exception e) {
                winMusic = null;
            }

            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JLabel background = new JLabel(new ImageIcon("win_image.png"));
            setContentPane(background);
            background.setLayout(new GridBagLayout());

            JPanel overlay = new JPanel(new GridBagLayout());
            overlay.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.anchor = GridBagConstraints.CENTER;
            gc.insets = new Insets(20, 0, 20, 0);

            JLabel congrats = new JLabel("Congratulations!");
            congrats.setFont(new Font("Comic Sans MS", Font.BOLD, 80));
            congrats.setForeground(Color.WHITE);
            gc.gridy = 0;
            overlay.add(congrats, gc);

            JLabel msgLabel = new JLabel("You reached 2048! Your score: " + score);
            msgLabel.setFont(new Font("Arial", Font.PLAIN, 48));
            msgLabel.setForeground(Color.WHITE);
            gc.gridy = 1;
            overlay.add(msgLabel, gc);

            JPanel btns = new JPanel();
            btns.setOpaque(false);
            JButton replay = new JButton("Play Again");
            JButton exit = new JButton("Exit");
            replay.setFont(new Font("Arial", Font.BOLD, 32));
            exit.setFont(new Font("Arial", Font.BOLD, 32));
            btns.add(replay);
            btns.add(exit);
            gc.gridy = 2;
            overlay.add(btns, gc);

            background.add(overlay);

            replay.addActionListener(e -> {
                if(winMusic != null) winMusic.stop();
                dispose();
                parent.initGame();
                parent.setVisible(true);
                updateExternalBestScore();
            });
            exit.addActionListener(e -> {
                if(winMusic != null) winMusic.stop();
                System.exit(0);
            });

            setVisible(true);
        }

        private void updateExternalBestScore() {
            int best = prefs.getInt("bestScore", 0);
            externalBestScoreLabel.setText("Best Score: " + best);
        }
    }

    static class StyledLosePage extends JFrame {
        Clip loseMusic;
        Preferences prefs;
        JLabel externalBestScoreLabel;
        GameWindow parent;

        public StyledLosePage(int score, GameWindow parent, Preferences prefs, JLabel externalBestScoreLabel) {
            super("Game Over");
            this.parent = parent;
            this.prefs = prefs;
            this.externalBestScoreLabel = externalBestScoreLabel;

            if(parent.bgMusic != null) parent.bgMusic.stop();

            try {
                loseMusic = loadSound("lost_music.wav");
                if(loseMusic != null) loseMusic.loop(Clip.LOOP_CONTINUOUSLY);
            } catch(Exception e) {
                loseMusic = null;
            }

            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JLabel background = new JLabel(new ImageIcon("lost_image.jpg"));
            setContentPane(background);
            background.setLayout(new GridBagLayout());

            JPanel overlay = new JPanel(new GridBagLayout());
            overlay.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx=0; gc.anchor=GridBagConstraints.CENTER; gc.insets=new Insets(20, 0, 20, 0);

            JLabel gameOver = new JLabel("Game Over");
            gameOver.setFont(new Font("Comic Sans MS", Font.BOLD, 80));
            gameOver.setForeground(Color.WHITE);
            gc.gridy=0; overlay.add(gameOver, gc);

            JLabel msgLabel = new JLabel("No more moves! Your score: " + score);
            msgLabel.setFont(new Font("Arial", Font.PLAIN, 48));
            msgLabel.setForeground(Color.WHITE);
            gc.gridy=1; overlay.add(msgLabel, gc);

            JPanel btns = new JPanel();
            btns.setOpaque(false);
            JButton replay = new JButton("Play Again");
            JButton exit = new JButton("Exit");
            replay.setFont(new Font("Arial", Font.BOLD, 32));
            exit.setFont(new Font("Arial", Font.BOLD, 32));
            btns.add(replay);
            btns.add(exit);
            gc.gridy=2; overlay.add(btns, gc);

            background.add(overlay);

            replay.addActionListener(e -> {
                if(loseMusic != null) loseMusic.stop();
                dispose();
                parent.initGame();
                parent.setVisible(true);
                updateExternalBestScore();
            });
            exit.addActionListener(e -> {
                if(loseMusic != null) loseMusic.stop();
                System.exit(0);
            });

            setVisible(true);
        }

        private void updateExternalBestScore() {
            int best = prefs.getInt("bestScore", 0);
            externalBestScoreLabel.setText("Best Score: " + best);
        }
    }

    // Audio helper methods

    private static Clip playMusic(String filename, boolean loop) throws Exception {
        Clip clip = loadSound(filename);
        if(clip != null) {
            clip.start();
            if(loop) clip.loop(Clip.LOOP_CONTINUOUSLY);
        }
        return clip;
    }

    private static Clip loadSound(String filename) {
        try {
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(new File(filename));
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            return clip;
        } catch (Exception e) {
            System.err.println("Could not load audio file: " + filename);
            return null;
        }
    }

    private static void playSound(Clip clip) {
        if(clip != null) {
            if(clip.isRunning()) clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }
}




