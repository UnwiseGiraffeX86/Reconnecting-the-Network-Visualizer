// Team Name: Carbon Coders
// Member: Geala Stefan-Octavian
// Hackathon: Dimensional Gateway Grid Reconnection
// Challenge: Numberlink Solver (Iterative Deepening + Tunnel Prioritization)

import java.io.*;
import java.util.*;

public class Solution {

    // --- GLOBAL STATE ---
    static int R, C, SIZE;
    static int[] grid; 
    static int[] heads; 
    static int[] tails;
    static boolean[] finished;
    static RunePair[] pairsInfo; 
    static int numPairs;
    static int[] parent; 

    // --- PRECOMPUTED LOOKUPS ---
    static int[][] ADJ; 
    static int[] ROW; 
    static int[] COL; 

    // --- BUFFERS (Global) ---
    static int[] bfsQueue;
    static int[] bfsVisCookie; 
    static int bfsCookieCounter = 0;
    static int[] regionMap;
    
    // --- HISTORY STACK ---
    static int[] historyStack;
    static int historyTop = 0;
    
    // --- CONTROL ---
    static long startTime;
    static int opsCounter; 
    static final int OPS_LIMIT = 8000000; // Increased limit
    static boolean attemptFailed = false;
    static Random rng = new Random();

    public static void main(String[] args) {
        InputReader in = new InputReader();
        PrintWriter out = new PrintWriter(new BufferedOutputStream(System.out));

        try {
            String token = in.next();
            if (token == null) return;
            R = Integer.parseInt(token);
            C = Integer.parseInt(in.next());
            SIZE = R * C;
            int numEntries = Integer.parseInt(in.next());

            // 1. PRECOMPUTE TOPOLOGY
            ADJ = new int[SIZE][4];
            ROW = new int[SIZE];
            COL = new int[SIZE];
            
            for (int r = 0; r < R; r++) {
                for (int c = 0; c < C; c++) {
                    int u = r * C + c;
                    ROW[u] = r;
                    COL[u] = c;
                    ADJ[u][0] = (r > 0) ? u - C : -1; // N
                    ADJ[u][1] = (r < R - 1) ? u + C : -1; // S
                    ADJ[u][2] = (c < C - 1) ? u + 1 : -1; // E
                    ADJ[u][3] = (c > 0) ? u - 1 : -1; // W
                }
            }

            grid = new int[SIZE];
            Arrays.fill(grid, -1);
            parent = new int[SIZE];
            Arrays.fill(parent, -1);

            Map<Integer, Integer> pending = new HashMap<>();
            ArrayList<RunePair> pairList = new ArrayList<>();

            for (int i = 0; i < numEntries; i++) {
                int runeVal = Integer.parseInt(in.next());
                int linearIdx = Integer.parseInt(in.next());
                if (linearIdx >= SIZE) continue;

                grid[linearIdx] = runeVal; 

                if (pending.containsKey(runeVal)) {
                    int p1 = pending.get(runeVal);
                    pairList.add(new RunePair(runeVal, p1, linearIdx));
                } else {
                    pending.put(runeVal, linearIdx);
                }
            }

            numPairs = pairList.size();
            pairsInfo = pairList.toArray(new RunePair[0]);
            
            heads = new int[numPairs];
            tails = new int[numPairs];
            finished = new boolean[numPairs];
            
            for(int i=0; i<numPairs; i++) {
                heads[i] = pairsInfo[i].start;
                tails[i] = pairsInfo[i].end;
            }

            // Init buffers
            bfsQueue = new int[SIZE];
            bfsVisCookie = new int[SIZE];
            regionMap = new int[SIZE];
            historyStack = new int[SIZE * 4 + 500000]; 

            startTime = System.currentTimeMillis();
            boolean solved = false;

            // --- PHASE 1: DETERMINISTIC SOLVE (Strict Heuristic) ---
            // Try solving with strict "Most Constrained" logic first.
            // This usually solves easy/medium puzzles (0-4) instantly.
            sortPairsDeterministic();
            if (solveBacktracking(0, false)) solved = true;

            // --- PHASE 2: RANDOMIZED SOLVE (Deep Search) ---
            // If deterministic failed (Test 5, 6, 7, 8), use Monte Carlo.
            while (!solved) {
                shufflePairs();
                
                // Reset State
                for(int i=0; i<numPairs; i++) {
                    heads[i] = pairsInfo[i].start;
                    tails[i] = pairsInfo[i].end;
                    finished[i] = false;
                }
                Arrays.fill(grid, -1);
                Arrays.fill(parent, -1);
                for(RunePair p : pairsInfo) {
                    grid[p.start] = p.id;
                    grid[p.end] = p.id;
                }
                
                historyTop = 0;
                opsCounter = 0;
                attemptFailed = false;
                
                if (solveBacktracking(0, true)) solved = true;
            }

            if (solved) {
                printSolution(out);
            } else {
                out.println("0");
            }

        } catch (Exception e) {
            // silent
        }
        out.flush();
        out.close();
    }

