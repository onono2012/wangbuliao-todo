#!/usr/bin/env python3
"""忘不了 在线更新服务器（参考实现）

用法: python3 ota_server.py [目录] [端口] [监听地址]
  目录      放 wbl.json 与新 APK 的目录（默认: 当前目录）
  端口      监听端口（默认: 8788）
  监听地址  默认 127.0.0.1（仅本机/同设备回环测试）

⚠️ App 的 network_security_config 仅放行 127.0.0.1/localhost 的明文 http。
   - 同设备自测: 保持默认 127.0.0.1 即可（E2E 验证即此方案）。
   - 局域网/公网真分发: 必须走 https（明文 http 会被 Android 9+ 拒绝）；
     如确需局域网明文测试，需在 network_security_config.xml 增加对应
     domain 后同钥重建 APK。
每个请求会打印访问日志到 stdout。
"""
import http.server
import socketserver
import sys
import os
import datetime

DIR = sys.argv[1] if len(sys.argv) > 1 else '.'
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8788
HOST = sys.argv[3] if len(sys.argv) > 3 else '127.0.0.1'
os.chdir(DIR)


class H(http.server.SimpleHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print('%s %s %s' % (
            datetime.datetime.now().strftime('%m-%d %H:%M:%S'),
            self.address_string(), fmt % args), flush=True)


socketserver.TCPServer.allow_reuse_address = True
with socketserver.ThreadingTCPServer((HOST, PORT), H) as httpd:
    print('Serving %s on %s:%d' % (os.getcwd(), HOST, PORT), flush=True)
    httpd.serve_forever()
