#!/usr/bin/env bash
# GIS-Agent 后端启动脚本（含 JVM 堆配置，避免推理模型长输出 + 向量化触发 OOM）
# 用法: ./start.sh            # 使用默认堆
#       JAVA_OPTS="-Xmx4g" ./start.sh   # 自定义堆（如仍 OOM 可调大）
set -e
cd "$(dirname "$0")"
JAR=target/gis-agent-platform-0.1.0-SNAPSHOT.jar
if [ ! -f "$JAR" ]; then
  echo "未找到 $JAR，请先执行: mvn -o package -DskipTests"
  exit 1
fi
# 默认堆 2g；推理模型(如 deepseek-v4-pro)输出很长，内存紧张可调大 -Xmx。
# -XX:+HeapDumpOnOutOfMemoryError 会在再次 OOM 时生成 heapdump.hprof 便于定位。
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx2g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof}"
exec java $JAVA_OPTS -jar "$JAR" "$@"
