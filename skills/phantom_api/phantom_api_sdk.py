#!/usr/bin/env python3
"""
PhantomAPI Python SDK v2.0
高性能客户端库，支持连接池、缓存、重试

GitHub: https://github.com/232252/PhantomAPI
"""

import json
import time
from typing import Optional, Dict, Any, List, Callable
from dataclasses import dataclass
from functools import wraps

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


@dataclass
class CacheEntry:
    """缓存条目"""
    data: Any
    timestamp: float
    ttl: int


def cached(ttl: int = 30):
    """缓存装饰器"""
    def decorator(func):
        cache: Dict[str, CacheEntry] = {}
        
        @wraps(func)
        def wrapper(self, *args, **kwargs):
            # 生成缓存键
            key = f"{func.__name__}:{args}:{kwargs}"
            now = time.time()
            
            # 检查缓存
            if key in cache:
                entry = cache[key]
                if now - entry.timestamp < entry.ttl:
                    return entry.data
            
            # 调用函数
            result = func(self, *args, **kwargs)
            
            # 更新缓存
            cache[key] = CacheEntry(data=result, timestamp=now, ttl=ttl)
            
            return result
        return wrapper
    return decorator


class PhantomAPIError(Exception):
    """PhantomAPI 错误"""
    pass


class PhantomAPI:
    """
    PhantomAPI 客户端
    
    示例:
        api = PhantomAPI("192.168.110.140")
        
        # 测试连接
        api.ping()
        
        # 获取 UI 树
        tree = api.ui_tree()
        
        # 点击并等待
        api.tap(540, 300, wait_text="搜索结果", timeout_ms=5000)
    """
    
    def __init__(
        self, 
        host: str = "192.168.110.140", 
        port: int = 9999,
        timeout: float = 5.0,
        max_retries: int = 3,
        cache_ttl: int = 30
    ):
        """
        初始化客户端
        
        Args:
            host: 设备 IP
            port: HTTP 端口
            timeout: 请求超时时间 (秒)
            max_retries: 最大重试次数
            cache_ttl: 默认缓存时间 (秒)
        """
        self.base_url = f"http://{host}:{port}"
        self.timeout = timeout
        self.cache_ttl = cache_ttl
        
        # 创建带重试的 session
        self.session = requests.Session()
        
        retry_strategy = Retry(
            total=max_retries,
            backoff_factor=0.1,
            status_forcelist=[500, 502, 503, 504],
            allowed_methods=["GET", "POST"]
        )
        
        adapter = HTTPAdapter(max_retries=retry_strategy)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)
        
        # 缓存
        self._cache: Dict[str, CacheEntry] = {}
    
    def _get_cached(self, key: str, ttl: int) -> Optional[Any]:
        """获取缓存"""
        if key in self._cache:
            entry = self._cache[key]
            if time.time() - entry.timestamp < ttl:
                return entry.data
        return None
    
    def _set_cache(self, key: str, data: Any):
        """设置缓存"""
        self._cache[key] = CacheEntry(data=data, timestamp=time.time(), ttl=self.cache_ttl)
    
    def _request(self, method: str, endpoint: str, data: Optional[Dict] = None) -> Dict:
        """发送请求"""
        url = f"{self.base_url}{endpoint}"
        
        try:
            if method == "GET":
                response = self.session.get(url, timeout=self.timeout)
            else:
                response = self.session.post(url, json=data, timeout=self.timeout)
            
            response.raise_for_status()
            return response.json()
        
        except requests.Timeout:
            raise PhantomAPIError(f"请求超时: {endpoint}")
        except requests.RequestException as e:
            raise PhantomAPIError(f"请求失败: {e}")
    
    # ==================== 系统域 ====================
    
    def ping(self) -> Dict:
        """测试连接"""
        return self._request("GET", "/api/ping")
    
    @cached(ttl=300)  # 5 分钟缓存
    def device_info(self) -> Dict:
        """获取设备信息"""
        return self._request("GET", "/api/sys/info")
    
    def foreground_app(self) -> Dict:
        """获取前台应用"""
        return self._request("GET", "/api/sys/foreground")
    
    @cached(ttl=60)  # 1 分钟缓存
    def packages(self, limit: int = 50) -> List[Dict]:
        """获取已安装应用列表"""
        result = self._request("GET", "/api/sys/packages")
        packages = result.get("packages", [])
        return packages[:limit] if limit else packages
    
    # ==================== UI 域 ====================
    
    def ui_tree(self) -> Dict:
        """获取 UI 树"""
        return self._request("GET", "/api/ui/tree")
    
    def find_node(self, text: str) -> Dict:
        """查找节点"""
        return self._request("GET", f"/api/ui/find?text={text}")
    
    def tap(
        self, 
        x: int, 
        y: int, 
        wait_text: Optional[str] = None,
        timeout_ms: int = 5000,
        interval_ms: int = 200
    ) -> Dict:
        """
        点击坐标
        
        Args:
            x: X 坐标
            y: Y 坐标
            wait_text: 等待出现的文本
            timeout_ms: 超时时间 (毫秒)
            interval_ms: 检查间隔 (毫秒)
        """
        body = {"x": x, "y": y}
        
        if wait_text:
            body["wait"] = {
                "condition": {"type": "text", "text": wait_text},
                "timeout_ms": timeout_ms,
                "interval_ms": interval_ms
            }
        
        return self._request("POST", "/api/ui/tap", body)
    
    def swipe(
        self, 
        start_x: int, 
        start_y: int, 
        end_x: int, 
        end_y: int, 
        duration: int = 300
    ) -> Dict:
        """
        滑动
        
        Args:
            start_x: 起点 X
            start_y: 起点 Y
            end_x: 终点 X
            end_y: 终点 Y
            duration: 持续时间 (毫秒)
        """
        return self._request("POST", "/api/ui/swipe", {
            "startX": start_x,
            "startY": start_y,
            "endX": end_x,
            "endY": end_y,
            "duration": duration
        })
    
    def back(self) -> Dict:
        """返回键"""
        return self._request("POST", "/api/ui/back", {})
    
    def home(self) -> Dict:
        """主页键"""
        return self._request("POST", "/api/ui/home", {})
    
    def wait_for_text(self, text: str, timeout_ms: int = 5000) -> Dict:
        """等待文本出现"""
        return self._request("POST", "/api/ui/wait", {
            "condition": {"type": "text", "text": text},
            "timeout_ms": timeout_ms
        })
    
    # ==================== Web 域 ====================
    
    def web_detect(self) -> Dict:
        """检测 WebView 状态"""
        return self._request("GET", "/api/web/detect")
    
    def web_dom(self) -> Dict:
        """获取 WebView DOM"""
        return self._request("GET", "/api/web/dom")
    
    def web_find(self, text: str) -> Dict:
        """查找 WebView 元素"""
        return self._request("GET", f"/api/web/find?text={text}")
    
    def web_click(
        self, 
        x: Optional[int] = None, 
        y: Optional[int] = None,
        text: Optional[str] = None,
        index: Optional[int] = None
    ) -> Dict:
        """
        WebView 点击
        
        Args:
            x: X 坐标
            y: Y 坐标
            text: 文本
            index: 索引
        """
        data = {}
        if x is not None and y is not None:
            data["x"] = x
            data["y"] = y
        elif text:
            data["text"] = text
        elif index is not None:
            data["index"] = index
        
        return self._request("POST", "/api/web/click", data)
    
    # ==================== Chrome CDP 域 ====================
    
    def cdp_pages(self) -> Dict:
        """获取 Chrome 页面列表"""
        return self._request("GET", "/api/cdp/pages")
    
    def cdp_title(self) -> Dict:
        """获取 Chrome 页面标题"""
        return self._request("GET", "/api/cdp/title")
    
    # ==================== 网络域 ====================
    
    def net_status(self) -> Dict:
        """获取网络状态"""
        return self._request("GET", "/api/net/status")
    
    def net_wifi(self) -> Dict:
        """获取 WiFi 信息"""
        return self._request("GET", "/api/net/wifi")
    
    def net_connections(self, limit: int = 20) -> List[Dict]:
        """获取网络连接"""
        result = self._request("GET", "/api/net/connections")
        connections = result.get("connections", [])
        return connections[:limit]
    
    def net_traffic(self) -> Dict:
        """获取流量统计"""
        return self._request("GET", "/api/net/traffic")
    
    # ==================== 辅助域 ====================
    
    def scope_apps(self) -> Dict:
        """获取需要启用作用域的应用"""
        return self._request("GET", "/api/scope/apps")
    
    # ==================== 高级功能 ====================
    
    def smart_tap(
        self, 
        find_text: str, 
        tap_x: int, 
        tap_y: int,
        wait_timeout_ms: int = 5000
    ) -> Dict:
        """
        智能点击：点击后等待指定文本出现
        
        Args:
            find_text: 等待出现的文本
            tap_x: 点击 X 坐标
            tap_y: 点击 Y 坐标
            wait_timeout_ms: 等待超时
        """
        return self.tap(tap_x, tap_y, wait_text=find_text, timeout_ms=wait_timeout_ms)
    
    def batch_tap(self, coordinates: List[tuple], interval: float = 0.5) -> List[Dict]:
        """
        批量点击
        
        Args:
            coordinates: 坐标列表 [(x1, y1), (x2, y2), ...]
            interval: 点击间隔 (秒)
        """
        results = []
        for x, y in coordinates:
            result = self.tap(x, y)
            results.append(result)
            if interval > 0:
                time.sleep(interval)
        return results
    
    def scroll_up(self, duration: int = 300) -> Dict:
        """向上滚动"""
        return self.swipe(540, 1500, 540, 500, duration)
    
    def scroll_down(self, duration: int = 300) -> Dict:
        """向下滚动"""
        return self.swipe(540, 500, 540, 1500, duration)
    
    def close(self):
        """关闭连接"""
        self.session.close()
    
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


# ==================== 便捷函数 ====================

def create_client(host: str = "192.168.110.140", **kwargs) -> PhantomAPI:
    """创建客户端"""
    return PhantomAPI(host=host, **kwargs)


# ==================== 使用示例 ====================

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="PhantomAPI Python SDK")
    parser.add_argument("--host", default="192.168.110.140", help="设备 IP")
    parser.add_argument("--command", default="ping", help="命令")
    args = parser.parse_args()
    
    api = PhantomAPI(args.host)
    
    print(f"测试连接: {args.host}")
    result = api.ping()
    print(json.dumps(result, indent=2, ensure_ascii=False))
    
    print("\n设备信息:")
    info = api.device_info()
    print(json.dumps(info, indent=2, ensure_ascii=False))
