#!/bin/bash

# Comprehensive Test Script for Hybrid Sync System
# Tests both the WearOS app and the local server

echo "🚀 Starting Hybrid Sync System Tests"
echo "======================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print test result
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ PASSED${NC}: $2"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}❌ FAILED${NC}: $2"
        ((TESTS_FAILED++))
    fi
}

# Function to test HTTP endpoint
test_endpoint() {
    local url=$1
    local description=$2
    local expected_content=$3
    
    echo -e "${BLUE}Testing${NC}: $description"
    response=$(curl -s "$url")
    
    if [[ $response == *"$expected_content"* ]]; then
        print_result 0 "$description"
        return 0
    else
        print_result 1 "$description"
        echo "Expected: $expected_content"
        echo "Got: $response"
        return 1
    fi
}

# 1. Test Local Server Health
echo -e "\n${YELLOW}1. Local Server Tests${NC}"
echo "------------------------"

SERVER_URL="http://localhost:3000"

# Test health endpoint
test_endpoint "$SERVER_URL/api/v1/health" "Server Health Check" "healthy"

# Test statistics endpoint
test_endpoint "$SERVER_URL/api/v1/stats" "Server Statistics" "total_entries"

# 2. Test Data Sync Endpoints
echo -e "\n${YELLOW}2. Data Sync Tests${NC}"
echo "-------------------"

# Test data posting
echo -e "${BLUE}Testing${NC}: Data Upload"
SYNC_DATA=$(cat <<'EOF'
{
  "device_id": "test_hybrid_sync_123",
  "last_sync_timestamp": 0,
  "data": [
    {
      "id": "hybrid_test_entry_1",
      "timestamp": 1753547500000,
      "type": "daily_metrics",
      "data": {
        "steps": 15000,
        "calories": 3000,
        "distance": 12000,
        "activeMinutes": 90
      },
      "source": "Galaxy Watch Hybrid Test"
    },
    {
      "id": "hybrid_test_entry_2", 
      "timestamp": 1753547500000,
      "type": "heart_rate",
      "data": {
        "bpm": 75,
        "timestamp": 1753547500000
      },
      "source": "Galaxy Watch Hybrid Test"
    }
  ]
}
EOF
)

response=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$SYNC_DATA" \
  "$SERVER_URL/api/v1/data")

if [[ $response == *"success"* ]] && [[ $response == *"2"* ]]; then
    print_result 0 "Data Upload (2 entries)"
else
    print_result 1 "Data Upload"
    echo "Response: $response"
fi

# Test data retrieval
echo -e "${BLUE}Testing${NC}: Data Retrieval"
response=$(curl -s "$SERVER_URL/api/v1/data?device_id=test_hybrid_sync_123&since=0")

if [[ $response == *"success"* ]] && [[ $response == *"hybrid_test_entry_1"* ]]; then
    print_result 0 "Data Retrieval"
else
    print_result 1 "Data Retrieval"
    echo "Response: $response"
fi

# 3. Test Error Handling
echo -e "\n${YELLOW}3. Error Handling Tests${NC}"
echo "------------------------"

# Test invalid data format
echo -e "${BLUE}Testing${NC}: Invalid Data Format"
response=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"invalid": "data"}' \
  "$SERVER_URL/api/v1/data")

if [[ $response == *"Invalid request format"* ]]; then
    print_result 0 "Invalid Data Format Handling"
else
    print_result 1 "Invalid Data Format Handling"
fi

# 4. Test Network Configuration
echo -e "\n${YELLOW}4. Network Configuration Tests${NC}"
echo "--------------------------------"

# Get local IP address
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")

echo -e "${BLUE}Local Server URLs:${NC}"
echo "  - Localhost: http://localhost:3000"
echo "  - Network: http://$LOCAL_IP:3000"
echo ""

# Test network accessibility
if [ "$LOCAL_IP" != "127.0.0.1" ]; then
    test_endpoint "http://$LOCAL_IP:3000/api/v1/health" "Network Access Health Check" "healthy"
else
    echo -e "${YELLOW}⚠️  Could not determine local IP address${NC}"
fi

# 5. Test APK Build Status
echo -e "\n${YELLOW}5. Android App Build Tests${NC}"
echo "----------------------------"

if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_SIZE=$(stat -f%z "app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null || stat -c%s "app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null)
    print_result 0 "APK Build (${APK_SIZE} bytes)"
else
    print_result 1 "APK Build"
fi

# 6. Test Server Performance
echo -e "\n${YELLOW}6. Performance Tests${NC}"
echo "---------------------"

# Test response time
echo -e "${BLUE}Testing${NC}: Server Response Time"
start_time=$(date +%s%N)
curl -s "$SERVER_URL/api/v1/health" > /dev/null
end_time=$(date +%s%N)
duration=$(( (end_time - start_time) / 1000000 )) # Convert to milliseconds

if [ $duration -lt 1000 ]; then
    print_result 0 "Server Response Time (${duration}ms)"
else
    print_result 1 "Server Response Time (${duration}ms - too slow)"
fi

# Test concurrent requests
echo -e "${BLUE}Testing${NC}: Concurrent Requests"
for i in {1..5}; do
    curl -s "$SERVER_URL/api/v1/health" > /dev/null &
done
wait

# Check if server is still responsive after concurrent requests
test_endpoint "$SERVER_URL/api/v1/health" "Server Stability After Concurrent Requests" "healthy"

# 7. Final Summary
echo -e "\n${YELLOW}7. Test Summary${NC}"
echo "=================="

TOTAL_TESTS=$((TESTS_PASSED + TESTS_FAILED))
PASS_RATE=$(( TESTS_PASSED * 100 / TOTAL_TESTS ))

echo -e "Total Tests: $TOTAL_TESTS"
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"
echo -e "Pass Rate: $PASS_RATE%"

echo ""
echo -e "${BLUE}📱 WearOS App Configuration:${NC}"
echo "  - Update server URL in app: viewModel.setServerUrl(\"http://$LOCAL_IP:3000\")"
echo "  - Install APK: adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""

echo -e "${BLUE}🔗 Integration Testing:${NC}"
echo "  1. Install the APK on a WearOS device/emulator"
echo "  2. Grant all permissions in the app"
echo "  3. Configure the server URL to your local IP"
echo "  4. Test data sync functionality"
echo "  5. Verify data appears in the server logs"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed! The hybrid sync system is ready for integration testing.${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Please review the issues above.${NC}"
    exit 1
fi
