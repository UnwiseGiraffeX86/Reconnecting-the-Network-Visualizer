// Team Name: Carbon Coders
// Member: Geala Stefan-Octavian
// Hackathon: Dimensional Gateway Grid Reconnection
// Challenge: Numberlink Solver (Final Fixes for Test 5 & 7)

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
    static int OPS_LIMIT = 500000; 
    static boolean attemptFailed = false;
    static Random rng = new Random();

    public static void main(String[] args) {
        // Modified main method to use Scanner and fit the requested structure
        Scanner scanner = new Scanner(System.in);
        PrintWriter out = new PrintWriter(new BufferedOutputStream(System.out));

        if (scanner.hasNextInt()) {
            R = scanner.nextInt();
            C = scanner.nextInt();
            SIZE = R * C;
            int numEntries = scanner.nextInt();

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
                int runeVal = scanner.nextInt();
                int linearIdx = scanner.nextInt();
                if (linearIdx >= SIZE) continue;

                grid[linearIdx] = runeVal; 

                if (pending.containsKey(runeVal)) {
                    int p1 = pending.get(runeVal);
                    pairList.add(new RunePair(runeVal, p1, linearIdx));
                    pending.remove(runeVal); 
                } else {
                    pending.put(runeVal, linearIdx);
                }
            }

            // Orphan handling (Test 6)
            if (!pending.isEmpty()) {
                List<Integer> orphanKeys = new ArrayList<>(pending.keySet());
                Collections.sort(orphanKeys);
                for (int i = 0; i < orphanKeys.size(); i += 2) {
                    if (i + 1 < orphanKeys.size()) {
                        int id1 = orphanKeys.get(i);
                        int id2 = orphanKeys.get(i+1);
                        pairList.add(new RunePair(id1, pending.get(id1), pending.get(id2)));
                    }
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

            // --- PHASE 1: DETERMINISTIC SOLVE ---
            sortPairsDeterministic();
            // High limit for first pass
            OPS_LIMIT = 20000000; 
            if (solveBacktracking(0, false)) solved = true;

            // --- PHASE 2: RANDOMIZED ITERATIVE DEEPENING ---
            int multiplier = 1;
            while (!solved && hasTime()) {
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
                
                OPS_LIMIT = 500000 * multiplier;
                if (multiplier < 60) multiplier += 5; 
                
                if (solveBacktracking(0, true)) solved = true;
            }

            if (solved) {
                printSolution(out);
            } else {
                out.println("0");
            }
        }
        out.flush();
        out.close();
        scanner.close();
    }

    static boolean hasTime() {
        return System.currentTimeMillis() - startTime < 2450;
    }

    // ==========================================
    // CORE SOLVER
    // ==========================================

    static boolean solveBacktracking(int completedCount, boolean randomized) {
        opsCounter++;
        if (randomized && opsCounter > OPS_LIMIT) {
            attemptFailed = true;
            return false;
        }
        if (completedCount == numPairs) return true;

        int initialHistoryTop = historyTop;

        // 1. FORCED MOVES
        if (!applyForcedMoves()) {
            undoMoves(initialHistoryTop);
            return false;
        }
        
        int currentCompleted = 0;
        for(boolean b : finished) if(b) currentCompleted++;
        if (currentCompleted == numPairs) return true;

        // 2. CONNECTIVITY CHECK
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
                // Strict Validity Check: v must be empty OR the specific target for pair i
                if (v != -1 && (grid[v] == -1 || v == target)) {
                    if (!createsDeadEnd(v, target)) {
                        tempMoves[opts++] = k; 
                    }
                }
            }

            if (opts == 0) {
                undoMoves(initialHistoryTop);
                return false; 
            }

            if (opts < minOptions) {
                minOptions = opts;
                bestPair = i;
                bestMoveCount = opts;
                for(int k=0; k<opts; k++) bestMoves[k] = tempMoves[k];
                if (minOptions == 1) break; 
            } else if (opts == minOptions) {
                int distBest = getDist(heads[bestPair], tails[bestPair]);
                int distCurr = getDist(heads[i], tails[i]);
                if (distCurr < distBest) { 
                    bestPair = i;
                    bestMoveCount = opts;
                    for(int k=0; k<opts; k++) bestMoves[k] = tempMoves[k];
                }
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

    static int getDist(int u, int v) {
        return Math.abs(ROW[u] - ROW[v]) + Math.abs(COL[u] - COL[v]);
    }

    // --- CHECK REGIONS ---
    static boolean checkRegions() {
        bfsCookieCounter++;
        int regionCount = 0;
        
        for(int i=0; i<SIZE; i++) {
            if (grid[i] == -1 && bfsVisCookie[i] != bfsCookieCounter) {
                bfsLabelRegion(i, regionCount++);
            }
        }
        
        for (int p = 0; p < numPairs; p++) {
            if (finished[p]) continue;
            int head = heads[p];
            int tail = tails[p];
            
            boolean adjacent = false;
            for(int k=0; k<4; k++) { if (ADJ[head][k] == tail) { adjacent = true; break; } }
            if (adjacent) continue;
            
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
            if (attemptFailed) return false; 

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
            scores[i] = scoreMove(u, moves[i], target) + rng.nextInt(20);
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
        int dist = getDist(v, target);
        int hugs = 0;
        for(int k=0; k<4; k++) {
            int n = ADJ[v][k];
            if (n == -1 || grid[n] != -1) hugs++;
        }
        return 2000 + (hugs * 50) - (dist * 2);
    }

    static void sortPairsDeterministic() {
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
}
