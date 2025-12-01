# Arcane Grid Reconnection (Bestem)

A visual coding challenge platform designed to test and visualize pathfinding algorithms. The goal is to connect matching rune pairs on a grid without paths overlapping or crossing.

> **Note:** This project was inspired by the "Reconnecting the Network" challenge from the **BESTEM V14 Phase 1 Preselection**. I decided to expand it into a full-featured visualization tool for my GitHub portfolio.

## 🎮 Features

- **Interactive Visualizer**: Watch your algorithm's paths come to life on the grid.
- **Multi-Language Support**: Write solutions in **Java**, **Python**, or **C++**.
- **Level Editor**: Create and save your own custom test cases.
- **Batch Testing**: Run your solution against all available test cases in one click.
- **Performance Metrics**: Analyze your solution's efficiency with metrics for Path Length, Turn Count, and Optimality (vs Manhattan distance).
- **Code Snapshots**: Save and restore versions of your code to safely experiment with optimizations.

## 🚀 Getting Started

### Prerequisites

- **Python 3.x**
- **Java JDK** (if using Java)
- **G++ / MinGW** (if using C++)

### Installation

1.  Clone the repository or download the source code.
2.  Install the required Python dependencies:

    ```bash
    pip install flask
    ```

3.  Ensure your input/output directories are set up correctly in `server.py` if they differ from the default.

### Running the Application

1.  Start the Flask server:

    ```bash
    python server.py
    ```

2.  Open your web browser and navigate to:

    ```
    http://localhost:5000
    ```

## 🧩 Problem Statement

**The Task:**
Given a grid with paired arcane runes, calculate valid, non-overlapping paths to connect each pair.

**Constraints:**
-   Grid dimensions up to 14x14.
-   Paths cannot share cells.
-   Paths cannot cross each other.
-   Each rune ID appears exactly twice (start and end).

**Input Format:**
A stream of integers: `rows cols n_pairs [rune_id node_index]...`

**Output Format:**
```text
<number_of_channels>
<origin_index> <num_steps> <direction_1> <direction_2> ...
```

## 🛠️ Project Structure

-   **`server.py`**: Flask backend handling API requests, file operations, and code execution.
-   **`static/`**: Frontend application.
    -   `index.html`: Main UI layout.
    -   `script.js`: Application logic, visualization, and editor integration.
    -   `style.css`: Styling and themes.
-   **`input/`**: Directory containing `.txt` test case files.
-   **`output/`**: Directory containing expected output files (optional).

## ⌨️ Controls

-   **Ctrl/Cmd + Enter**: Run the current code.
-   **Play/Pause**: Control the visualization animation.
-   **Slider**: Scrub through the pathfinding steps.
-   **Snapshots (📷)**: Save the current state of your code.

## 📝 License

This project is for educational purposes.
