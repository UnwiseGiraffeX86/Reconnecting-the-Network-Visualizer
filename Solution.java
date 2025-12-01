import java.util.*;
import java.io.*;

public class Solution {

    // Global variables to maintain logic state
    static int gridRows, gridCols;
    static List<RunePair> allRunePairs = new ArrayList<>();
    static int[][] connectionGrid;
    static final int EMPTY_CELL = -1;
    static long startTime;

    // Directions: 0 = N, 1 = E, 2 = S, 3 = W
    static int[] rowDelta = {-1, 0, 1, 0};
    static int[] colDelta = {0, 1, 0, -1};

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextInt()) {
            int rows = scanner.nextInt();
            int cols = scanner.nextInt();
            int nPairs = scanner.nextInt(); // In this context, this is the number of endpoint definitions
            
            // Initialize globals
            gridRows = rows;
            gridCols = cols;
            startTime = System.currentTimeMillis();

            Map<Integer, List<Integer>> tempEndpoints = new HashMap<>();

            // Your code here
            try {
                // Read endpoints
                for (int i = 0; i < nPairs; i++) {
                    int runeId = scanner.nextInt();
                    int nodeIdx = scanner.nextInt();

                    tempEndpoints.computeIfAbsent(runeId, k -> new ArrayList<>()).add(nodeIdx);
                }

                // Build rune pairs, enforcing "origin = smallest position"
                allRunePairs.clear();
                for (Map.Entry<Integer, List<Integer>> entry : tempEndpoints.entrySet()) {
                    List<Integer> locs = entry.getValue();
                    if (locs.size() == 2) {
                        int idx1 = locs.get(0);
                        int idx2 = locs.get(1);
                        int sIdx = Math.min(idx1, idx2);
                        int eIdx = Math.max(idx1, idx2);

                        Point pStart = idxToPoint(sIdx);
                        Point pEnd   = idxToPoint(eIdx);

                        allRunePairs.add(new RunePair(entry.getKey(), sIdx, eIdx, pStart, pEnd));
                    }
                }

                solveWithHeuristics();

            } catch (Exception e) {
                // Fail gracefully
                System.out.println(0);
            }
        }
        scanner.close();
    }

    private static Point idxToPoint(int idx) {
        return new Point(idx / gridCols, idx % gridCols);
    }

    private static void solveWithHeuristics() {
        // Strategy 1: sort by Manhattan distance ascending
        Collections.sort(allRunePairs, new Comparator<RunePair>() {
            public int compare(RunePair a, RunePair b) {
                return a.manhattanDist() - b.manhattanDist();
            }
        });
        if (attemptSolution()) return;

        // Strategy 2: sort by Manhattan distance descending
        Collections.sort(allRunePairs, new Comparator<RunePair>() {
            public int compare(RunePair a, RunePair b) {
                return b.manhattanDist() - a.manhattanDist();
            }
        });
        if (attemptSolution()) return;

        // Strategy 3: "backbone first" monte-carlo (random shuffles) with time limit
        Random rng = new Random(1234567L);
        // Using 3500ms limit similar to original logic
        while (System.currentTimeMillis() - startTime < 3500L) {
            Collections.shuffle(allRunePairs, rng);
            if (attemptSolution()) return;
        }

        // If nothing worked in time, print 0
        System.out.println(0);
    }

    private static boolean attemptSolution() {
        // reset grid
        connectionGrid = new int[gridRows][gridCols];
        for (int[] row : connectionGrid) {
            Arrays.fill(row, EMPTY_CELL);
        }

        // mark endpoints as occupied
        for (RunePair p : allRunePairs) {
            connectionGrid[p.start.r][p.start.c] = p.id;
            connectionGrid[p.end.r][p.end.c]     = p.id;
        }

        List<ConnectedPath> successfulPaths = new ArrayList<>();

        // for each pair in current order, find shortest path with BFS
        for (RunePair pair : allRunePairs) {
            ConnectedPath path = findShortestPathBFS(pair);
            if (path == null) {
                return false; // this ordering failed
            }

            // mark path cells (excluding endpoints already set)
            for (Point step : path.steps) {
                if (!step.equals(pair.start) && !step.equals(pair.end)) {
                    connectionGrid[step.r][step.c] = pair.id;
                }
            }
            successfulPaths.add(path);
        }

        // if we reached here, we found a valid solution for this ordering
        printSolution(successfulPaths);
        return true;
    }

    private static ConnectedPath findShortestPathBFS(RunePair pair) {
        int totalCells = gridRows * gridCols;
        int[] qRows = new int[totalCells];
        int[] qCols = new int[totalCells];
        int head = 0, tail = 0;

        qRows[tail] = pair.start.r;
        qCols[tail] = pair.start.c;
        tail++;

        int[][] parent = new int[gridRows][gridCols];
        for (int[] row : parent) {
            Arrays.fill(row, -1);
        }

        boolean[][] visited = new boolean[gridRows][gridCols];
        visited[pair.start.r][pair.start.c] = true;

        while (head < tail) {
            int r = qRows[head];
            int c = qCols[head];
            head++;

            // destination reached
            if (r == pair.end.r && c == pair.end.c) {
                return reconstructPath(pair, parent);
            }

            // explore 4 neighbors in fixed order N, E, S, W
            for (int d = 0; d < 4; d++) {
                int nr = r + rowDelta[d];
                int nc = c + colDelta[d];

                if (nr >= 0 && nr < gridRows && nc >= 0 && nc < gridCols && !visited[nr][nc]) {
                    boolean isTarget = (nr == pair.end.r && nc == pair.end.c);
                    boolean isEmpty  = (connectionGrid[nr][nc] == EMPTY_CELL);

                    if (isEmpty || isTarget) {
                        visited[nr][nc] = true;
                        parent[nr][nc] = r * gridCols + c;
                        qRows[tail] = nr;
                        qCols[tail] = nc;
                        tail++;
                    }
                }
            }
        }

        return null; // no path found in this configuration
    }

    private static ConnectedPath reconstructPath(RunePair pair, int[][] parent) {
        LinkedList<Point> steps = new LinkedList<>();
        LinkedList<String> directions = new LinkedList<>();

        int currR = pair.end.r;
        int currC = pair.end.c;

        while (currR != pair.start.r || currC != pair.start.c) {
            steps.addFirst(new Point(currR, currC));
            int pIdx = parent[currR][currC];
            int pr = pIdx / gridCols;
            int pc = pIdx % gridCols;

            if (currR < pr) directions.addFirst("N");
            else if (currR > pr) directions.addFirst("S");
            else if (currC > pc) directions.addFirst("E");
            else directions.addFirst("W");

            currR = pr;
            currC = pc;
        }

        steps.addFirst(pair.start);
        return new ConnectedPath(pair, steps, directions);
    }

    private static void printSolution(List<ConnectedPath> results) {
        // sort lines by rune id
        Collections.sort(results, new Comparator<ConnectedPath>() {
            public int compare(ConnectedPath a, ConnectedPath b) {
                return a.pair.id - b.pair.id;
            }
        });

        System.out.println(results.size());
        for (ConnectedPath res : results) {
            // origin = smallest index of the pair
            int originIdx = res.pair.startIdx;
            System.out.print(originIdx + " " + res.directions.size());
            for (String dir : res.directions) {
                System.out.print(" " + dir);
            }
            System.out.println();
        }
    }

    // --- Helper Classes ---

    static class Point {
        int r, c;
        Point(int r, int c) { this.r = r; this.c = c; }
        public boolean equals(Point o) { return o != null && r == o.r && c == o.c; }
    }

    static class RunePair {
        int id;
        int startIdx, endIdx;
        Point start, end;

        RunePair(int id, int startIdx, int endIdx, Point start, Point end) {
            this.id = id;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.start = start;
            this.end = end;
        }

        int manhattanDist() {
            return Math.abs(start.r - end.r) + Math.abs(start.c - end.c);
        }
    }

    static class ConnectedPath {
        RunePair pair;
        List<Point> steps;
        List<String> directions;

        ConnectedPath(RunePair p, List<Point> s, List<String> d) {
            this.pair = p;
            this.steps = s;
            this.directions = d;
        }
    }
}