    // ==========================================
    // CORE BACKTRACKING SOLVER
    // ==========================================

    static boolean solveBacktracking(int completedCount, boolean randomized) {
        opsCounter++;
        if (randomized && opsCounter > OPS_LIMIT) {
            attemptFailed = true;
            return false;
        }
        if (completedCount == numPairs) return true;

        int initialHistoryTop = historyTop;

        // 1. FORCED MOVES (Iterative)
        if (!applyForcedMoves()) {
            undoMoves(initialHistoryTop);
            return false;
        }
        
        int currentCompleted = 0;
        for(boolean b : finished) if(b) currentCompleted++;
        if (currentCompleted == numPairs) return true;

        // 2. CONNECTIVITY CHECK (Expensive, run periodically)
        // Run only if we made significant progress or forced moves stabilized?
        // Actually, for correctness on hard grids, we MUST check this often.
        if (!checkRegions()) {
            undoMoves(initialHistoryTop);
            return false;
        }

        // 3. BRANCHING (MCAT)
        int bestPair = -1;
        int minOptions = 5;
        int[] bestMoves = new int[4];
        int bestMoveCount = 0;

        for (int i = 0; i < numPairs; i++) {
            if (finished[i]) continue;
            
            int u = heads[i];
            int target = tails[i];
            int opts = 0;
            int[] tempMoves = new int[4];
            
            for(int k=0; k<4; k++) {
                int v = ADJ[u][k];
                if (v != -1 && (grid[v] == -1 || v == target)) {
                    if (!createsDeadEnd(v, target)) {
                        tempMoves[opts++] = k; 
                    }
                }
            }

            if (opts == 0) {
                undoMoves(initialHistoryTop);
                return false; // Stranded
            }

            if (opts < minOptions) {
                minOptions = opts;
                bestPair = i;
                bestMoveCount = opts;
                // Manual array copy for speed
                for(int k=0; k<opts; k++) bestMoves[k] = tempMoves[k];
                if (minOptions == 1) break; 
            }
        }

        int pIdx = bestPair;
        int u = heads[pIdx];
        int target = tails[pIdx];
        int id = pairsInfo[pIdx].id;

        if (randomized) {
            sortMovesRandomized(u, target, bestMoves, bestMoveCount);
        } else {
            sortMovesDeterministic(u, target, bestMoves, bestMoveCount);
        }

        for (int i = 0; i < bestMoveCount; i++) {
            int dirIdx = bestMoves[i];
            int v = ADJ[u][dirIdx];

            recordMove(pIdx, u, v, id);
            
            if (solveBacktracking(currentCompleted + (finished[pIdx] ? 1 : 0), randomized)) return true;
            if (randomized && attemptFailed) break; 
            
            undoMoves(historyTop - 3); 
        }

        undoMoves(initialHistoryTop);
        return false;
    }

    // --- CHECK REGIONS ---
    static boolean checkRegions() {
        bfsCookieCounter++;
        int regionCount = 0;
        
        // Label Regions
        for(int i=0; i<SIZE; i++) {
            if (grid[i] == -1 && bfsVisCookie[i] != bfsCookieCounter) {
                bfsLabelRegion(i, regionCount++);
            }
        }
        
        for (int p = 0; p < numPairs; p++) {
            if (finished[p]) continue;
            int head = heads[p];
            int tail = tails[p];
            
            // Direct Adjacency Check
            boolean adjacent = false;
            for(int k=0; k<4; k++) { if (ADJ[head][k] == tail) { adjacent = true; break; } }
            if (adjacent) continue;
            
            // Check Connectivity
            long headMask = 0;
            boolean headTouchesAny = false;
            for(int k=0; k<4; k++) {
                int n = ADJ[head][k];
                if (n != -1 && grid[n] == -1 && bfsVisCookie[n] == bfsCookieCounter) {
                    int rID = regionMap[n];
                    if (rID < 64) headMask |= (1L << rID);
                    headTouchesAny = true;
                }
            }
            if (!headTouchesAny) return false;
            
            boolean connected = false;
            for(int k=0; k<4; k++) {
                int n = ADJ[tail][k];
                if (n != -1 && grid[n] == -1 && bfsVisCookie[n] == bfsCookieCounter) {
                    int rID = regionMap[n];
                    if (rID < 64) {
                        if ((headMask & (1L << rID)) != 0) { connected = true; break; }
                    } else { connected = true; break; }
                }
            }
            if (!connected) return false;
        }
        return true;
    }
    
