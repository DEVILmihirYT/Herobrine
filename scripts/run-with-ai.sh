#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ENV_FILE="${HEROBRINE_AI_ENV:-$HOME/.config/herobrine-ai.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing AI secret file: $ENV_FILE"
  echo "Create it first and keep it OUTSIDE the Git repository."
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

required_vars=(
  GROQ_API_KEY
  GEMINI_CHAT_API_KEY
  GEMINI_VOICE_API_KEY
)

for name in "${required_vars[@]}"; do
  if [ -z "${!name:-}" ]; then
    echo "Missing $name in $ENV_FILE"
    exit 1
  fi
done

echo "AI credentials loaded from $ENV_FILE"
echo "Starting Herobrine with Groq + Gemini configuration..."

exec ./gradlew runServer
