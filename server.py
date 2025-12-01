import os
import subprocess
import tempfile
from flask import Flask, request, jsonify, send_from_directory

app = Flask(__name__, static_folder='static', static_url_path='')

# Configuration
INPUT_DIR = r'c:\Users\stefa\Downloads\tests-hard\tests\input'
OUTPUT_DIR = r'c:\Users\stefa\Downloads\tests-hard\tests\output'
WORKING_DIR = os.path.dirname(os.path.abspath(__file__))

@app.route('/')
def index():
    return send_from_directory('static', 'index.html')

@app.route('/api/files')
def list_files():
    try:
        # List all .txt files, not just those starting with 'input'
        files = sorted([f for f in os.listdir(INPUT_DIR) if f.endswith('.txt')])
        return jsonify(files)
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/case/<filename>')
def get_case(filename):
    try:
        input_path = os.path.join(INPUT_DIR, filename)
        output_filename = filename.replace('input', 'output')
        output_path = os.path.join(OUTPUT_DIR, output_filename)
        
        with open(input_path, 'r') as f:
            input_data = f.read()
            
        output_data = ""
        if os.path.exists(output_path):
            with open(output_path, 'r') as f:
                output_data = f.read()
                
        return jsonify({
            'input': input_data,
            'output': output_data
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/save', methods=['POST'])
def save_level():
    try:
        data = request.json
        filename = data.get('filename')
        content = data.get('content')
        
        if not filename or not content:
            return jsonify({'error': 'Missing filename or content'}), 400
            
        if not filename.endswith('.txt'):
            filename += '.txt'
            
        file_path = os.path.join(INPUT_DIR, filename)
        
        with open(file_path, 'w') as f:
            f.write(content)
            
        return jsonify({'success': True, 'filename': filename})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/run', methods=['POST'])
def run_code():
    data = request.json
    code = data.get('code')
    input_data = data.get('input')
    language = data.get('language', 'java')  # Default to java
    
    if not code:
        return jsonify({'error': 'No code provided'}), 400

    try:
        if language == 'java':
            return run_java(code, input_data)
        elif language == 'python':
            return run_python(code, input_data)
        elif language == 'cpp':
            return run_cpp(code, input_data)
        else:
            return jsonify({'error': f'Unsupported language: {language}'}), 400
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)})

def run_java(code, input_data):
    with tempfile.TemporaryDirectory() as temp_dir:
        java_file = os.path.join(temp_dir, 'Solution.java')
        
        with open(java_file, 'w') as f:
            f.write(code)
            
        compile_process = subprocess.run(['javac', java_file], capture_output=True, text=True)
        if compile_process.returncode != 0:
            return jsonify({'success': False, 'error': 'Compilation Error:\n' + compile_process.stderr})
            
        try:
            run_process = subprocess.run(
                ['java', '-cp', temp_dir, 'Solution'], 
                input=input_data, 
                capture_output=True, 
                text=True, 
                timeout=5
            )
            if run_process.returncode != 0:
                 return jsonify({'success': False, 'error': 'Runtime Error:\n' + run_process.stderr})
            return jsonify({'success': True, 'output': run_process.stdout})
        except subprocess.TimeoutExpired:
            return jsonify({'success': False, 'error': 'Time Limit Exceeded'})

def run_python(code, input_data):
    with tempfile.TemporaryDirectory() as temp_dir:
        py_file = os.path.join(temp_dir, 'solution.py')
        
        with open(py_file, 'w') as f:
            f.write(code)
            
        try:
            # Use 'python' or 'python3' depending on environment. Assuming 'python' for Windows.
            run_process = subprocess.run(
                ['python', py_file], 
                input=input_data, 
                capture_output=True, 
                text=True, 
                timeout=5
            )
            if run_process.returncode != 0:
                 return jsonify({'success': False, 'error': 'Runtime Error:\n' + run_process.stderr})
            return jsonify({'success': True, 'output': run_process.stdout})
        except subprocess.TimeoutExpired:
            return jsonify({'success': False, 'error': 'Time Limit Exceeded'})

def run_cpp(code, input_data):
    with tempfile.TemporaryDirectory() as temp_dir:
        cpp_file = os.path.join(temp_dir, 'solution.cpp')
        exe_file = os.path.join(temp_dir, 'solution.exe')
        
        with open(cpp_file, 'w') as f:
            f.write(code)
            
        # Compile with g++
        compile_process = subprocess.run(['g++', cpp_file, '-o', exe_file], capture_output=True, text=True)
        if compile_process.returncode != 0:
            return jsonify({'success': False, 'error': 'Compilation Error:\n' + compile_process.stderr})
            
        try:
            run_process = subprocess.run(
                [exe_file], 
                input=input_data, 
                capture_output=True, 
                text=True, 
                timeout=5
            )
            if run_process.returncode != 0:
                 return jsonify({'success': False, 'error': 'Runtime Error:\n' + run_process.stderr})
            return jsonify({'success': True, 'output': run_process.stdout})
        except subprocess.TimeoutExpired:
            return jsonify({'success': False, 'error': 'Time Limit Exceeded'})


if __name__ == '__main__':
    app.run(debug=True, port=5000)
