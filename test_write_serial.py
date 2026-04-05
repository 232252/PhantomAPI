import requests
import time
import concurrent.futures
import threading

BASE_URL = "http://192.168.110.140:9999"

print("=== 测试并发写操作（应该串行执行）===")

results = []
lock = threading.Lock()

def do_tap(x, y, idx):
    start = time.time()
    try:
        r = requests.post(f"{BASE_URL}/api/gesture/tap", json={"x": x, "y": y}, timeout=10)
        elapsed = time.time() - start
        with lock:
            results.append((idx, elapsed, r.json()))
        print(f"请求 {idx}: 完成 ({elapsed:.2f}s)")
    except Exception as e:
        with lock:
            results.append((idx, -1, str(e)))
        print(f"请求 {idx}: 错误 - {e}")

# 同时发送 3 个 tap 请求
start = time.time()
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
    futures = []
    for i in range(3):
        futures.append(executor.submit(do_tap, 540, 1200, i+1))
    concurrent.futures.wait(futures)

total = time.time() - start
print(f"\n总耗时: {total:.2f}s")
print(f"结果: {results}")

print("\n=== 测试读写混合 ===")

def read_op(idx):
    try:
        r = requests.get(f"{BASE_URL}/api/ping", timeout=5)
        print(f"读操作 {idx}: {r.status_code}")
    except Exception as e:
        print(f"读操作 {idx}: 错误 - {e}")

def write_op(idx):
    try:
        r = requests.post(f"{BASE_URL}/api/gesture/tap", json={"x": 540, "y": 1000}, timeout=10)
        print(f"写操作 {idx}: {r.json()}")
    except Exception as e:
        print(f"写操作 {idx}: 错误 - {e}")

# 混合发送
print("\n同时发送 2 读 + 2 写操作...")
start = time.time()
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
    executor.submit(read_op, 1)
    executor.submit(write_op, 1)
    executor.submit(read_op, 2)
    executor.submit(write_op, 2)

print(f"混合操作总耗时: {time.time() - start:.2f}s")
