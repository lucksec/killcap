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

## 识别模式

server.py 支持两种识别模式：

### 1. ddddocr 模式（默认）

```python
OCR_MODE = "ddddocr"
```

- **优点**：速度快（毫秒级），无需额外配置
- **缺点**：准确率一般，对复杂验证码效果差
- **适用**：简单验证码、批量爆破、对速度要求高

### 2. AI 模式

```python
OCR_MODE = "ai"
AI_API_URL = "http://127.0.0.1:1234/v1/chat/completions"
AI_API_KEY = "sk-lm-xxx"
AI_MODEL = "qwen/qwen2.5-vl-7b"
```

- **优点**：准确率高，支持复杂验证码
- **缺点**：速度慢（秒级），需要本地大模型
- **适用**：复杂验证码、计算型验证码、对准确率要求高

### 切换模式

修改 `server.py` 顶部配置：

```python
# 使用 ddddocr
OCR_MODE = "ddddocr"

# 使用 AI
OCR_MODE = "ai"
```

## 本地大模型推荐

使用 AI 模式需要本地运行大模型服务，推荐使用 [LM Studio](https://lmstudio.ai/)。

### 推荐模型

| 模型 | 参数量 | 显存需求 | 速度 | 准确率 | 说明 |
|------|--------|----------|------|--------|------|
| **qwen2.5-vl-7b** | 7B | 8GB | 快 | 高 | 推荐首选，中文识别效果好 |
| **qwen2.5-vl-3b** | 3B | 4GB | 很快 | 中 | 轻量级，显存不足时使用 |
| **gemma-3-4b-it** | 4B | 6GB | 快 | 中 | Google 出品，英文效果好 |
| **internvl2-8b** | 8B | 10GB | 中 | 很高 | 识别准确率最高 |
| **minicpm-v-2.6** | 8B | 10GB | 中 | 很高 | 中文识别效果极佳 |

### 模型选择建议

- **显存 8GB 以下**：`qwen2.5-vl-3b` 或 `gemma-3-4b-it`
- **显存 8-12GB**：`qwen2.5-vl-7b`（推荐）
- **显存 12GB 以上**：`internvl2-8b` 或 `minicpm-v-2.6`

### LM Studio 配置

1. 下载安装 [LM Studio](https://lmstudio.ai/)
2. 搜索并下载推荐模型（如 `qwen2.5-vl-7b`）
3. 启动本地服务（默认端口 1234）
4. 修改 `server.py` 配置：

```python
OCR_MODE = "ai"
AI_API_URL = "http://127.0.0.1:1234/v1/chat/completions"
AI_API_KEY = "lm-studio"  # LM Studio 默认 key
AI_MODEL = "qwen/qwen2.5-vl-7b"  # 根据实际模型名称修改
```

### 其他本地模型平台

| 平台 | 说明 |
|------|------|
| [LM Studio](https://lmstudio.ai/) | 推荐，GUI 界面，支持多平台 |
| [Ollama](https://ollama.ai/) | 命令行工具，轻量级 |
| [LocalAI](https://github.com/mudler/LocalAI) | 兼容 OpenAI API |
| [vLLM](https://github.com/vllm-project/vllm) | 高性能推理引擎 |

## Burp 插件输出模式详解

在 Burp 插件的"输出模式"下拉框中，有以下选项：

### 普通验证码模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| **纯整数0-9** | 只保留数字 | 纯数字验证码，如 `1234` |
| **纯小写a-z** | 只保留小写字母 | 纯小写验证码，如 `abcd` |
| **纯大写A-Z** | 只保留大写字母 | 纯大写验证码，如 `ABCD` |
| **大小写英文** | 保留大小写字母 | 大小写混合验证码，如 `aBcD` |
| **小写+数字** | 保留小写字母和数字 | 小写+数字验证码，如 `a1b2` |
| **大写+数字** | 保留大写字母和数字 | 大写+数字验证码，如 `A1B2` |
| **大小写+数字** | 保留大小写字母和数字 | 最常见的验证码类型，如 `a1B2` |
| **默认字符库** | 使用 ddddocr 默认字符库 | 不确定验证码类型时使用 |

### 特殊模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| **不识别（高级模块）** | 不进行 OCR 识别 | 验证码在响应包中直接返回，配合高级模式使用 |
| **计算型验证码** | 识别数学表达式并计算结果 | 如 `3+1=?`、`5*2=?`、`8/2=?` |

### 如何选择输出模式

1. **查看验证码图片**：先手动查看验证码是什么类型
2. **选择对应模式**：根据验证码内容选择合适的输出模式
3. **测试识别**：在 Web 监控界面（http://127.0.0.1:8899）查看识别结果

**示例：**

- 验证码是 `1234` → 选择 **纯整数0-9**
- 验证码是 `abcd` → 选择 **纯小写a-z**
- 验证码是 `a1B2` → 选择 **大小写+数字**
- 验证码是 `3+1=?` → 选择 **计算型验证码**
- 验证码在响应 JSON 中 → 选择 **不识别**，配合高级模式提取

## 高级模式

高级模式用于从验证码响应中提取额外数据（如 uuid）。

### 使用场景

当验证码接口返回 JSON 格式时：

```json
{"img": "base64...", "uuid": "878c633b27db4b22996ddcd9df64013b"}
```

需要提取 `uuid` 字段用于登录请求。

### 配置步骤

1. **数据源**：选择"响应体"（从 JSON 中提取）
2. **正则**：`"uuid":"(.*?)"`
3. 点击 **开启高级模式**
4. **保存配置**

### 使用关键字

| 关键字 | 说明 |
|--------|------|
| `@killcap@1@` | 验证码 1 的识别结果 |
| `@killcap@2@` | 验证码 2 的识别结果 |
| `@killcap@3@` | 验证码 3 的识别结果 |
| `@killcap@4@` | 验证码 4 的识别结果 |
| `@killcap@5@` | 验证码 5 的识别结果 |
| `@killcap@x@` | 高级模式提取的数据（如 uuid） |

### 示例请求

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

## 重试设置

当验证码识别错误时，可自动重试：

1. 勾选 **错误重试**
2. 设置最大重试次数（默认 3 次）
3. 错误关键词（默认：验证码错误,验证码已失效）

## Web 监控界面

访问 `http://127.0.0.1:8899` 可查看：

- 验证码图片
- 识别结果
- 时间戳
- 验证码类型
- 当前识别模式（ddddocr/ai）

## 使用方法

### 1. 安装依赖

```bash
# ddddocr 模式
pip3 install ddddocr requests

# AI 模式
pip3 install requests pillow
```

### 2. 启动 OCR 服务端

```bash
python3 server.py
```

### 3. 加载 Burp 插件

1. 打开 Burp Suite → Extender → Extensions
2. 点击 Add → 选择 `killcap_v1.0.jar`
3. 确认插件加载成功

### 4. 配置插件

在 `killcap` 标签页：

1. **OCR 接口**：`127.0.0.1:8899`（默认）
2. **验证码 URL**：填写获取验证码的接口地址
3. **输出模式**：根据验证码类型选择（见上方详解）
4. **监控设置**：勾选需要监控的工具（Intruder/Repeater/Proxy）
5. 点击 **保存配置**

### 5. 使用关键字

在请求中使用 `@killcap@N@` 替换验证码，`@killcap@x@` 替换高级模式数据。

## 常见问题

### Q: 识别不准确怎么办？

1. 检查输出模式是否正确
2. 尝试切换到 AI 模式
3. 查看 Web 监控界面的识别结果

### Q: 服务端启动失败？

1. 检查端口 8899 是否被占用
2. 检查依赖是否安装：`pip3 install ddddocr requests`

### Q: 验证码已失效？

1. 开启重试设置
2. 检查验证码 URL 是否正确
3. 检查网络连接

### Q: 如何使用 AI 模式？

1. 安装本地大模型（如 LM Studio）
2. 修改 `server.py` 中的 `OCR_MODE = "ai"`
3. 配置 `AI_API_URL`、`AI_API_KEY`、`AI_MODEL`
4. 重启服务端

## 编译

```bash
# 编译 Burp 插件
mkdir -p build
javac -source 1.8 -target 1.8 -classpath burp.jar -d build BurpExtender.java
cd build
jar cf ../killcap_v1.0.jar burp/
```

## License

MIT License
