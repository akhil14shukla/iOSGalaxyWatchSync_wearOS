#!/bin/bash

# Health Data Sync Server Setup and Test Script

echo "🚀 Setting up Health Data Sync Server..."

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js v16 or later."
    echo "   Download from: https://nodejs.org/"
    exit 1
fi

echo "✅ Node.js found: $(node --version)"

# Navigate to local-server directory
cd local-server || exit 1

# Install dependencies
echo "📦 Installing dependencies..."
npm install

if [ $? -ne 0 ]; then
    echo "❌ Failed to install dependencies"
    exit 1
fi

echo "✅ Dependencies installed successfully"

# Start the server in background
echo "🔥 Starting server..."
node server.js &
SERVER_PID=$!

# Wait a moment for server to start
sleep 3

# Test server endpoints
echo "🧪 Testing server endpoints..."

# Test health check
echo "Testing health check endpoint..."
HEALTH_RESPONSE=$(curl -s http://localhost:3000/api/v1/health)
if [[ $HEALTH_RESPONSE == *"healthy"* ]]; then
    echo "✅ Health check: PASSED"
else
    echo "❌ Health check: FAILED"
fi

# Test sync endpoint with sample data
echo "Testing sync endpoint..."
SYNC_DATA='{
  "device_id": "test_galaxy_watch_123",
  "last_sync_timestamp": 0,
  "data": [
    {
      "id": "test_entry_1",
      "timestamp": '$(date +%s)'000',
      "type": "daily_metrics",
      "data": {
        "steps": 10000,
        "calories": 2500,
        "distance": 8000
      },
      "source": "Galaxy Watch Test"
    }
  ]
}'

SYNC_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$SYNC_DATA" \
  http://localhost:3000/api/v1/data)

if [[ $SYNC_RESPONSE == *"success"* ]]; then
    echo "✅ Sync endpoint: PASSED"
else
    echo "❌ Sync endpoint: FAILED"
fi

# Test data retrieval
echo "Testing data retrieval endpoint..."
GET_RESPONSE=$(curl -s "http://localhost:3000/api/v1/data?device_id=test_galaxy_watch_123&since=0")
if [[ $GET_RESPONSE == *"success"* ]]; then
    echo "✅ Data retrieval: PASSED"
else
    echo "❌ Data retrieval: FAILED"
fi

# Test statistics endpoint
echo "Testing statistics endpoint..."
STATS_RESPONSE=$(curl -s http://localhost:3000/api/v1/stats)
if [[ $STATS_RESPONSE == *"total_entries"* ]]; then
    echo "✅ Statistics endpoint: PASSED"
else
    echo "❌ Statistics endpoint: FAILED"
fi

echo ""
echo "🎉 Server testing completed!"
echo ""
echo "📍 Local Server URLs:"
echo "   Health Check: http://localhost:3000/api/v1/health"
echo "   Statistics: http://localhost:3000/api/v1/stats"
echo "   API Base: http://localhost:3000/api/v1/"
echo ""
echo "🌐 Network Access:"

# Get local IP address
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "Unable to determine")
if [ "$LOCAL_IP" != "Unable to determine" ]; then
    echo "   External: http://$LOCAL_IP:3000"
    echo ""
    echo "📱 Use this URL in your Wear OS app:"
    echo "   viewModel.setServerUrl(\"http://$LOCAL_IP:3000\")"
else
    echo "   Unable to determine local IP address"
fi

echo ""
echo "🛑 To stop the server: kill $SERVER_PID"
echo "📝 Server logs are visible in the console"
echo ""

# Keep script running to show server logs
wait $SERVER_PID
