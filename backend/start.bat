@echo off
chcp 65001 >nul
cd /d %~dp0
set JAR=target\gis-agent-platform-0.1.0-SNAPSHOT.jar
if not exist "%JAR%" (
  echo 未找到 %JAR%，请先执行: mvn -o package -DskipTests
  pause
  exit /b 1
)
rem 默认堆 2g；推理模型(如 deepseek-v4-pro)输出很长，内存紧张可调大 -Xmx。
rem -XX:+HeapDumpOnOutOfMemoryError 会在再次 OOM 时生成 heapdump.hprof 便于定位。
set JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof
java %JAVA_OPTS% -jar %JAR% %*
