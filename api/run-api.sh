#!/bin/bash
# Start the Flask API server
cd "$(dirname "$0")"
pip install -r requirements.txt
python app.py
