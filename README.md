# killcap V1.0

Burp Suite 验证码自动识别插件，支持普通验证码和计算型验证码（如 `3+1=?`）。

## 架构

```
┌─────────────┐     HTTP      ┌─────────────┐     API      ┌─────────────┐
│  Burp Suite │ ──────────── │  server.py  │ ──────────── │  LLM Model  │
│   插件      │   :8899      │  OCR服务端   │   :1234     │  本地大模型  │
└─────────────┘              └─────────────┘              └─────────────┘
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `BurpExtender.java` | Burp 插件源码，负责拦截请求、调用 OCR 服务、替换验证码 |
| `server.py` | Python OCR 服务端，负责获取验证码图片、调用大模型识别、返回结果 |
| `killcap_v1.0.jar` | 编译好的 Burp 插件 |

## server.py 详解

### 功能概述

server.py 是一个 HTTP 服务端，监听 `127.0.0.1:8899`，接收 Burp 插件的验证码识别请求，调用本地大模型进行 OCR 识别，返回识别结果。

### 工作流程

```
1. 接收 Burp 插件的 POST 请求（/imgurl）
2. 解析参数（验证码URL、Cookie、模式等）
3. 获取验证码图片（支持 JSON 响应中的 base64 图片）
4. 调用本地大模型进行 OCR 识别
5. 如果是计算型验证码，计算数学表达式
6. 返回识别结果（支持高级模式提取 uuid 等数据）
```

### API 接口

**POST /imgurl**

请求参数（form-data）：

| 参数 | 说明 | 示例 |
|------|------|------|
| `xp_url` | 验证码 URL（base64 编码） | `aHR0cHM6Ly8uLi4=` |
| `xp_type` | 请求模式 | `1`=简单, `2`=复杂 |
| `xp_cookie` | Cookie（base64 编码） | `c2Vzc2lvbj14eHg=` |
| `xp_set_ranges` | 输出模式 | `9`=计算型验证码 |
| `xp_complex_request` | 复杂模式请求包（base64 编码） | |
| `xp_rf` | 高级模式数据源 | `0`=响应体, `1`=响应头 |
| `xp_re` | 正则表达式（base64 编码） | InV1aWQiOiIoLio/KSI=` |
| `xp_is_re_run` | 是否启用高级模式 | `true`/`false` |

响应格式：

```
识别结果|正则提取数据
```

示例：
- 普通模式：`4`
- 高级模式：`4|878c633b27db4b22996ddcd9df64013b`

### 本地大模型配置

在 `server.py` 顶部配置：

```python
OCR_API_URL = "http://127.0.0.1:1234/v1/chat/completions"
OCR_API_KEY = "sk-lm-xxx"
OCR_MODEL = "qwen/qwen2.5-vl-7b"
```

支持 OpenAI 兼容格式的本地模型服务（如 LM Studio、Ollama 等）。

### 计算型验证码

当 `xp_set_ranges=9` 时，进入计算型验证码模式：

1. 调用大模型识别图片中的数学表达式（如 `3+1=?`）
2. 解析表达式并计算结果
3. 返回数字结果

支持的运算符：`+` `-` `*` `/` `×` `÷`

### Web 监控界面

访问 `http://127.0.0.1:8899` 可查看：
- 验证码图片
- 识别结果
- 时间戳
- 验证码类型

### 依赖安装

```bash
pip3 install requests pillow
```

## 使用方法

### 1. 启动 OCR 服务端

```bash
python3 server.py
```

### 2. 加载 Burp 插件

1. 打开 Burp Suite → Extender → Extensions
2. 点击 Add → 选择 `killcap_v1.0.jar`
3. 确认插件加载成功

### 3. 配置插件

在 `killcap` 标签页：

1. **OCR 接口**：`127.0.0.1:8899`（默认）
2. **验证码 URL**：填写获取验证码的接口地址
3. **输出模式**：选择对应的模式（计算型验证码选"计算型验证码"）
4. **监控设置**：勾选需要监控的工具（Intruder/Repeater/Proxy）
5. 点击 **保存配置**

### 4. 使用关键字

在请求中使用以下关键字：

| 关键字 | 说明 |
|--------|------|
| `@killcap@1@` | 验证码 1 的识别结果 |
| `@killcap@2@` | 验证码 2 的识别结果 |
| `@killcap@3@` | 验证码 3 的识别结果 |
| `@killcap@4@` | 验证码 4 的识别结果 |
| `@killcap@5@` | 验证码 5 的识别结果 |
| `@killcap@x@` | 高级模式提取的数据（如 uuid） |

### 5. 示例请求

```http
POST /login HTTP/1.1
Host: target.com
Content-Type: application/json

{
  "username": "admin",
  "password": "123456",
  "code": "@killcap@1@",
  "uuid": "@killcap@x@"
}
```

## 高级模式

高级模式用于从验证码响应中提取额外数据（如 uuid）。

### 配置

1. **数据源**：选择从响应体或响应头提取
2. **正则**：填写正则表达式，用 `()` 包裹要提取的部分
3. 点击 **开启高级模式**
4. **保存配置**

### 示例

验证码接口返回：
```json
{"img": "base64...", "uuid": "878c633b27db4b22996ddcd9df64013b"}
```

正则：`"uuid":"(.*?)"`

提取结果：`878c633b27db4b22996ddcd9df64013b`

在请求中使用 `@killcap@x@` 会被替换为提取的 uuid。

## 重试设置

当验证码识别错误时，可自动重试：

1. 勾选 **错误重试**
2. 设置最大重试次数（默认 3 次）
3. 错误关键词（默认：验证码错误,验证码已失效）

## 编译

```bash
# 编译 Burp 插件
mkdir -p build
javac -source 1.8 -target 1.8 -classpath burp.jar -d build BurpExtender.java
cd build
jar cf ../killcap_v1.0.jar burp/
```

## 注意事项

- 爆破时线程设置为 1
- 确保本地大模型服务正常运行
- 验证码 URL 需要填写完整的 HTTP 地址

## License

MIT License
