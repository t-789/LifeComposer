#!/bin/bash
# stop_run.sh - 优雅停止 /bin/bash ./run.sh 进程

set -u

TARGET_PATTERN='^/bin/bash \./run\.sh$'   # 精确匹配命令行
WAIT_SECONDS=10

# 查找目标进程 PID
pids=$(pgrep -f "$TARGET_PATTERN" || true)

if [ -z "$pids" ]; then
    echo "未找到目标进程: /bin/bash ./run.sh"
    exit 0
fi

echo "找到目标进程 PID: $pids"

# 向每个进程所在的进程组发送 SIGINT
for pid in $pids; do
    pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')
    if [ -z "$pgid" ]; then
        echo "无法获取 PID $pid 的进程组 ID，跳过"
        continue
    fi
    echo "向进程组 $pgid (PID $pid) 发送 SIGINT ..."
    kill -INT -- -"$pgid" 2>/dev/null || true
done

# 等待一段时间
sleep "$WAIT_SECONDS"

# 检查是否还有残留
remaining=$(pgrep -f "$TARGET_PATTERN" || true)
if [ -z "$remaining" ]; then
    echo "进程已成功退出。"
    exit 0
fi

echo "仍有进程存在，尝试发送 SIGTERM ..."
for pid in $remaining; do
    pgid=$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')
    if [ -n "$pgid" ]; then
        kill -TERM -- -"$pgid" 2>/dev/null || true
    fi
done

sleep 10

# 再次检查
if pgrep -f "$TARGET_PATTERN" >/dev/null; then
    echo "进程仍未退出，可能需要手动检查或使用 SIGKILL："
    echo "  kill -9 -- -<PGID>"
    exit 1
else
    echo "进程已成功退出。"
    exit 0
fi