    static void bfsLabelRegion(int start, int rID) {
        int head = 0, tail = 0;
        bfsQueue[tail++] = start;
        bfsVisCookie[start] = bfsCookieCounter;
        regionMap[start] = rID;
        
        while(head < tail) {
            int u = bfsQueue[head++];
            for(int k=0; k<4; k++) {
                int v = ADJ[u][k];
                if (v != -1 && grid[v] == -1 && bfsVisCookie[v] != bfsCookieCounter) {
                    bfsVisCookie[v] = bfsCookieCounter;
                    regionMap[v] = rID;
                    bfsQueue[tail++] = v;
                }
            }
        }
    }

    // --- FORCED MOVES ---
    static boolean applyForcedMoves() {
        boolean changed = true;
        while (changed) {
            changed = false;
            if (attemptFailed) return false; // Early exit on timeout

            // 1. Active Heads with 1 option
            for (int i = 0; i < numPairs; i++) {
                if (finished[i]) continue;
                int u = heads[i];
                int target = tails[i];
                int validCount = 0;
                int forcedV = -1;

                for (int k = 0; k < 4; k++) {
                    int v = ADJ[u][k];
                    if (v != -1 && (grid[v] == -1 || v == target)) {
                        if (!createsDeadEnd(v, target)) {
                            validCount++;
                            forcedV = v;
                        }
                    }
                }

                if (validCount == 0) return false;
                if (validCount == 1) {
                    recordMove(i, u, forcedV, pairsInfo[i].id);
                    changed = true;
                }
            }
            
            // 2. Tunnel/Cul-de-sac Filling
            // Scan neighbors of active heads
            for (int i = 0; i < numPairs; i++) {
                if (finished[i]) continue;
                int u = heads[i];
                for(int k=0; k<4; k++) {
                    int v = ADJ[u][k];
                    if (v != -1 && grid[v] == -1) {
                        if (getAccessDegree(v) <= 1) { 
                            recordMove(i, u, v, pairsInfo[i].id);
                            changed = true;
                            break; 
                        }
                    }
                }
            }
        }
        return true;
    }

    static void recordMove(int pIdx, int prevHead, int newHead, int id) {
        historyStack[historyTop++] = pIdx;
        historyStack[historyTop++] = prevHead;
        historyStack[historyTop++] = newHead;
        grid[newHead] = id;
        heads[pIdx] = newHead;
        parent[newHead] = prevHead; 
        if (newHead == tails[pIdx]) finished[pIdx] = true;
    }

    static void undoMoves(int targetTop) {
        while (historyTop > targetTop) {
            int newHead = historyStack[--historyTop];
            int prevHead = historyStack[--historyTop];
            int pIdx = historyStack[--historyTop];
            if (finished[pIdx] && newHead == tails[pIdx]) finished[pIdx] = false;
            if (newHead != tails[pIdx]) grid[newHead] = -1;
            heads[pIdx] = prevHead;
        }
    }

    // --- HELPERS ---
    static boolean createsDeadEnd(int v, int target) {
        if (v == target) return false;
        for (int i=0; i<4; i++) {
            int n = ADJ[v][i];
            if (n == -1) continue;
            if (grid[n] == -1) {
                int exits = 0;
                for(int j=0; j<4; j++) {
                    int nn = ADJ[n][j];
                    if (nn == -1 || nn == v) continue;
                    if (grid[nn] == -1) exits++;
                    else {
                        // Check if it's an unfinished endpoint
                        // Optimized: check if grid val is an unfinished ID
                        // This requires mapping ID back to pair index or checking all pairs.
                        // Checking all pairs is fast enough (~12 checks).
                        for(int p=0; p<numPairs; p++) {
                            if (!finished[p]) {
                                if (heads[p] == nn || tails[p] == nn) { exits++; break; }
                            }
                        }
                    }
                }
                if (exits == 0) return true; 
            }
        }
        return false;
    }
    
