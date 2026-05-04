#!/usr/bin/env bash
# 使用 FFmpeg 将测试彩条推至 RTMP（用于验证 SRS/云端收流地址）。
# 使用：填写 RTMP_URL 后 chmod +x 并执行。

set -euo pipefail

# 例：rtmp://127.0.0.1:1935/live/demo
: "${RTMP_URL:?请设置环境变量 RTMP_URL}"

ffmpeg -re -f lavfi -i testsrc=size=1280x720:rate=30 \
  -f lavfi -i sine=frequency=1000:sample_rate=44100 \
  -c:v libx264 -preset veryfast -tune zerolatency -pix_fmt yuv420p \
  -c:a aac -b:a 128k -shortest \
  -f flv "$RTMP_URL"
