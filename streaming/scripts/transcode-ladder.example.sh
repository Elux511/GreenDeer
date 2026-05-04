#!/usr/bin/env bash
# 多档位转码仅作「思路示例」：生产环境常在云端转码或独立转码节点完成。
# 需根据输入源、机器性能调整；农村场景可优先 480p/30fps 低码率档。

set -euo pipefail

: "${INPUT_URL:?例如 rtmp://... 或 本地文件路径}"
: "${OUTPUT_DIR:?输出目录}"

mkdir -p "$OUTPUT_DIR"

ffmpeg -i "$INPUT_URL" \
  -filter_complex "[0:v]split=3[v1][v2][v3]; \
    [v1]scale=854:480:flags=bicubic[out480]; \
    [v2]scale=1280:720:flags=bicubic[out720]; \
    [v3]scale=1920:1080:flags=bicubic[out1080]" \
  -map "[out480]" -c:v:0 libx264 -b:v:0 800k -maxrate:v:0 900k -bufsize:v:0 1200k \
  -map "[out720]" -c:v:1 libx264 -b:v:1 2000k -maxrate:v:1 2200k -bufsize:v:1 3000k \
  -map "[out1080]" -c:v:2 libx264 -b:v:2 4500k -maxrate:v:2 5000k -bufsize:v:2 6000k \
  -map 0:a? -c:a aac -b:a 128k \
  -f hls -hls_time 2 -hls_list_size 6 -hls_flags delete_segments \
  -var_stream_map "v:0,a:0 v:1,a:0 v:2,a:0" \
  -master_pl_name master.m3u8 \
  "$OUTPUT_DIR/stream_%v.m3u8"
