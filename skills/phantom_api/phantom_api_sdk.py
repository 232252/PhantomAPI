#!/usr/bin/env python3
"""
PhantomAPI Python SDK v3.0
高性能客户端库，支持链式调用、批量操作、自动重试

GitHub: https://github.com/232252/PhantomAPI
"""

import json
import time
from typing import Optional, Dict, Any, List, Tuple, Union
from functools import wraps

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


class PhantomAPIError(Exception):
    """PhantomAPI 错误"""
    pass


def retry(times: int = 3, delay: float = 0.5):
    """重试装饰器"""
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            last_error = None
            for i in range(times):
                try:
                    result = func(*args, **kwargs)
                    if result.get('success', True):
                        return result
                    last_error = result.get('error', 'Unknown error')
                except Exception as e:
                    last_error = str(e)
                if i < times - 1:
                    time.sleep(delay)
            raise PhantomAPIError(f"Failed after {times} retries: {last_error}")
        return wrapper
    return decorator


class PhantomAPI:
    """
    PhantomAPI 客户端
    
    示例:
        api = PhantomAPI("192.168.110.140")
        api.ping()
        api.tap_and_wait("签到", "签到成功")
        coords = api.scroll_find("目标文字")
    """
    
    def __init__(self, host: str = "192.168.110.140", port: int = 9999,
                 timeout: float = 5.0, max_retries: int = 3):
        self.base_url = f"http://{host}:{port}"
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({'Connection': 'keep-alive'})
        
        retry_strategy = Retry(total=max_retries, backoff_factor=0.1,
                              status_forcelist=[500, 502, 503, 504])
        adapter = HTTPAdapter(max_retries=retry_strategy)
        self.session.mount("http://", adapter)
    
    def _get(self, endpoint: str) -> Dict:
        resp = self.session.get(f"{self.base_url}{endpoint}", timeout=self.timeout)
        try:
            return resp.json()
        except:
            return {"success": False, "error": "Empty response", "status_code": resp.status_code}
    
    def _post(self, endpoint: str, data: Dict) -> Dict:
        resp = self.session.post(f"{self.base_url}{endpoint}", json=data, timeout=self.timeout)
        try:
            return resp.json()
        except:
            return {"success": False, "error": "Empty response", "status_code": resp.status_code}
    
    # ===== 基础 =====
    def ping(self) -> Dict: return self._get("/api/ping")
    def device_info(self) -> Dict: return self._get("/api/sys/info")
    def foreground(self) -> Dict: return self._get("/api/sys/foreground")
    def activity(self) -> Dict: return self._get("/api/sys/activity")
    def packages(self) -> Dict: return self._get("/api/sys/packages")
    
    # ===== UI =====
    def ui_tree(self) -> Dict: return self._get("/api/ui/tree")
    
    def find(self, text: str = None, id: str = None, clickable: bool = False, upward: int = 3) -> Dict:
        params = []
        if text: params.append(f"text={text}")
        if id: params.append(f"id={id}")
        if clickable: params.append("clickable=true")
        params.append(f"upward={upward}")
        return self._get(f"/api/ui/find?{'&'.join(params)}")
    
    def find_coords(self, text: str, clickable: bool = True) -> Optional[Tuple[int, int]]:
        result = self.find(text=text, clickable=clickable)
        nodes = result.get('nodes', [])
        if nodes:
            b = nodes[0].get('bounds', {})
            return (b.get('centerX'), b.get('centerY'))
        return None
    
    def find_all_coords(self, text: str, clickable: bool = True) -> List[Tuple[int, int]]:
        result = self.find(text=text, clickable=clickable)
        return [(n['bounds']['centerX'], n['bounds']['centerY']) 
                for n in result.get('nodes', []) if 'bounds' in n]
    
    @retry(times=3)
    def tap(self, x: int = None, y: int = None, text: str = None, clickable: bool = True, upward: int = 3) -> Dict:
        if text:
            return self._post("/api/ui/tap", {"text": text, "clickable": clickable, "upward": upward})
        return self._post("/api/ui/tap", {"x": x, "y": y})
    
    def swipe(self, sx: int, sy: int, ex: int, ey: int, duration: int = 300) -> Dict:
        return self._post("/api/ui/swipe", {"startX": sx, "startY": sy, "endX": ex, "endY": ey, "duration": duration})
    
    def swipe_up(self, distance: int = 800):
        return self.swipe(540, 1500, 540, 1500 - distance)
    
    def swipe_down(self, distance: int = 800):
        return self.swipe(540, 500, 540, 500 + distance)
    
    def back(self) -> Dict: return self._post("/api/ui/back", {})
    def home(self) -> Dict: return self._get("/api/ui/home")
    
    def wait(self, text: str, mode: str = "text_appear", timeout: int = 5000, interval: int = 300) -> Dict:
        return self._post("/api/ui/wait", {"mode": mode, "text": text, "timeout_ms": timeout, "interval_ms": interval})
    
    def wait_appear(self, text: str, timeout: int = 5000) -> bool:
        return self.wait(text, "text_appear", timeout).get('success', False)
    
    def wait_disappear(self, text: str, timeout: int = 5000) -> bool:
        return self.wait(text, "text_disappear", timeout).get('success', False)
    
    def action(self, action: str, text: str = None, id: str = None, wait_after: int = 0) -> Dict:
        body = {"action": action}
        if text: body["text"] = text
        if id: body["id"] = id
        if wait_after: body["wait_ms_after"] = wait_after
        return self._post("/api/ui/action", body)
    
    def safe_back(self, package: str, max_tries: int = 5) -> Dict:
        return self._post("/api/ui/safe_back", {"check_package": package, "max_tries": max_tries})
    
    # ===== Web =====
    def web_detect(self) -> Dict: return self._get("/api/web/detect")
    def web_dom(self) -> Union[List, Dict]: return self._get("/api/web/dom")
    def web_find(self, text: str) -> Dict: return self._get(f"/api/web/find?text={text}")
    def web_click(self, x: int = None, y: int = None, text: str = None) -> Dict:
        if text: return self._post("/api/web/click", {"text": text})
        return self._post("/api/web/click", {"x": x, "y": y})
    
    # ===== Network =====
    def net_status(self) -> Dict: return self._get("/api/net/status")
    def net_wifi(self) -> Dict: return self._get("/api/net/wifi")
    def net_connections(self, package: str = None) -> Dict:
        if package: return self._get(f"/api/net/connections?package={package}")
        return self._get("/api/net/connections")
    def net_traffic(self) -> Dict: return self._get("/api/net/traffic")
    
    # ===== 高级操作 =====
    def tap_and_wait(self, text: str, wait_text: str, timeout: int = 5000) -> bool:
        """点击并等待结果"""
        self.tap(text=text, clickable=True)
        return self.wait_appear(wait_text, timeout)
    
    def scroll_find(self, text: str, max_swipes: int = 5, direction: str = "up") -> Optional[Tuple[int, int]]:
        """滚动查找"""
        swipe_fn = self.swipe_up if direction == "up" else self.swipe_down
        for _ in range(max_swipes + 1):
            coords = self.find_coords(text)
            if coords: return coords
            swipe_fn()
            time.sleep(0.5)
        return None
    
    def scroll_and_tap(self, text: str, max_swipes: int = 5) -> bool:
        """滚动查找并点击"""
        coords = self.scroll_find(text, max_swipes)
        if coords:
            self.tap(*coords)
            return True
        return False
    
    def batch_tap(self, coords: List[Tuple[int, int]], interval: float = 0.3) -> List[Dict]:
        """批量点击"""
        results = []
        for x, y in coords:
            results.append(self.tap(x, y))
            time.sleep(interval)
        return results
    
    def smart_back_to(self, target_activity: str, max_tries: int = 10) -> bool:
        """返回到指定Activity"""
        for _ in range(max_tries):
            current = self.activity()
            if target_activity in current.get('activity', ''):
                return True
            self.back()
            time.sleep(0.3)
        return False
    
    def start_app(self, package: str) -> Dict:
        """启动应用"""
        import subprocess
        result = subprocess.run(
            ['adb', 'shell', 'monkey', '-p', package, '-c', 'android.intent.category.LAUNCHER', '1'],
            capture_output=True, text=True
        )
        return {"success": result.returncode == 0}
    
    def is_app_running(self, package: str) -> bool:
        """检查应用是否在前台"""
        current = self.foreground()
        return package in current.get('packageName', '')


# 使用示例
if __name__ == "__main__":
    api = PhantomAPI()
    
    print("Ping:", api.ping())
    print("Device:", api.device_info().get('model'))
    
    # 点击并等待
    if api.tap_and_wait("签到", "签到成功"):
        print("签到成功！")
    
    # 滚动查找
    coords = api.scroll_find("目标文字", max_swipes=5)
    if coords:
        api.tap(*coords)
    
    # 批量点击
    api.batch_tap([(100, 200), (300, 400), (500, 600)])
