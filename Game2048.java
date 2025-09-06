import java.awt.*;
import java.awt.event.*;
import java.util.Random;
import javax.swing.*;

public class Game2048 {
    private static final int SIZE = 4;
    private int[][] grid;
    private Random random;
    private int score;
    private int highScore;
    private JFrame frame;
    private JPanel gridPanel;
    private JLabel[][] gridLabels;
    private JLabel scoreLabel;
    private JLabel highScoreLabel;
    private boolean winConditionReached;

    public Game2048() {
        grid = new int[SIZE][SIZE];
        random = new Random();
        score = 0;
        highScore = 0;

        // Setup frame
        frame = new JFrame("TechVidvan's 2048 Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 450);
        frame.setLayout(new BorderLayout());

        // Grid panel
        gridPanel = new JPanel(new GridLayout(SIZE, SIZE));
        gridLabels = new JLabel[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                gridLabels[i][j] = new JLabel("", JLabel.CENTER);
                gridLabels[i][j].setFont(new Font("Arial", Font.BOLD, 24));
                gridLabels[i][j].setOpaque(true);
                gridLabels[i][j].setBackground(Color.LIGHT_GRAY);
                gridLabels[i][j].setBorder(BorderFactory.createLineBorder(Color.BLACK));
                gridPanel.add(gridLabels[i][j]);
            }
        }
        frame.add(gridPanel, BorderLayout.CENTER);

        // Info panel for score
        JPanel infoPanel = new JPanel(new GridLayout(1, 2));
        scoreLabel = new JLabel("Score: 0", JLabel.CENTER);
        highScoreLabel = new JLabel("High Score: 0", JLabel.CENTER);
        infoPanel.add(scoreLabel);
        infoPanel.add(highScoreLabel);
        frame.add(infoPanel, BorderLayout.NORTH);

        // Key listener for input
        frame.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                boolean moved = false;
                if (keyCode == KeyEvent.VK_UP) {
                    moved = moveUp();
                } else if (keyCode == KeyEvent.VK_DOWN) {
                    moved = moveDown();
                } else if (keyCode == KeyEvent.VK_LEFT) {
                    moved = moveLeft();
                } else if (keyCode == KeyEvent.VK_RIGHT) {
                    moved = moveRight();
                }
                if (moved) {
                    addNewNumber();
                    updateGridLabels();
                    updateScore();
                    if (isGameOver()) {
                        showGameOverMessage();
                    }
                }
            }
        });

        frame.setFocusable(true);
        frame.requestFocus();
        frame.setVisible(true);

        // Initialize the grid with two random numbers
        initializeGrid();
        updateGridLabels();
    }

    // Initialize grid values to 0 and add two numbers
    public void initializeGrid() {
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                grid[i][j] = 0;
        addNewNumber();
        addNewNumber();
        score = 0;
        winConditionReached = false;
        updateScore();
    }

    // Place a random 2 or 4 in an empty spot
    public void addNewNumber() {
        int row, col;
        if (isGridFull()) return;
        do {
            row = random.nextInt(SIZE);
            col = random.nextInt(SIZE);
        } while (grid[row][col] != 0);
        grid[row][col] = random.nextInt(2) == 0 ? 2 : 4;
    }

    // Update GUI labels to reflect grid state
    public void updateGridLabels() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int val = grid[i][j];
                if (val == 0) {
                    gridLabels[i][j].setText("");
                    gridLabels[i][j].setBackground(Color.LIGHT_GRAY);
                } else {
                    gridLabels[i][j].setText(String.valueOf(val));
                    gridLabels[i][j].setBackground(getTileColor(val));
                    if (val == 2048) winConditionReached = true;
                }
            }
        }
    }

    // Color code tiles based on value
    public Color getTileColor(int value) {
        switch (value) {
            case 2:    return new Color(238, 228, 218);
            case 4:    return new Color(237, 224, 200);
            case 8:    return new Color(242, 177, 121);
            case 16:   return new Color(245, 149, 99);
            case 32:   return new Color(246, 124, 95);
            case 64:   return new Color(246, 94, 59);
            case 128:  return new Color(237, 207, 114);
            case 256:  return new Color(237, 204, 97);
            case 512:  return new Color(237, 200, 80);
            case 1024: return new Color(237, 197, 63);
            case 2048: return new Color(237, 194, 46);
            default:   return Color.WHITE;
        }
    }

    // Check if the grid is full
    public boolean isGridFull() {
        for (int[] row : grid)
            for (int val : row)
                if (val == 0) return false;
        return true;
    }

    // Move tiles up, return true if movement happened
    public boolean moveUp() {
        boolean moved = false;
        for (int col = 0; col < SIZE; col++) {
            int mergePos = -1;
            for (int row = 1; row < SIZE; row++) {
                if (grid[row][col] != 0) {
                    int r = row;
                    while (r > 0 && (grid[r-1][col] == 0 || grid[r-1][col] == grid[r][col])) {
                        if (grid[r-1][col] == grid[r][col] && mergePos != r-1) {
                            grid[r-1][col] *= 2;
                            score += grid[r-1][col];
                            grid[r][col] = 0;
                            mergePos = r-1;
                            moved = true;
                        } else if (grid[r-1][col] == 0) {
                            grid[r-1][col] = grid[r][col];
                            grid[r][col] = 0;
                            moved = true;
                        }
                        r--;
                    }
                }
            }
        }
        return moved;
    }

    // Move tiles down
    public boolean moveDown() {
        boolean moved = false;
        for (int col = 0; col < SIZE; col++) {
            int mergePos = -1;
            for (int row = SIZE - 2; row >= 0; row--) {
                if (grid[row][col] != 0) {
                    int r = row;
                    while (r < SIZE - 1 && (grid[r+1][col] == 0 || grid[r+1][col] == grid[r][col])) {
                        if (grid[r+1][col] == grid[r][col] && mergePos != r+1) {
                            grid[r+1][col] *= 2;
                            score += grid[r+1][col];
                            grid[r][col] = 0;
                            mergePos = r+1;
                            moved = true;
                        } else if (grid[r+1][col] == 0) {
                            grid[r+1][col] = grid[r][col];
                            grid[r][col] = 0;
                            moved = true;
                        }
                        r++;
                    }
                }
            }
        }
        return moved;
    }

    // Move tiles left
    public boolean moveLeft() {
        boolean moved = false;
        for (int row = 0; row < SIZE; row++) {
            int mergePos = -1;
            for (int col = 1; col < SIZE; col++) {
                if (grid[row][col] != 0) {
                    int c = col;
                    while (c > 0 && (grid[row][c-1] == 0 || grid[row][c-1] == grid[row][c])) {
                        if (grid[row][c-1] == grid[row][c] && mergePos != c-1) {
                            grid[row][c-1] *= 2;
                            score += grid[row][c-1];
                            grid[row][c] = 0;
                            mergePos = c-1;
                            moved = true;
                        } else if (grid[row][c-1] == 0) {
                            grid[row][c-1] = grid[row][c];
                            grid[row][c] = 0;
                            moved = true;
                        }
                        c--;
                    }
                }
            }
        }
        return moved;
    }

    // Move tiles right
    public boolean moveRight() {
        boolean moved = false;
        for (int row = 0; row < SIZE; row++) {
            int mergePos = -1;
            for (int col = SIZE - 2; col >= 0; col--) {
                if (grid[row][col] != 0) {
                    int c = col;
                    while (c < SIZE - 1 && (grid[row][c+1] == 0 || grid[row][c+1] == grid[row][c])) {
                        if (grid[row][c+1] == grid[row][c] && mergePos != c+1) {
                            grid[row][c+1] *= 2;
                            score += grid[row][c+1];
                            grid[row][c] = 0;
                            mergePos = c+1;
                            moved = true;
                        } else if (grid[row][c+1] == 0) {
                            grid[row][c+1] = grid[row][c];
                            grid[row][c] = 0;
                            moved = true;
                        }
                        c++;
                    }
                }
            }
        }
        return moved;
    }

    // Check if game is over
    public boolean isGameOver() {
        if (winConditionReached) return true;
        // Any zero exists?
        for (int[] row : grid)
            for (int val : row)
                if (val == 0) return false;
        // Check adjacent equals horizontally/vertically
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (j < SIZE - 1 && grid[i][j] == grid[i][j + 1]) return false;
                if (i < SIZE - 1 && grid[i][j] == grid[i + 1][j]) return false;
            }
        }
        return true;
    }

    // Show game over dialog and option to restart
    public void showGameOverMessage() {
        String message;
        if (winConditionReached) {
            message = "Congratulations! You reached the 2048 tile!\nDo you want to continue playing?";
        } else {
            message = "Game over! Do you want to play again?";
        }
        int choice = JOptionPane.showConfirmDialog(frame, message, "Game Over", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            restartGame();
        } else {
            System.exit(0);
        }
    }

    // Restart the game
    public void restartGame() {
        score = 0;
        winConditionReached = false;
        initializeGrid();
        updateGridLabels();
        updateScore();
    }

    // Update score display
    public void updateScore() {
        scoreLabel.setText("Score: " + score);
        if (score > highScore) {
            highScore = score;
            highScoreLabel.setText("High Score: " + highScore);
        }
    }

    // Main method
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Game2048());
    }
}

