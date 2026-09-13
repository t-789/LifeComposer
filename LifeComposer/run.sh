#!/bin/bash
# LifeComposer - Safe Startup Script
#
# Starts the backend with DeepSeek deepseek-flash defaults. Loads a local .env
# file if present (copy from .env.example). QA and Chat are enabled by default;
# /api/chat/* never falls back to mock content.
#
# Per-use-case configuration (set in .env or export before running):
#
#   LLM_QA_PROVIDER=deepseek
#   LLM_QA_BASE_URL=https://api.deepseek.com/v1
#   LLM_QA_MODEL=deepseek-flash
#   LLM_QA_API_KEY_ENV=DEEPSEEK_API_KEY   # Name of env var holding API key
#   LLM_QA_ENABLED=true
#
#   LLM_PLANNING_PROVIDER=deepseek
#   LLM_PLANNING_BASE_URL=https://api.deepseek.com/v1
#   LLM_PLANNING_MODEL=deepseek-flash
#   LLM_PLANNING_API_KEY_ENV=DEEPSEEK_API_KEY
#   LLM_PLANNING_ENABLED=false
#
#   LLM_PROFILE_PROVIDER=deepseek
#   LLM_PROFILE_BASE_URL=https://api.deepseek.com/v1
#   LLM_PROFILE_MODEL=deepseek-flash
#   LLM_PROFILE_API_KEY_ENV=DEEPSEEK_API_KEY
#   LLM_PROFILE_ENABLED=false
#
#   LLM_SQL_PROVIDER=deepseek
#   LLM_SQL_BASE_URL=https://api.deepseek.com/v1
#   LLM_SQL_MODEL=deepseek-flash
#   LLM_SQL_API_KEY_ENV=DEEPSEEK_API_KEY
#   LLM_SQL_ENABLED=false
#
#   LLM_CHAT_PROVIDER=deepseek
#   LLM_CHAT_BASE_URL=https://api.deepseek.com/v1
#   LLM_CHAT_MODEL=deepseek-flash
#   LLM_CHAT_API_KEY_ENV=DEEPSEEK_API_KEY
#   LLM_CHAT_ENABLED=true
#
# Setup:
#   cp .env.example .env
#   # then add your DeepSeek API key to .env or export it:
#   export DEEPSEEK_API_KEY=your-api-key-here
#   ./run.sh

cd "$(dirname "$0")" || exit 1

# Load local environment variables
if [ -f .env ]; then
    set -a
    source .env
    set +a
fi

echo "=== LifeComposer Backend ==="
echo "LLM Configuration:"
echo "  QA:       ${LLM_QA_PROVIDER:-deepseek} / ${LLM_QA_MODEL:-deepseek-flash} (${LLM_QA_ENABLED:-true})"
echo "  Planning: ${LLM_PLANNING_PROVIDER:-deepseek} / ${LLM_PLANNING_MODEL:-deepseek-flash} (${LLM_PLANNING_ENABLED:-false})"
echo "  Profile:  ${LLM_PROFILE_PROVIDER:-deepseek} / ${LLM_PROFILE_MODEL:-deepseek-flash} (${LLM_PROFILE_ENABLED:-false})"
echo "  SQL:      ${LLM_SQL_PROVIDER:-deepseek} / ${LLM_SQL_MODEL:-deepseek-flash} (${LLM_SQL_ENABLED:-false})"
echo "  Chat:     ${LLM_CHAT_PROVIDER:-deepseek} / ${LLM_CHAT_MODEL:-deepseek-flash} (${LLM_CHAT_ENABLED:-true})"
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
    api_key_env_var="LLM_${UC_UPPER}_API_KEY_ENV"

    prov=${!prov_var:-deepseek}
    base_url=${!base_url_var:-https://api.deepseek.com/v1}
    model=${!model_var:-deepseek-flash}
    api_key_env=${!api_key_env_var:-DEEPSEEK_API_KEY}

    # qa and chat default to enabled; others default to disabled
    if [ "$uc" = "chat" ] || [ "$uc" = "qa" ]; then
        enabled=${!enabled_var:-true}
    else
        enabled=${!enabled_var:-false}
    fi

    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.provider=$prov"
    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.baseUrl=$base_url"
    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.model=$model"
    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.enabled=$enabled"
    JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.apiKeyEnv=$api_key_env"
done

./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="$JVM_ARGS"