    static int getAccessDegree(int v) {
        int count = 0;
        for(int k=0; k<4; k++) {
            int n = ADJ[v][k];
            if (n != -1) {
                if (grid[n] == -1) count++;
                else {
                    for(int p=0; p<numPairs; p++) if(!finished[p] && heads[p]==n) { count++; break; }
                }
            }
        }
        return count;
    }

    static void sortMovesRandomized(int u, int target, int[] moves, int count) {
        int[] scores = new int[count];
        for(int i=0; i<count; i++) {
            scores[i] = scoreMove(u, moves[i], target) + rng.nextInt(5); // Noise
        }
        sortMovesByScore(moves, scores, count);
    }

    static void sortMovesDeterministic(int u, int target, int[] moves, int count) {
        int[] scores = new int[count];
        for(int i=0; i<count; i++) {
            scores[i] = scoreMove(u, moves[i], target);
        }
        sortMovesByScore(moves, scores, count);
    }

    static void sortMovesByScore(int[] moves, int[] scores, int count) {
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                if (scores[j] > scores[i]) {
                    int t = moves[i]; moves[i] = moves[j]; moves[j] = t;
                    int s = scores[i]; scores[i] = scores[j]; scores[j] = s;
                }
            }
        }
    }

    static int scoreMove(int u, int dirIdx, int target) {
        int v = ADJ[u][dirIdx];
        int dist = Math.abs(ROW[v] - ROW[target]) + Math.abs(COL[v] - COL[target]);
        int hugs = 0;
        for(int k=0; k<4; k++) {
            int n = ADJ[v][k];
            if (n == -1 || grid[n] != -1) hugs++;
        }
        return 500 + (hugs * 50) - dist;
    }

    static void sortPairsDeterministic() {
        // Sort by distance (Longest First)
        Arrays.sort(pairsInfo, (a, b) -> b.dist - a.dist);
        reSyncPairs();
    }

    static void shufflePairs() {
        for (int i = pairsInfo.length - 1; i > 0; i--) {
            int idx = rng.nextInt(i + 1);
            RunePair temp = pairsInfo[idx]; pairsInfo[idx] = pairsInfo[i]; pairsInfo[i] = temp;
        }
        reSyncPairs();
    }

    static void reSyncPairs() {
        for(int i=0; i<numPairs; i++) {
            heads[i] = pairsInfo[i].start;
            tails[i] = pairsInfo[i].end;
        }
    }

    static void printSolution(PrintWriter out) {
        ArrayList<ConnectedPath> results = new ArrayList<>();
        for (int i = 0; i < numPairs; i++) {
            RunePair p = pairsInfo[i];
            StringBuilder sb = new StringBuilder();
            int curr = p.end;
            while (curr != p.start) {
                int prev = parent[curr];
                int diff = curr - prev;
                if (diff == -C) sb.append('N'); else if (diff == C) sb.append('S'); else if (diff == 1) sb.append('E'); else sb.append('W');
                curr = prev;
            }
            results.add(new ConnectedPath(p.start, sb.reverse().toString()));
        }
        Collections.sort(results, Comparator.comparingInt(res -> res.origin));
        out.println(results.size());
        for (ConnectedPath cp : results) {
            out.print(cp.origin + " " + cp.moves.length());
            for (int k = 0; k < cp.moves.length(); k++) out.print(" " + cp.moves.charAt(k));
            out.println();
        }
    }

    static class RunePair { int id, start, end, dist; RunePair(int id, int s, int e) { this.id=id; this.start=s; this.end=e; this.dist=Math.abs(s/C-e/C)+Math.abs(s%C-e%C); } }
    static class ConnectedPath { int origin; String moves; ConnectedPath(int o, String m) { this.origin=o; this.moves=m; } }
    static class InputReader {
        BufferedReader reader; StringTokenizer tokenizer;
        public InputReader() { reader = new BufferedReader(new InputStreamReader(System.in)); }
        String next() {
            while (tokenizer == null || !tokenizer.hasMoreTokens()) {
                try { String line = reader.readLine(); if (line == null) return null; tokenizer = new StringTokenizer(line); } catch (IOException e) { return null; }
            }
            return tokenizer.nextToken();
        }
    }
}