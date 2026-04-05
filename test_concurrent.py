import requests
import time
import concurrent.futures

BASE_URL = "http://192.168.110.140:9999"

# 只读请求列表
read_apis = [
    ("/api/ping", "GET"),
    ("/api/sys/info", "GET"),
    ("/api/app/list", "GET"),
    ("/api/app/current", "GET"),
]

print("=== 测试并发只读请求 ===")

# 并发执行
start = time.time()
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
    futures = []
    for api, method in read_apis:
        url = f"{BASE_URL}{api}"
        if method == "GET":
            futures.append(executor.submit(requests.get, url, timeout=5))
        else:
            futures.append(executor.submit(requests.post, url, timeout=5))
    
    for i, future in enumerate(concurrent.futures.as_completed(futures)):
        try:
            r = future.result()
            print(f"API {i+1}: {r.status_code} - {len(r.text)} bytes")
        except Exception as e:
            print(f"API {i+1}: Error - {e}")

elapsed_concurrent = time.time() - start
print(f"\n并发耗时: {elapsed_concurrent:.2f}s")

# 串行执行
print("\n=== 测试串行只读请求 ===")
start = time.time()
for api, method in read_apis:
    url = f"{BASE_URL}{api}"
    try:
        if method == "GET":
            r = requests.get(url, timeout=5)
        else:
            r = requests.post(url, timeout=5)
        print(f"{api}: {r.status_code}")
    except Exception as e:
        print(f"{api}: Error - {e}")

elapsed_sequential = time.time() - start
print(f"\n串行耗时: {elapsed_sequential:.2f}s")
print(f"\n并发加速比: {elapsed_sequential/elapsed_concurrent:.2f}x")
