#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
killcap OCR 服务端
支持 ddddocr 和本地大模型两种识别方式
"""

import base64
import json
import os
import re
import sys
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs
from io import BytesIO

import requests


# ==================== 配置 ====================

# OCR 模式: "ddddocr" 或 "ai"
# ddddocr: 本地识别，速度快，准确率一般
# ai: 调用本地大模型，速度慢，准确率高
OCR_MODE = "ddddocr"

# AI 模型配置（当 OCR_MODE="ai" 时生效）
AI_API_URL = "http://127.0.0.1:1234/v1/chat/completions"
AI_API_KEY = "sk-lm-xxx"
AI_MODEL = "qwen/qwen2.5-vl-7b"

# 服务配置
HOST = "0.0.0.0"
PORT = 8899

# ==================== 初始化 ====================

# ddddocr 初始化
ocr = None
if OCR_MODE == "ddddocr":
    try:
        import ddddocr
        ocr = ddddocr.DdddOcr(show_ad=False)
        print("[*] ddddocr 初始化成功")
    except ImportError:
        print("[!] ddddocr 未安装，尝试切换到 AI 模式")
        print("[!] 安装命令: pip3 install ddddocr")
        OCR_MODE = "ai"

# AI 模式检查
if OCR_MODE == "ai":
    if AI_API_KEY == "sk-lm-xxx":
        print("[!] 请配置 AI_API_KEY")
        print("[!] 或者切换到 ddddocr 模式: OCR_MODE = \"ddddocr\"")

# 创建 temp 目录
os.makedirs('temp', exist_ok=True)
with open('temp/log.txt', 'w') as f:
    pass


# ==================== 工具函数 ====================

def compress_image(image_data, max_width=150):
    """压缩图片减少 token"""
    try:
        from PIL import Image
        img = Image.open(BytesIO(image_data))
        if img.width > max_width:
            ratio = max_width / img.width
            img = img.resize((max_width, int(img.height * ratio)), Image.Resampling.LANCZOS)
        buffer = BytesIO()
        img.convert('RGB').save(buffer, format='JPEG', quality=40)
        return buffer.getvalue()
    except:
        return image_data


def extract_image_from_response(response):
    """从响应中提取图片"""
    try:
        ct = response.headers.get('Content-Type', '')
        if 'json' in ct or response.text.strip().startswith('{'):
            data = response.json()
            img = data.get('img', '')
            if img:
                if ',' in img:
                    img = img.split(',', 1)[1]
                return base64.b64decode(img)
        return response.content
    except:
        return response.content


def evaluate_expression(text):
    """计算数学表达式"""
    try:
        text = text.strip()
        print(f"[*] 输入: {text}", flush=True)

        # 纯数字直接返回
        if text.isdigit():
            return text

        # 清理
        text = re.sub(r'[=＝?？]', '', text)
        text = text.replace(' ', '')
        text = text.replace('×', '*').replace('÷', '/').replace('x', '*').replace('X', '*')

        # 匹配表达式
        match = re.match(r'^(\d+\.?\d*)([+\-*/])(\d+\.?\d*)$', text)
        if match:
            n1, op, n2 = float(match.group(1)), match.group(2), float(match.group(3))
            if op == '+': r = n1 + n2
            elif op == '-': r = n1 - n2
            elif op == '*': r = n1 * n2
            elif op == '/': r = n1 / n2 if n2 != 0 else 0
            else: return text
            r = int(r) if r == int(r) else r
            print(f"[*] 计算: {n1}{op}{n2}={r}", flush=True)
            return str(r)

        # 提取数字
        numbers = re.findall(r'\d+', text)
        if numbers:
            return numbers[-1]

        return text
    except:
        return text


# ==================== 识别函数 ====================

def recognize_with_ddddocr(image_data, is_calculation=False):
    """使用 ddddocr 识别"""
    try:
        if ocr is None:
            print("[-] ddddocr 未初始化", flush=True)
            return None

        text = ocr.classification(image_data)
        print(f"[*] ddddocr: {text}", flush=True)

        if not text or not text.strip():
            return None

        text = text.strip()

        # 计算型验证码：计算结果
        if is_calculation:
            result = evaluate_expression(text)
            return result

        return text

    except Exception as e:
        print(f"[-] ddddocr 错误: {e}", flush=True)
        return None


def recognize_with_ai(image_data, is_calculation=False):
    """使用本地大模型识别"""
    try:
        compressed = compress_image(image_data)
        img_base64 = base64.b64encode(compressed).decode('utf-8')

        if is_calculation:
            system = "你是计算器。看图中的数学题并计算。/是除号，×是乘号。只输出最终数字答案，不要任何解释。"
            user = "答案是？"
        else:
            system = "你是OCR工具。识别验证码。只输出文字，不要任何解释。"
            user = "验证码是？"

        payload = {
            "model": AI_MODEL,
            "messages": [
                {"role": "system", "content": system},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": user},
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{img_base64}"}}
                    ]
                }
            ],
            "temperature": 0,
            "max_tokens": 200
        }

        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {AI_API_KEY}"
        }

        print(f"[*] AI 图片: {len(compressed)} bytes", flush=True)

        response = requests.post(AI_API_URL, headers=headers, json=payload, timeout=30)

        if response.status_code == 200:
            result = response.json()
            raw_text = None

            if 'choices' in result and len(result['choices']) > 0:
                msg = result['choices'][0].get('message', {})
                raw_text = msg.get('content', '') or msg.get('reasoning_content', '')

            if not raw_text or not raw_text.strip():
                return None

            raw_text = raw_text.strip()
            print(f"[*] AI 原始: {raw_text[:100]}", flush=True)

            # 计算型：提取数字
            if is_calculation:
                m = re.search(r'(答案|结果)[是为]?\s*[：:]?\s*(\d+)', raw_text)
                if m:
                    return m.group(2)
                m = re.search(r'\*\*(\d+)\*\*', raw_text)
                if m:
                    return m.group(1)
                numbers = re.findall(r'\d+', raw_text)
                if numbers:
                    return numbers[-1]
                return None

            # 普通验证码：清理思考内容
            for prefix in ['The user', 'I need', 'Let me', 'Looking', 'The image', 'Here']:
                if raw_text.startswith(prefix):
                    parts = raw_text.split(':')
                    if len(parts) > 1:
                        raw_text = parts[-1].strip()
                    break

            quoted = re.findall(r"'([^']+)'", raw_text)
            if quoted:
                return ''.join(quoted)

            lines = raw_text.strip().split('\n')
            for line in reversed(lines):
                line = line.strip().replace('*', '')
                if line and len(line) < 15 and not line.startswith('The'):
                    return line

            return raw_text[:20]

        else:
            print(f"[-] AI 失败: {response.status_code}", flush=True)
            return None

    except Exception as e:
        print(f"[-] AI 错误: {e}", flush=True)
        return None


def recognize(image_data, is_calculation=False):
    """统一识别接口"""
    if OCR_MODE == "ai":
        return recognize_with_ai(image_data, is_calculation)
    else:
        return recognize_with_ddddocr(image_data, is_calculation)


# ==================== HTTP 服务 ====================

class CaptchaHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/':
            self.serve_web_ui()
        elif self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({
                "status": "ok",
                "mode": OCR_MODE
            }).encode())
        else:
            self.send_error(404)

    def do_POST(self):
        if self.path == '/imgurl':
            self.handle_captcha()
        else:
            self.send_error(404)

    def serve_web_ui(self):
        try:
            with open('temp/log.txt', 'r') as f:
                content = f.read()
            html = f'''<!DOCTYPE html><html><head><meta charset="UTF-8"><title>killcap OCR</title>
<style>body{{background:#1a1a2e;color:#eee;font-family:Arial;padding:20px}}
h1{{text-align:center;color:#00d4ff}}table{{width:100%;border-collapse:collapse}}
th{{background:#16213e;color:#00d4ff;padding:10px;border:1px solid #0f3460}}
td{{padding:8px;border:1px solid #0f3460;text-align:center}}img{{max-width:150px}}
.result{{font-size:20px;font-weight:bold;color:#00ff88}}
button{{padding:8px 16px;background:#00d4ff;color:#000;border:none;border-radius:4px;cursor:pointer;margin:5px}}
.mode{{background:#ff6b6b;color:#fff;padding:4px 8px;border-radius:4px;font-size:12px}}</style>
</head><body><h1>killcap OCR</h1>
<p style="text-align:center">模式: <span class="mode">{OCR_MODE}</span> | <button onclick="location.reload()">刷新</button></p>
<table><tr><th>验证码</th><th>结果</th><th>时间</th><th>类型</th></tr>{content}</table>
<script>setTimeout(()=>location.reload(),3000)</script></body></html>'''
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=UTF-8')
            self.end_headers()
            self.wfile.write(html.encode())
        except Exception as e:
            self.send_error(500, str(e))

    def handle_captcha(self):
        try:
            body = self.rfile.read(int(self.headers.get('Content-Length', 0))).decode()
            params = parse_qs(body)

            url = self._param(params, 'xp_url', True)
            req_type = self._param(params, 'xp_type', default='1')
            cookie = self._param(params, 'xp_cookie', True)
            set_ranges = int(self._param(params, 'xp_set_ranges', default='6'))
            complex_req = self._param(params, 'xp_complex_request', True)
            rf = int(self._param(params, 'xp_rf', default='0'))
            re_pattern = self._param(params, 'xp_re', True)
            is_re = self._param(params, 'xp_is_re_run', default='false')

            print(f"\n[*] 请求: ranges={set_ranges}, mode={OCR_MODE}", flush=True)

            resp = self._fetch(url, req_type, cookie, complex_req)
            if not resp:
                self._respond("error:获取失败")
                return

            img = extract_image_from_response(resp)

            if set_ranges == 9:
                result = recognize(img, True)
            else:
                text = recognize(img, False)
                result = self._filter(text, set_ranges) if text else None

            if not result:
                self._respond("error:识别失败")
                return

            if is_re == 'true' and re_pattern:
                extra = self._extract_extra(resp, rf, re_pattern)
                if extra:
                    result = f"{result}|{extra}"

            print(f"[+] 结果: {result}", flush=True)
            self._save_log(img, result, set_ranges)
            self._respond(result)

        except Exception as e:
            print(f"[-] 错误: {e}", flush=True)
            self._respond(f"error:{e}")

    def _param(self, params, name, b64=False, default=''):
        v = params.get(name, [default])[0]
        if b64 and v:
            try: return base64.b64decode(v).decode()
            except: pass
        return v

    def _fetch(self, url, req_type, cookie, complex_req):
        try:
            h = {'User-Agent': 'Mozilla/5.0'}
            if cookie and cookie != 'null=null;':
                h['Cookie'] = cookie
            if req_type == '2' and complex_req:
                return self._complex_req(url, complex_req)
            return requests.get(url, headers=h, timeout=10, verify=False)
        except Exception as e:
            print(f"[-] 获取失败: {e}", flush=True)
            return None

    def _complex_req(self, url, raw):
        try:
            lines = raw.strip().split('\n')
            method = lines[0].split()[0].upper()
            headers, body, in_body = {}, '', False
            for line in lines[1:]:
                line = line.strip()
                if in_body:
                    body += line
                elif line == '':
                    in_body = True
                elif ':' in line:
                    k, v = line.split(':', 1)
                    if k.strip().lower() not in ['content-length', 'connection']:
                        headers[k.strip()] = v.strip()
            if method == 'POST':
                return requests.post(url, headers=headers, data=body, timeout=10, verify=False)
            return requests.get(url, headers=headers, timeout=10, verify=False)
        except:
            return None

    def _filter(self, text, sr):
        filters = {0:r'[^0-9]', 1:r'[^a-z]', 2:r'[^A-Z]', 3:r'[^a-zA-Z]',
                   4:r'[^a-z0-9]', 5:r'[^A-Z0-9]', 6:r'[^a-zA-Z0-9]', 7:r'[^a-zA-Z0-9]'}
        p = filters.get(sr, filters[6])
        return re.sub(p, '', text) if sr != 8 else text

    def _extract_extra(self, resp, rf, pattern):
        try:
            if not pattern: return None
            if rf == 1:
                parts = pattern.split('|', 1)
                if len(parts) == 2:
                    m = re.search(parts[1].strip(), resp.headers.get(parts[0].strip(), ''))
                    return m.group(1) if m else None
            else:
                m = re.search(pattern, resp.text)
                return m.group(1) if m else None
        except:
            return None

    def _save_log(self, img, result, sr):
        try:
            if not img: return
            t = time.time()
            with open(f"temp/{t}.png", 'wb') as f: f.write(img)
            with open('temp/log.txt', 'r') as f:
                old = f.read()
            with open(f"temp/{t}.png", 'rb') as f:
                b64 = base64.b64encode(f.read()).decode()
            names = {9:"计算型", 8:"不识别"}
            name = names.get(sr, f"类型{sr}")
            with open('temp/log.txt', 'w') as f:
                f.write(f'<tr><td><img src="data:image/png;base64,{b64}"/></td>'
                       f'<td class="result">{result}</td>'
                       f'<td>{time.strftime("%H:%M:%S")}</td><td>{name}</td></tr>\n' + old)
            os.remove(f"temp/{t}.png")
        except:
            pass

    def _respond(self, data):
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain; charset=utf-8')
        self.end_headers()
        self.wfile.write(data.encode())

    def log_message(self, fmt, *args):
        pass


# ==================== 启动 ====================

if __name__ == '__main__':
    print(f"[*] killcap OCR 服务端")
    print(f"[*] 模式: {OCR_MODE}")
    print(f"[*] 地址: http://{HOST}:{PORT}")

    if OCR_MODE == "ddddocr":
        print(f"[*] ddddocr: {'就绪' if ocr else '未安装'}")
    else:
        print(f"[*] AI: {AI_API_URL}")
        print(f"[*] 模型: {AI_MODEL}")

    server = HTTPServer((HOST, PORT), CaptchaHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()
