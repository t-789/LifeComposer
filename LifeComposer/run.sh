#!/bin/bash
# LifeComposer - Safe Local Startup Script
#
# This script starts the LifeComposer backend with local Ollama defaults.
# All LLM use cases default to disabled (mock/fallback mode).
# To use cloud LLMs, set the corresponding environment variables before running.
#
# Per-use-case configuration (set before running this script):
#
#   LLM_QA_PROVIDER=ollama       # Provider: ollama, deepseek, qwen, glm, openai
#   LLM_QA_BASE_URL=http://localhost:11434/v1
#   LLM_QA_MODEL=lfm2.5:8b
#   LLM_QA_API_KEY_ENV=          # Name of env var holding API key
#   LLM_QA_ENABLED=false
#
#   LLM_PLANNING_PROVIDER=ollama
#   LLM_PLANNING_BASE_URL=http://localhost:11434/v1
#   LLM_PLANNING_MODEL=lfm2.5:8b
#   LLM_PLANNING_API_KEY_ENV=
#   LLM_PLANNING_ENABLED=false
#
#   LLM_PROFILE_PROVIDER=ollama
#   LLM_PROFILE_BASE_URL=http://localhost:11434/v1
#   LLM_PROFILE_MODEL=lfm2.5:8b
#   LLM_PROFILE_API_KEY_ENV=
#   LLM_PROFILE_ENABLED=false
#
#   LLM_SQL_PROVIDER=ollama
#   LLM_SQL_BASE_URL=http://localhost:11434/v1
#   LLM_SQL_MODEL=lfm2.5:8b
#   LLM_SQL_API_KEY_ENV=
#   LLM_SQL_ENABLED=false
#
#   LLM_CHAT_PROVIDER=ollama
#   LLM_CHAT_BASE_URL=http://localhost:11434/v1
#   LLM_CHAT_MODEL=lfm2.5:8b
#   LLM_CHAT_API_KEY_ENV=
#   LLM_CHAT_ENABLED=true
#
# Examples:
#   # Use DeepSeek for Q&A:
#   export LLM_QA_PROVIDER=deepseek
#   export LLM_QA_BASE_URL=https://api.deepseek.com/v1
#   export LLM_QA_MODEL=deepseek-chat
#   export LLM_QA_API_KEY_ENV=DEEPSEEK_API_KEY
#   export LLM_QA_ENABLED=true
#   export DEEPSEEK_API_KEY=your-api-key-here
#   ./run.sh
#
#   # Use all local Ollama defaults (current):
#   ./run.sh

cd "$(dirname "$0")" || exit 1

echo "=== LifeComposer Backend ==="
echo "LLM Configuration:"
echo "  QA:       ${LLM_QA_PROVIDER:-ollama} / ${LLM_QA_MODEL:-lfm2.5:8b} (${LLM_QA_ENABLED:-false})"
echo "  Planning: ${LLM_PLANNING_PROVIDER:-ollama} / ${LLM_PLANNING_MODEL:-lfm2.5:8b} (${LLM_PLANNING_ENABLED:-false})"
echo "  Profile:  ${LLM_PROFILE_PROVIDER:-ollama} / ${LLM_PROFILE_MODEL:-lfm2.5:8b} (${LLM_PROFILE_ENABLED:-false})"
echo "  SQL:      ${LLM_SQL_PROVIDER:-ollama} / ${LLM_SQL_MODEL:-lfm2.5:8b} (${LLM_SQL_ENABLED:-false})"
echo "  Chat:     ${LLM_CHAT_PROVIDER:-ollama} / ${LLM_CHAT_MODEL:-lfm2.5:8b} (${LLM_CHAT_ENABLED:-true})"
echo ""

# Inject LLM useCase config as Spring system properties so the
# LlmConfig @ConfigurationProperties(prefix=llm) reads env vars
JVM_ARGS=""
for uc in qa planning profile sql chat; do
    UC_UPPER=$(echo "$uc" | tr 'a-z' 'A-Z')
    
    prov_var="LLM_${UC_UPPER}_PROVIDER"
    base_url_var="LLM_${UC_UPPER}_BASE_URL"
    model_var="LLM_${UC_UPPER}_MODEL"
    enabled_var="LLM_${UC_UPPER}_ENABLED"
    
    prov=${!prov_var:-ollama}
    base_url=${!base_url_var:-http://localhost:11434/v1}
    model=${!model_var:-lfm2.5:8b}
    
    # chat defaults to enabled; others default to disabled
    if [ "$uc" = "chat" ]; then
        enabled=${!enabled_var:-true}
    else
        enabled=${!enabled_var:-false}
    fi
    
    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.provider=$prov"
    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.baseUrl=$base_url"
    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.model=$model"
    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.enabled=$enabled"
done

./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="$JVM_ARGS"