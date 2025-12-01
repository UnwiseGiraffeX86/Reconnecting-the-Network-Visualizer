class Visualizer {
    constructor() {
        this.currentInput = null;
        this.currentOutput = null;
        this.gridContainer = document.getElementById('grid-container');
        this.statusBar = document.getElementById('status-bar');
        this.consoleOutput = document.getElementById('console-output');
        this.fileList = document.getElementById('file-list');
        this.editor = null;
        
        // Animation state
        this.animationSteps = [];
        this.currentStep = 0;
        this.isPlaying = false;
        this.animationTimer = null;
        this.paths = []; // Store parsed paths for interaction
        
        // Editor state
        this.isEditing = false;
        this.editorGrid = { rows: 10, cols: 10, runes: new Map() }; // Map<index, runeId>
        
        // Language & Snapshots
        this.currentLanguage = localStorage.getItem('selectedLanguage') || 'java';
        this.snapshots = JSON.parse(localStorage.getItem('snapshots') || '[]');

        this.init();
    }

    async init() {
        await this.initMonaco();
        await this.loadFileList();
        this.renderSnapshots();
        
        document.getElementById('run-btn').addEventListener('click', () => this.runCode());
        document.getElementById('run-all-btn').addEventListener('click', () => this.runAllTests());
        document.getElementById('show-expected-btn').addEventListener('click', () => this.showExpected());
        
        // Snapshots & Language
        document.getElementById('take-snapshot-btn').addEventListener('click', () => this.takeSnapshot());
        document.getElementById('language-select').addEventListener('change', (e) => this.setLanguage(e.target.value));
        document.getElementById('language-select').value = this.currentLanguage;

        // Editor Controls
        document.getElementById('mode-toggle-btn').addEventListener('click', () => this.toggleEditMode());
        document.getElementById('resize-grid-btn').addEventListener('click', () => this.initEditorGrid());
        document.getElementById('rune-id').addEventListener('input', () => this.updateRunePreview());
        document.getElementById('save-level-btn').addEventListener('click', () => this.saveLevel());
        this.updateRunePreview(); // Init preview color

        // Modal close
        document.querySelectorAll('.close-modal').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.target.closest('.modal').style.display = 'none';
            });
        });

        // Problem Modal
        document.getElementById('problem-btn').addEventListener('click', () => {
            document.getElementById('problem-modal').style.display = 'block';
        });
        
        // Debug controls
        document.getElementById('play-btn').addEventListener('click', () => this.togglePlay());
        document.getElementById('anim-slider').addEventListener('input', (e) => this.seekAnimation(e.target.value));
        document.getElementById('show-coords').addEventListener('change', (e) => this.toggleCoords(e.target.checked));
        
        // Resizer Logic
        const resizer = document.getElementById('dragMe');
        const leftSide = document.getElementById('viz-panel');
        const rightSide = document.getElementById('code-panel');
        let x = 0;
        let leftWidth = 0;

        const mouseDownHandler = function(e) {
            x = e.clientX;
            leftWidth = leftSide.getBoundingClientRect().width;
            resizer.classList.add('resizing');
            document.addEventListener('mousemove', mouseMoveHandler);
            document.addEventListener('mouseup', mouseUpHandler);
        };

        const mouseMoveHandler = (e) => {
            const dx = e.clientX - x;
            const newLeftWidth = ((leftWidth + dx) * 100) / resizer.parentNode.getBoundingClientRect().width;
            leftSide.style.width = `${newLeftWidth}%`;
            // rightSide.style.width = `${100 - newLeftWidth}%`; // Flexbox handles this automatically if we remove width from right side
            
            if (this.editor) this.editor.layout();
        };

        const mouseUpHandler = function() {
            resizer.classList.remove('resizing');
            document.removeEventListener('mousemove', mouseMoveHandler);
            document.removeEventListener('mouseup', mouseUpHandler);
        };

        resizer.addEventListener('mousedown', mouseDownHandler);
        
        window.addEventListener('resize', () => {
            if (this.editor) {
                this.editor.layout();
            }
        });

        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                e.preventDefault();
                this.runCode();
            }
        });
    }

    initMonaco() {
        return new Promise((resolve) => {
            require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.44.0/min/vs' }});
            require(['vs/editor/editor.main'], () => {
                // Load saved code or default
                const savedCode = localStorage.getItem(`${this.currentLanguage}SolutionCode`);
                const defaultCode = this.getDefaultCode(this.currentLanguage);

                this.editor = monaco.editor.create(document.getElementById('monaco-editor-container'), {
                    value: savedCode || defaultCode,
                    language: this.currentLanguage,
                    theme: 'vs-dark',
                    automaticLayout: true,
                    minimap: { enabled: false },
                    fontSize: 14
                });

                // Auto-save on change
                this.editor.onDidChangeModelContent(() => {
                    localStorage.setItem(`${this.currentLanguage}SolutionCode`, this.editor.getValue());
                });

                resolve();
            });
        });
    }

    getDefaultCode(lang) {
        if (lang === 'java') {
            return [
                'import java.util.*;',
                'import java.io.*;',
                '',
                'public class Solution {',
                '    public static void main(String[] args) {',
                '        Scanner scanner = new Scanner(System.in);',
                '        if (scanner.hasNextInt()) {',
                '            int rows = scanner.nextInt();',
                '            int cols = scanner.nextInt();',
                '            int nPairs = scanner.nextInt();',
                '            // Your code here',
                '        }',
                '    }',
                '}'
            ].join('\n');
        } else if (lang === 'python') {
            return [
                'import sys',
                '',
                'def solve():',
                '    input_data = sys.stdin.read().split()',
                '    if not input_data: return',
                '    iterator = iter(input_data)',
                '    ',
                '    rows = int(next(iterator))',
                '    cols = int(next(iterator))',
                '    n_pairs = int(next(iterator))',
                '    ',
                '    # Your code here',
                '    # print(f"{num_channels}")',
                '    # print(f"{origin} {segments} {directions}")',
                '',
                'if __name__ == "__main__":',
                '    solve()'
            ].join('\n');
        } else if (lang === 'cpp') {
            return [
                '#include <iostream>',
                '#include <vector>',
                '#include <string>',
                '',
                'using namespace std;',
                '',
                'int main() {',
                '    int rows, cols, nPairs;',
                '    if (cin >> rows >> cols >> nPairs) {',
                '        // Your code here',
                '    }',
                '    return 0;',
                '}'
            ].join('\n');
        }
        return '';
    }

    setLanguage(lang) {
        // Save current code first (already handled by auto-save, but good to be safe)
        localStorage.setItem(`${this.currentLanguage}SolutionCode`, this.editor.getValue());
        
        this.currentLanguage = lang;
        localStorage.setItem('selectedLanguage', lang);
        
        const savedCode = localStorage.getItem(`${lang}SolutionCode`);
        const code = savedCode || this.getDefaultCode(lang);
        
        monaco.editor.setModelLanguage(this.editor.getModel(), lang);
        this.editor.setValue(code);
        
        // Update auto-save listener context (actually the listener uses this.currentLanguage so it's fine)
    }

    // --- Snapshots ---

    takeSnapshot() {
        const code = this.editor.getValue();
        const timestamp = new Date().toLocaleTimeString();
        const id = Date.now();
        
        const snapshot = {
            id: id,
            time: timestamp,
            code: code,
            lang: this.currentLanguage
        };
        
        this.snapshots.unshift(snapshot); // Add to top
        if (this.snapshots.length > 10) this.snapshots.pop(); // Limit to 10
        
        localStorage.setItem('snapshots', JSON.stringify(this.snapshots));
        this.renderSnapshots();
    }

    restoreSnapshot(id) {
        const snapshot = this.snapshots.find(s => s.id === id);
        if (snapshot) {
            if (confirm(`Restore snapshot from ${snapshot.time}? Current code will be overwritten.`)) {
                if (this.currentLanguage !== snapshot.lang) {
                    this.setLanguage(snapshot.lang);
                    document.getElementById('language-select').value = snapshot.lang;
                }
                this.editor.setValue(snapshot.code);
            }
        }
    }

    deleteSnapshot(id, e) {
        e.stopPropagation();
        this.snapshots = this.snapshots.filter(s => s.id !== id);
        localStorage.setItem('snapshots', JSON.stringify(this.snapshots));
        this.renderSnapshots();
    }

    renderSnapshots() {
        const list = document.getElementById('snapshot-list');
        list.innerHTML = '';
        
        this.snapshots.forEach(snap => {
            const li = document.createElement('li');
            li.innerHTML = `
                <div>
                    <span style="font-weight:bold; margin-right:5px;">${snap.lang}</span>
                    <span class="time">${snap.time}</span>
                </div>
                <span class="delete-snap" title="Delete">×</span>
            `;
            li.addEventListener('click', () => this.restoreSnapshot(snap.id));
            li.querySelector('.delete-snap').addEventListener('click', (e) => this.deleteSnapshot(snap.id, e));
            list.appendChild(li);
        });
    }

    async loadFileList() {
        try {
            const response = await fetch('/api/files');
            const files = await response.json();
            
            this.fileList.innerHTML = '';
            files.forEach(file => {
                const li = document.createElement('li');
                li.textContent = file;
                li.addEventListener('click', () => this.loadCase(file, li));
                this.fileList.appendChild(li);
            });
            
            if (files.length > 0) {
                // Load first file by default
                this.fileList.firstChild.click();
            }
        } catch (e) {
            console.error('Error loading file list:', e);
        }
    }

    async loadCase(filename, liElement) {
        // Update active state
        document.querySelectorAll('#file-list li').forEach(el => el.classList.remove('active'));
        liElement.classList.add('active');
        
        try {
            const response = await fetch(`/api/case/${filename}`);
            const data = await response.json();
            
            this.currentInput = data.input;
            this.currentOutput = data.output; // Expected output
            
            this.renderInput(this.currentInput);
            this.consoleOutput.textContent = 'Loaded ' + filename;
        } catch (e) {
            console.error('Error loading case:', e);
            this.consoleOutput.textContent = 'Error loading case: ' + e.message;
        }
    }

    parseInput(inputStr) {
        const tokens = inputStr.trim().split(/\s+/);
        let idx = 0;
        
        const rows = parseInt(tokens[idx++]);
        const cols = parseInt(tokens[idx++]);
        const nDefs = parseInt(tokens[idx++]); // Number of rune definitions
        
        const runes = [];
        for (let i = 0; i < nDefs; i++) {
            const runeId = parseInt(tokens[idx++]);
            const nodeIdx = parseInt(tokens[idx++]);
            runes.push({ id: runeId, pos: nodeIdx });
        }
        
        return { rows, cols, runes };
    }

    getColor(id) {
        // Generate a distinct color based on the ID using HSL
        const hue = (id * 137.508) % 360; // Golden angle approximation
        return `hsl(${hue}, 70%, 60%)`;
    }

    renderInput(inputStr) {
        const { rows, cols, runes } = this.parseInput(inputStr);
        this.gridRows = rows;
        this.gridCols = cols;
        this.runes = runes;
        
        this.gridContainer.style.gridTemplateColumns = `repeat(${cols}, 40px)`;
        this.gridContainer.innerHTML = '';
        
        // Create cells
        for (let i = 0; i < rows * cols; i++) {
            const cell = document.createElement('div');
            cell.className = 'cell';
            cell.dataset.index = i;
            
            // Add coordinate label
            const coord = document.createElement('div');
            coord.className = 'coord';
            coord.textContent = `${Math.floor(i/cols)},${i%cols}`;
            cell.appendChild(coord);
            
            this.gridContainer.appendChild(cell);
        }
        
        // Place runes
        runes.forEach(rune => {
            const cell = this.gridContainer.children[rune.pos];
            cell.classList.add('rune');
            const color = this.getColor(rune.id);
            cell.style.backgroundColor = color;
            cell.style.boxShadow = `0 0 10px ${color}`;
            cell.style.borderColor = color;
            
            // Keep coord if present
            const coord = cell.querySelector('.coord');
            cell.textContent = rune.id;
            if (coord) cell.appendChild(coord);
            
            // Add hover effect for isolation
            cell.addEventListener('mouseenter', () => this.highlightPath(rune.id));
            cell.addEventListener('mouseleave', () => this.clearHighlight());
        });
        
        this.statusBar.textContent = `Grid: ${rows}x${cols}, Runes: ${runes.length/2}`;
        
        // Reset animation state
        this.stopAnimation();
        this.animationSteps = [];
        this.updateSlider(0, 0);
        
        // Reset metrics
        const metricsPanel = document.getElementById('metrics-panel');
        if (metricsPanel) metricsPanel.style.display = 'none';
        const validationStatus = document.getElementById('validation-status');
        if (validationStatus) validationStatus.remove();
    }

    async runCode() {
        const code = this.editor ? this.editor.getValue() : '';
        this.consoleOutput.textContent = 'Running...';
        
        try {
            const response = await fetch('/api/run', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    code: code,
                    input: this.currentInput,
                    language: this.currentLanguage
                })
            });
            
            const result = await response.json();
            
            if (result.success) {
                this.consoleOutput.textContent = result.output;
                this.renderOutput(result.output);
            } else {
                this.consoleOutput.textContent = result.error;
            }
        } catch (e) {
            this.consoleOutput.textContent = 'Error: ' + e.message;
        }
    }

    showExpected() {
        if (this.currentOutput) {
            this.consoleOutput.textContent = "Expected Output:\n" + this.currentOutput;
            this.renderOutput(this.currentOutput);
        } else {
            this.consoleOutput.textContent = "No expected output available.";
        }
    }

    renderOutput(outputStr) {
        // Clear previous paths
        this.renderInput(this.currentInput);
        
        try {
            const lines = outputStr.trim().split('\n');
            // Filter out empty lines
            const validLines = lines.filter(l => l.trim().length > 0);
            
            if (validLines.length === 0) return;
            
            const numChannels = parseInt(validLines[0].trim());
            const paths = [];
            
            // Prepare animation steps
            this.animationSteps = [];
            let maxSteps = 0;
            
            for (let i = 1; i < validLines.length; i++) {
                const parts = validLines[i].trim().split(/\s+/);
                if (parts.length < 2) continue;
                
                const origin = parseInt(parts[0]);
                const segments = parseInt(parts[1]);
                const directions = parts.slice(2);
                
                paths.push({ origin, directions });
                if (directions.length > maxSteps) maxSteps = directions.length;
            }
            
            // Store paths for interaction
            this.paths = paths;

            // Generate animation frames
            // Frame 0: Just origins (already drawn)
            // Frame k: Draw first k segments of all paths
            for (let step = 0; step <= maxSteps; step++) {
                this.animationSteps.push(step);
            }
            
            // Initialize slider
            this.updateSlider(maxSteps, maxSteps);
            
            // Draw full paths initially
            this.drawAllPaths(maxSteps);

            this.validateSolution(paths, this.runes, this.gridRows, this.gridCols);
            this.calculateAndDisplayMetrics(paths);

        } catch (e) {
            console.error("Error parsing output", e);
            this.consoleOutput.textContent += "\nError parsing output for visualization.";
        }
    }

    drawAllPaths(maxStepLimit) {
        // Clear all path cells first (except runes)
        const cells = this.gridContainer.querySelectorAll('.cell.path');
        cells.forEach(cell => {
            cell.className = 'cell';
            cell.style = '';
            cell.textContent = '';
            // Restore coord if needed
            const coord = document.createElement('div');
            coord.className = 'coord';
            coord.textContent = `${Math.floor(cell.dataset.index/this.gridCols)},${cell.dataset.index%this.gridCols}`;
            cell.appendChild(coord);
        });

        this.paths.forEach(path => {
            this.drawPath(path.origin, path.directions, maxStepLimit);
        });
    }

    validateSolution(paths, runes, rows, cols, returnErrors = false) {
        const errors = [];
        const occupied = new Set();
        const runeMap = new Map(); // pos -> runeId
        const runePairs = new Map(); // runeId -> [pos1, pos2]

        // Populate rune maps
        runes.forEach(r => {
            runeMap.set(r.pos, r.id);
            if (!runePairs.has(r.id)) runePairs.set(r.id, []);
            runePairs.get(r.id).push(r.pos);
        });

        // Check each path
        paths.forEach((path, index) => {
            let currentPos = path.origin;
            const startRuneId = runeMap.get(currentPos);

            if (startRuneId === undefined) {
                errors.push(`Path ${index + 1} starts at non-rune position ${currentPos}.`);
                return;
            }

            // Check if path starts at a rune
            if (occupied.has(currentPos)) {
                 // It's okay to start at a rune, but we track usage to ensure no overlap later?
                 // Actually, runes are obstacles for OTHER paths.
            }

            let pathCells = [currentPos];

            for (const dir of path.directions) {
                const row = Math.floor(currentPos / cols);
                const col = currentPos % cols;
                let nextPos = -1;

                if (dir === 'N') nextPos = (row - 1) * cols + col;
                else if (dir === 'S') nextPos = (row + 1) * cols + col;
                else if (dir === 'E') nextPos = row * cols + (col + 1);
                else if (dir === 'W') nextPos = row * cols + (col - 1);

                // Boundary check
                if (nextPos < 0 || nextPos >= rows * cols || 
                    (dir === 'E' && nextPos % cols === 0) || 
                    (dir === 'W' && (nextPos + 1) % cols === 0)) {
                    errors.push(`Path for Rune ${startRuneId} goes out of bounds.`);
                    break;
                }

                currentPos = nextPos;
                pathCells.push(currentPos);
            }

            const endPos = currentPos;
            const endRuneId = runeMap.get(endPos);

            // Connectivity check
            if (endRuneId !== startRuneId) {
                errors.push(`Path for Rune ${startRuneId} does not end at the matching rune.`);
            }

            // Collision check
            // We add path cells to occupied set. 
            // Note: Start and End are runes, so they can be shared by the SAME path (start/end), 
            // but not by others. Intermediate cells cannot be runes or other paths.
            
            // Let's re-iterate to check collisions against global state
            // We need to be careful: The start and end positions are valid for THIS path, 
            // but intermediate positions must be empty.
            
            for (let i = 1; i < pathCells.length - 1; i++) {
                const pos = pathCells[i];
                if (runeMap.has(pos)) {
                    errors.push(`Path for Rune ${startRuneId} crosses another rune at ${pos}.`);
                }
            }
        });

        // Global collision check (naive but effective for visualization)
        const allPathCells = new Map(); // pos -> runeId
        
        paths.forEach(path => {
            let currentPos = path.origin;
            const runeId = runeMap.get(currentPos);
            if (!runeId) return;

            // Mark start (already marked by rune, but good for tracking path ownership)
            // We don't mark start/end as "path" collisions usually, but let's check intermediate
            
            let pos = currentPos;
            // Don't add start pos to allPathCells to avoid collision with itself or start rune logic
            // Actually, we should add it if we want to prevent other paths from crossing it?
            // Runes are already obstacles. We care about path-path collisions.
            
            for (let i = 0; i < path.directions.length; i++) {
                const dir = path.directions[i];
                const row = Math.floor(pos / cols);
                const col = pos % cols;
                if (dir === 'N') pos = (row - 1) * cols + col;
                else if (dir === 'S') pos = (row + 1) * cols + col;
                else if (dir === 'E') pos = row * cols + (col + 1);
                else if (dir === 'W') pos = row * cols + (col - 1);
                
                if (pos < 0 || pos >= rows * cols) continue; // Already caught

                // If it's the last step (destination rune), we don't count it as a "path cell" that blocks others
                // (though the rune itself blocks others).
                const isDestination = (i === path.directions.length - 1);
                
                if (!isDestination) {
                    if (allPathCells.has(pos)) {
                        const otherId = allPathCells.get(pos);
                        if (otherId !== runeId) {
                            errors.push(`Collision: Path for Rune ${runeId} overlaps with Path for Rune ${otherId} at ${pos}.`);
                        } else {
                            errors.push(`Self-intersection: Path for Rune ${runeId} crosses itself at ${pos}.`);
                        }
                    }
                    allPathCells.set(pos, runeId);
                }
            }
        });

        if (returnErrors) return errors;

        // Display results
        let statusDiv = document.getElementById('validation-status');
        if (!statusDiv) {
            statusDiv = document.createElement('div');
            statusDiv.id = 'validation-status';
            statusDiv.style.padding = '10px';
            statusDiv.style.marginTop = '10px';
            statusDiv.style.borderRadius = '4px';
            this.statusBar.parentNode.insertBefore(statusDiv, this.statusBar.nextSibling);
        }
        
        if (errors.length === 0) {
            statusDiv.style.backgroundColor = '#2e7d32'; // Green
            statusDiv.style.color = '#fff';
            statusDiv.textContent = `✅ Solution Valid! All ${paths.length} paths connected correctly.`;
        } else {
            statusDiv.style.backgroundColor = '#c62828'; // Red
            statusDiv.style.color = '#fff';
            statusDiv.innerHTML = `<strong>❌ Validation Failed:</strong><br>${errors.slice(0, 5).join('<br>')}${errors.length > 5 ? '<br>...' : ''}`;
        }
    }

    calculateAndDisplayMetrics(paths) {
        const metricsPanel = document.getElementById('metrics-panel');
        if (!metricsPanel) return;
        
        metricsPanel.style.display = 'flex';
        
        let totalLength = 0;
        let totalTurns = 0;
        let totalManhattan = 0;
        
        // Calculate metrics
        paths.forEach(path => {
            // Length
            totalLength += path.directions.length;
            
            // Turns
            for (let i = 1; i < path.directions.length; i++) {
                if (path.directions[i] !== path.directions[i-1]) {
                    totalTurns++;
                }
            }
            
            // Manhattan Distance (Optimality)
            // Find start and end coordinates
            const startPos = path.origin;
            let endPos = startPos;
            
            // Simulate path to find end (or we could look up the matching rune)
            // But we need the coordinates.
            const startRow = Math.floor(startPos / this.gridCols);
            const startCol = startPos % this.gridCols;
            
            // We can calculate end pos by following directions
            let currentRow = startRow;
            let currentCol = startCol;
            
            for (const dir of path.directions) {
                if (dir === 'N') currentRow--;
                else if (dir === 'S') currentRow++;
                else if (dir === 'E') currentCol++;
                else if (dir === 'W') currentCol--;
            }
            
            const dist = Math.abs(currentRow - startRow) + Math.abs(currentCol - startCol);
            totalManhattan += dist;
        });
        
        // Update UI
        document.getElementById('metric-length').textContent = totalLength;
        document.getElementById('metric-turns').textContent = totalTurns;
        
        const optimalityEl = document.getElementById('metric-optimality');
        const optimalitySubEl = document.getElementById('metric-optimality-sub');
        
        if (totalManhattan > 0) {
            const ratio = (totalLength / totalManhattan).toFixed(2);
            optimalityEl.textContent = `${ratio}x`;
            optimalitySubEl.textContent = `Min: ${totalManhattan}`;
            
            // Color coding
            if (ratio <= 1.0) optimalityEl.style.color = '#4caf50'; // Perfect
            else if (ratio < 1.2) optimalityEl.style.color = '#8bc34a'; // Good
            else if (ratio < 1.5) optimalityEl.style.color = '#ffeb3b'; // Okay
            else optimalityEl.style.color = '#ff9800'; // Suboptimal
        } else {
            optimalityEl.textContent = '-';
            optimalitySubEl.textContent = '';
        }
    }

    drawPath(origin, directions, maxSteps = Infinity) {
        let currentPos = origin;
        
        // Find rune ID at origin to color the path
        const startRune = this.runes.find(r => r.pos === origin);
        const runeId = startRune ? startRune.id : null;
        const color = runeId ? this.getColor(runeId) : '#ccc';
        
        // Mark start
        // const startCell = this.gridContainer.children[currentPos];
        
        const stepsToDraw = Math.min(directions.length, maxSteps);

        for (let i = 0; i < stepsToDraw; i++) {
            const dir = directions[i];
            let nextPos = currentPos;
            let arrow = '';
            
            const row = Math.floor(currentPos / this.gridCols);
            const col = currentPos % this.gridCols;
            
            if (dir === 'N') {
                nextPos = (row - 1) * this.gridCols + col;
                arrow = '↑';
            } else if (dir === 'S') {
                nextPos = (row + 1) * this.gridCols + col;
                arrow = '↓';
            } else if (dir === 'E') {
                nextPos = row * this.gridCols + (col + 1);
                arrow = '→';
            } else if (dir === 'W') {
                nextPos = row * this.gridCols + (col - 1);
                arrow = '←';
            }
            
            if (nextPos >= 0 && nextPos < this.gridRows * this.gridCols) {
                const cell = this.gridContainer.children[nextPos];
                // Don't overwrite rune styling completely, just add path
                if (!cell.classList.contains('rune')) {
                    cell.classList.add('path');
                    cell.classList.add(`path-id-${runeId}`); // For isolation
                    // Use a slightly transparent version of the color for the path background
                    cell.style.backgroundColor = color.replace(')', ', 0.3)').replace('hsl', 'hsla');
                    cell.style.color = '#fff';
                    cell.textContent = arrow;
                    
                    // Restore coord
                    const coord = document.createElement('div');
                    coord.className = 'coord';
                    coord.textContent = `${Math.floor(nextPos/this.gridCols)},${nextPos%this.gridCols}`;
                    cell.appendChild(coord);
                }
                currentPos = nextPos;
            }
        }
    }

    // Animation & Interaction Methods
    
    updateSlider(max, current) {
        const slider = document.getElementById('anim-slider');
        const counter = document.getElementById('step-counter');
        
        slider.max = max;
        slider.value = current;
        slider.disabled = (max === 0);
        counter.textContent = `${current}/${max}`;
        this.currentStep = current;
    }

    seekAnimation(value) {
        const step = parseInt(value);
        this.currentStep = step;
        this.drawAllPaths(step);
        document.getElementById('step-counter').textContent = `${step}/${document.getElementById('anim-slider').max}`;
    }

    togglePlay() {
        if (this.isPlaying) {
            this.stopAnimation();
        } else {
            this.startAnimation();
        }
    }

    startAnimation() {
        if (this.animationSteps.length === 0) return;
        
        this.isPlaying = true;
        document.getElementById('play-btn').textContent = '⏸';
        
        // If at end, restart
        const slider = document.getElementById('anim-slider');
        if (parseInt(slider.value) >= parseInt(slider.max)) {
            this.seekAnimation(0);
        }

        this.animationTimer = setInterval(() => {
            const slider = document.getElementById('anim-slider');
            let next = parseInt(slider.value) + 1;
            
            if (next > parseInt(slider.max)) {
                this.stopAnimation();
                return;
            }
            
            this.seekAnimation(next);
        }, 200); // 200ms per step
    }

    stopAnimation() {
        this.isPlaying = false;
        document.getElementById('play-btn').textContent = '▶';
        if (this.animationTimer) {
            clearInterval(this.animationTimer);
            this.animationTimer = null;
        }
    }

    toggleCoords(show) {
        if (show) {
            this.gridContainer.querySelectorAll('.cell').forEach(c => c.classList.add('show-coords'));
        } else {
            this.gridContainer.querySelectorAll('.cell').forEach(c => c.classList.remove('show-coords'));
        }
    }

    highlightPath(runeId) {
        this.gridContainer.classList.add('grid-dimmed');
        
        // Highlight runes
        const runes = this.gridContainer.querySelectorAll(`.rune`);
        runes.forEach(r => {
            if (r.textContent == runeId) r.classList.add('highlighted');
        });

        // Highlight path cells
        const paths = this.gridContainer.querySelectorAll(`.path-id-${runeId}`);
        paths.forEach(p => p.classList.add('highlighted'));
    }

    clearHighlight() {
        this.gridContainer.classList.remove('grid-dimmed');
        this.gridContainer.querySelectorAll('.highlighted').forEach(el => el.classList.remove('highlighted'));
    }

    async runAllTests() {
        const modal = document.getElementById('results-modal');
        const tableBody = document.querySelector('#results-table tbody');
        const progressBar = document.querySelector('.progress-fill');
        const statusText = document.getElementById('batch-status');
        
        modal.style.display = 'block';
        tableBody.innerHTML = '';
        progressBar.style.width = '0%';
        statusText.textContent = 'Initializing...';
        
        const code = this.editor ? this.editor.getValue() : '';
        
        // Get all files
        let files = [];
        try {
            const response = await fetch('/api/files');
            files = await response.json();
        } catch (e) {
            statusText.textContent = 'Error fetching file list.';
            return;
        }
        
        let passed = 0;
        let failed = 0;
        
        for (let i = 0; i < files.length; i++) {
            const file = files[i];
            statusText.textContent = `Running ${file} (${i+1}/${files.length})...`;
            progressBar.style.width = `${((i) / files.length) * 100}%`;
            
            const row = document.createElement('tr');
            row.innerHTML = `<td>${file}</td><td>Running...</td><td>-</td><td>-</td>`;
            tableBody.appendChild(row);
            
            const startTime = performance.now();
            
            try {
                // Fetch input first to validate later
                const caseResp = await fetch(`/api/case/${file}`);
                const caseData = await caseResp.json();
                
                // Run code
                const runResp = await fetch('/api/run', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        code: code,
                        input: caseData.input,
                        language: this.currentLanguage
                    })
                });
                
                const result = await runResp.json();
                const endTime = performance.now();
                const duration = (endTime - startTime).toFixed(0) + 'ms';
                
                if (result.success) {
                    // Validate output
                    // We need to parse the input string to get runes for validation
                    // This is a bit hacky: we temporarily set currentInput/runes to validate, then restore?
                    // Or better: refactor validation to take parsed input.
                    // For now, let's just parse locally.
                    
                    const { rows, cols, runes } = this.parseInput(caseData.input);
                    
                    // Parse output paths
                    const parseOutput = (str) => {
                        const lines = str.trim().split('\n');
                        const validLines = lines.filter(l => l.trim().length > 0);
                        const p = [];
                        const map = new Map();
                        if (validLines.length > 0) {
                            for (let k = 1; k < validLines.length; k++) {
                                const parts = validLines[k].trim().split(/\s+/);
                                if (parts.length >= 2) {
                                    const origin = parseInt(parts[0]);
                                    const dirs = parts.slice(2);
                                    p.push({ origin, directions: dirs });
                                    map.set(origin, dirs.join(' '));
                                }
                            }
                        }
                        return { paths: p, map, count: p.length };
                    };

                    const userResult = parseOutput(result.output);
                    const errors = this.validateSolution(userResult.paths, runes, rows, cols, true);
                    
                    if (errors.length === 0) {
                        // Physical validation passed. Now check against expected output.
                        let matchStatus = 'PASS';
                        let message = 'All paths valid';
                        let statusClass = 'status-pass';

                        if (caseData.output && caseData.output.trim().length > 0) {
                            const expectedResult = parseOutput(caseData.output);
                            
                            if (userResult.count !== expectedResult.count) {
                                matchStatus = 'FAIL';
                                message = `Count mismatch: Got ${userResult.count}, Expected ${expectedResult.count}`;
                                statusClass = 'status-fail';
                                failed++;
                            } else {
                                // Check if paths match
                                let mismatch = false;
                                for (const [origin, dirStr] of userResult.map) {
                                    if (expectedResult.map.get(origin) !== dirStr) {
                                        mismatch = true;
                                        break;
                                    }
                                }
                                
                                if (mismatch) {
                                    matchStatus = 'FAIL';
                                    message = 'Valid paths, but mismatch with expected output';
                                    statusClass = 'status-fail';
                                    failed++;
                                } else {
                                    passed++;
                                }
                            }
                        } else {
                            // No expected output to compare
                            message = 'Valid (No expected output)';
                            passed++;
                        }

                        row.innerHTML = `<td>${file}</td><td class="${statusClass}">${matchStatus}</td><td>${duration}</td><td>${message}</td>`;
                    } else {
                        row.innerHTML = `<td>${file}</td><td class="status-fail">FAIL</td><td>${duration}</td><td>${errors[0]}</td>`;
                        failed++;
                    }
                    
                } else {
                    row.innerHTML = `<td>${file}</td><td class="status-error">ERROR</td><td>${duration}</td><td>${result.error.split('\n')[0]}</td>`;
                    failed++;
                }
                
            } catch (e) {
                row.innerHTML = `<td>${file}</td><td class="status-error">Sys Error</td><td>-</td><td>${e.message}</td>`;
                failed++;
            }
            
            // Scroll to bottom
            const modalContent = document.querySelector('.modal-content');
            // modalContent.scrollTop = modalContent.scrollHeight;
        }
        
        progressBar.style.width = '100%';
        statusText.textContent = `Completed: ${passed} Passed, ${failed} Failed.`;
    }

    // --- Editor Methods ---

    toggleEditMode() {
        this.isEditing = !this.isEditing;
        const controls = document.getElementById('editor-controls');
        const fileList = document.getElementById('file-list');
        const btn = document.getElementById('mode-toggle-btn');
        
        if (this.isEditing) {
            controls.classList.remove('hidden');
            fileList.style.display = 'none';
            btn.classList.add('active');
            btn.style.backgroundColor = '#0e639c';
            this.gridContainer.classList.add('editing');
            this.initEditorGrid();
            this.statusBar.textContent = 'Editor Mode: Click cells to place runes.';
        } else {
            controls.classList.add('hidden');
            fileList.style.display = 'block';
            btn.classList.remove('active');
            btn.style.backgroundColor = '';
            this.gridContainer.classList.remove('editing');
            this.statusBar.textContent = 'Viewer Mode';
            // Reload current file if exists, or clear
            if (this.currentInput) {
                this.renderInput(this.currentInput);
            } else {
                this.gridContainer.innerHTML = '';
            }
        }
    }

    initEditorGrid() {
        if (!this.isEditing) return;
        
        const rows = parseInt(document.getElementById('edit-rows').value) || 10;
        const cols = parseInt(document.getElementById('edit-cols').value) || 10;
        
        this.editorGrid.rows = rows;
        this.editorGrid.cols = cols;
        this.editorGrid.runes.clear();
        
        this.gridContainer.style.gridTemplateColumns = `repeat(${cols}, 40px)`;
        this.gridContainer.innerHTML = '';
        
        for (let i = 0; i < rows * cols; i++) {
            const cell = document.createElement('div');
            cell.className = 'cell';
            cell.dataset.index = i;
            
            const coord = document.createElement('div');
            coord.className = 'coord';
            coord.textContent = `${Math.floor(i/cols)},${i%cols}`;
            cell.appendChild(coord);
            
            cell.addEventListener('click', () => this.handleEditorClick(i));
            
            this.gridContainer.appendChild(cell);
        }
    }

    handleEditorClick(index) {
        if (!this.isEditing) return;
        
        const cell = this.gridContainer.children[index];
        const currentRuneId = parseInt(document.getElementById('rune-id').value) || 1;
        
        if (this.editorGrid.runes.has(index)) {
            // Remove existing
            this.editorGrid.runes.delete(index);
            cell.classList.remove('rune');
            cell.style.backgroundColor = '';
            cell.style.boxShadow = '';
            cell.style.borderColor = '';
            
            // Restore coord text only
            const coordText = `${Math.floor(index/this.editorGrid.cols)},${index%this.editorGrid.cols}`;
            const coordDiv = cell.querySelector('.coord');
            cell.textContent = '';
            if (coordDiv) {
                coordDiv.textContent = coordText;
                cell.appendChild(coordDiv);
            } else {
                // Recreate coord if lost (shouldn't happen but safe)
                const newCoord = document.createElement('div');
                newCoord.className = 'coord';
                newCoord.textContent = coordText;
                cell.appendChild(newCoord);
            }
        } else {
            // Add new
            this.editorGrid.runes.set(index, currentRuneId);
            cell.classList.add('rune');
            const color = this.getColor(currentRuneId);
            cell.style.backgroundColor = color;
            cell.style.boxShadow = `0 0 10px ${color}`;
            cell.style.borderColor = color;
            
            const coordDiv = cell.querySelector('.coord');
            cell.textContent = currentRuneId;
            if (coordDiv) cell.appendChild(coordDiv);
        }
    }

    updateRunePreview() {
        const id = parseInt(document.getElementById('rune-id').value) || 1;
        const color = this.getColor(id);
        document.getElementById('rune-preview').style.backgroundColor = color;
    }

    async saveLevel() {
        const rows = this.editorGrid.rows;
        const cols = this.editorGrid.cols;
        const runes = this.editorGrid.runes;
        
        // Validation: Check pairs
        const counts = new Map();
        for (const id of runes.values()) {
            counts.set(id, (counts.get(id) || 0) + 1);
        }
        
        for (const [id, count] of counts) {
            if (count !== 2) {
                alert(`Error: Rune ID ${id} appears ${count} times. Must appear exactly twice.`);
                return;
            }
        }
        
        if (runes.size === 0) {
            alert("Grid is empty!");
            return;
        }

        // Format Output
        // <rows> <cols> <n_defs> <rune1> <node1> <rune2> <node2> ...
        // The 3rd number is the total number of rune definitions (endpoints), not pairs.
        const n_defs = runes.size;
        let output = `${rows} ${cols} ${n_defs}`;
        
        // Group by ID to output pairs together (optional but cleaner)
        const pairs = new Map(); // id -> [pos1, pos2]
        for (const [pos, id] of runes) {
            if (!pairs.has(id)) pairs.set(id, []);
            pairs.get(id).push(pos);
        }
        
        for (const [id, positions] of pairs) {
            output += ` ${id} ${positions[0]} ${id} ${positions[1]}`;
        }
        
        const filename = document.getElementById('level-name').value || 'custom_level';
        
        try {
            const response = await fetch('/api/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    filename: filename,
                    content: output
                })
            });
            
            const result = await response.json();
            if (result.success) {
                alert(`Level saved as ${result.filename}`);
                this.loadFileList(); // Refresh list
            } else {
                alert('Error saving level: ' + result.error);
            }
        } catch (e) {
            alert('System error: ' + e.message);
        }
    }
}

new Visualizer();
