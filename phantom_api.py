#!/usr/bin/env python3
"""
PhantomAPI - Python 简化版
直接在 Android 设备上运行的 HTTP API 服务
"""

import os
import sys
import json
import subprocess
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import xml.etree.ElementTree as ET
import re

VERSION = "1.0.0"
PORT = 9999

def shell(cmd):
    """执行 shell 命令"""
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)
        return result.stdout + result.stderr
    except:
        return ""

def get_local_ip():
    """获取本机 IP"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "127.0.0.1"

class PhantomAPIHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        print(f"[{self.log_date_time_string()}] {format % args}")
    
    def send_json(self, data, status=200):
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False, indent=2).encode('utf-8'))
    
    def send_error_json(self, message, status=400):
        self.send_json({'status': 'error', 'error': message}, status)
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        params = parse_qs(parsed.query)
        
        try:
            if path in ['/', '/api']:
                self.send_json({
                    'name': 'PhantomAPI', 'version': VERSION,
                    'docs': '/api/docs', 'ping': '/api/ping'
                })
            elif path == '/api/ping':
                self.send_json({'status': 'ok', 'timestamp': int(time.time() * 1000)})
            elif path == '/api/sys/info':
                self.handle_sys_info()
            elif path == '/api/sys/foreground':
                self.handle_sys_foreground()
            elif path == '/api/sys/packages':
                self.handle_sys_packages()
            elif path == '/api/ui/tree':
                self.handle_ui_tree()
            elif path == '/api/ui/find':
                self.handle_ui_find(params)
            elif path == '/api/ui/back':
                self.handle_ui_back()
            elif path == '/api/ui/home':
                self.handle_ui_home()
            elif path == '/api/web/detect':
                self.handle_web_detect()
            elif path == '/api/net/connections':
                self.handle_net_connections()
            else:
                self.send_error_json(f'Unknown: {path}', 404)
        except Exception as e:
            self.send_error_json(str(e), 500)
    
    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8') if content_length > 0 else '{}'
        try: data = json.loads(body)
        except: data = {}
        
        try:
            if path == '/api/ui/tap':
                self.handle_ui_tap(data)
            elif path == '/api/ui/swipe':
                self.handle_ui_swipe(data)
            elif path == '/api/ui/input':
                self.handle_ui_input(data)
            else:
                self.send_error_json(f'Unknown: {path}', 404)
        except Exception as e:
            self.send_error_json(str(e), 500)
    
    def handle_sys_info(self):
        info = {
            'model': shell('getprop ro.product.model').strip(),
            'brand': shell('getprop ro.product.brand').strip(),
            'sdk': shell('getprop ro.build.version.sdk').strip(),
            'release': shell('getprop ro.build.version.release').strip(),
            'ip': get_local_ip()
        }
        size = shell('wm size')
        if 'Physical size:' in size:
            info['screen'] = size.split('Physical size:')[1].strip()
        self.send_json({'status': 'success', 'data': info})
    
    def handle_sys_foreground(self):
        result = shell('dumpsys activity activities | grep mResumedActivity')
        pkg = 'unknown'
        if '/' in result:
            try: pkg = result.split()[1].split('/')[0]
            except: pass
        self.send_json({'status': 'success', 'data': {'packageName': pkg}})
    
    def handle_sys_packages(self):
        result = shell('pm list packages -3')
        pkgs = [l.replace('package:', '').strip() for l in result.split('\n') if l.startswith('package:')]
        self.send_json({'status': 'success', 'data': {'count': len(pkgs), 'packages': pkgs[:50]}})
    
    def handle_ui_tree(self):
        shell('uiautomator dump /sdcard/window_dump.xml')
        xml = shell('cat /sdcard/window_dump.xml')
        if not xml:
            self.send_error_json('无法获取 UI 树')
            return
        try:
            root = ET.fromstring(xml)
            tree = self._parse_node(root)
            self.send_json({'status': 'success', 'data': tree})
        except Exception as e:
            self.send_json({'status': 'success', 'data': {'raw': xml[:3000], 'error': str(e)}})
    
    def _parse_node(self, node, depth=0):
        result = {
            'depth': depth, 'class': node.attrib.get('class', ''),
            'text': node.attrib.get('text', ''), 'bounds': node.attrib.get('bounds', ''),
            'clickable': node.attrib.get('clickable', 'false') == 'true'
        }
        children = [self._parse_node(c, depth+1) for c in node]
        if children: result['children'] = children
        return result
    
    def handle_ui_find(self, params):
        text = params.get('text', [''])[0]
        if not text:
            self.send_error_json('需要 text 参数')
            return
        shell('uiautomator dump /sdcard/window_dump.xml')
        xml = shell('cat /sdcard/window_dump.xml')
        nodes = []
        if xml:
            try:
                root = ET.fromstring(xml)
                self._find_nodes(root, text.lower(), nodes)
            except: pass
        self.send_json({'status': 'success', 'data': {'count': len(nodes), 'nodes': nodes}})
    
    def _find_nodes(self, node, text, result):
        t = node.attrib.get('text', '').lower()
        d = node.attrib.get('content-desc', '').lower()
        if text in t or text in d:
            bounds = node.attrib.get('bounds', '[0,0][0,0]')
            m = re.findall(r'\[(\d+),(\d+)\]', bounds)
            cx, cy = 0, 0
            if len(m) == 2:
                cx = (int(m[0][0]) + int(m[1][0])) // 2
                cy = (int(m[0][1]) + int(m[1][1])) // 2
            result.append({'text': node.attrib.get('text', ''), 'bounds': bounds, 'x': cx, 'y': cy})
        for c in node: self._find_nodes(c, text, result)
    
    def handle_ui_tap(self, data):
        x = data.get('x', 0)
        y = data.get('y', 0)
        result = shell(f'input tap {x} {y}')
        self.send_json({'status': 'success', 'data': {'action': 'tap', 'x': x, 'y': y}})
    
    def handle_ui_swipe(self, data):
        sx = data.get('startX', 0)
        sy = data.get('startY', 0)
        ex = data.get('endX', 0)
        ey = data.get('endY', 0)
        d = data.get('duration', 300)
        shell(f'input swipe {sx} {sy} {ex} {ey} {d}')
        self.send_json({'status': 'success', 'data': {'action': 'swipe'}})
    
    def handle_ui_input(self, data):
        text = data.get('text', '')
        shell(f'input text "{text}"')
        self.send_json({'status': 'success', 'data': {'action': 'input', 'text': text}})
    
    def handle_ui_back(self):
        shell('input keyevent KEYCODE_BACK')
        self.send_json({'status': 'success', 'data': {'action': 'back'}})
    
    def handle_ui_home(self):
        shell('input keyevent KEYCODE_HOME')
        self.send_json({'status': 'success', 'data': {'action': 'home'}})
    
    def handle_web_detect(self):
        sockets = shell('cat /proc/net/unix | grep webview_devtools')
        result = []
        for line in sockets.split('\n'):
            if 'webview_devtools_remote' in line:
                parts = line.split()
                if len(parts) >= 8:
                    result.append({'socket': parts[-1], 'available': True})
        self.send_json({'status': 'success', 'data': {'webviews': result}})
    
    def handle_net_connections(self):
        tcp = shell('cat /proc/net/tcp')
        connections = []
        for line in tcp.split('\n')[1:20]:  # 限制数量
            parts = line.split()
            if len(parts) >= 8:
                connections.append({
                    'local': parts[1], 'remote': parts[2], 'uid': parts[7]
                })
        self.send_json({'status': 'success', 'data': {'connections': connections}})

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else PORT
    ip = get_local_ip()
    print(f"PhantomAPI v{VERSION}")
    print(f"服务地址: http://{ip}:{port}")
    print(f"API 文档: http://{ip}:{port}/api/docs")
    print("按 Ctrl+C 停止服务")
    
    server = HTTPServer(('0.0.0.0', port), PhantomAPIHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n服务已停止")
        server.shutdown()
