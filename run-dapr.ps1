# Run Dapr Workflow Service with Dapr Sidecar
# Prerequisites:
#   1. Dapr CLI installed: dapr --version
#   2. Dapr initialized: dapr init
#   3. Ollama running with qwen2.5:3b model

Write-Host "Building application..." -ForegroundColor Cyan
.\gradlew.bat build -x test

Write-Host "`nStarting Dapr Workflow Service..." -ForegroundColor Green

dapr run `
    --app-id dapr-workflow-service `
    --app-port 8081 `
    --dapr-http-port 3500 `
    --dapr-grpc-port 50001 `
    --components-path ./dapr/components `
    --config ./dapr/config.yaml `
    --log-level info `
    -- java --enable-preview -jar ./build/libs/dapr-agent-workflow-service.jar

