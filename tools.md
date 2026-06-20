# killcap - Burp Suite 验证码自动识别插件

> 基于 [xp_CAPTCHA（瞎跑）](https://github.com/smxiazi/NEW_xp_CAPTCHA) 二次开发，感谢原作者算命縖子

## 工具简介

killcap 是一款 Burp Suite 验证码自动识别插件，支持普通验证码和计算型验证码（如 `3+1=?`）。插件通过调用本地 OCR 服务（ddddocr 或本地大模型）自动识别验证码，并将结果替换到爆破请求中。

## 工具特点

- 支持普通验证码（数字、字母、混合）
- 支持计算型验证码（加减乘除）
- 支持 ddddocr 和 AI 两种识别模式
- 支持从验证码响应中提取额外数据（如 uuid）
- 支持验证码错误自动重试
- 支持 Intruder、Repeater、Proxy 流量监控
- Web 监控界面，实时查看识别结果

## 架构设计

```
┌─────────────────┐     HTTP      ┌─────────────────┐     API      ┌─────────────────┐
│   Burp Suite    │ ──────────── │    server.py    │ ──────────── │   本地大模型    │
│   killcap 插件  │   :8899      │   OCR 服务端    │   :1234     │   LM Studio     │
└─────────────────┘              └─────────────────┘              └─────────────────┘
```

## 环境要求

- Burp Suite（任意版本）
- Python 3.8+
- JDK 1.8+（编译插件）

## 安装部署

### 1. 安装 Python 依赖

```bash
# ddddocr 模式（推荐）
pip3 install ddddocr requests

# AI 模式（需要本地大模型）
pip3 install requests pillow
```

### 2. 启动 OCR 服务端

```bash
python3 server.py
```

默认监听 `127.0.0.1:8899`，可通过修改 `server.py` 顶部配置更改。

### 3. 加载 Burp 插件

1. 下载 `killcap_v1.0.jar`
2. 打开 Burp Suite → Extender → Extensions
3. 点击 Add → 选择 `killcap_v1.0.jar`
4. 确认插件加载成功，出现 `killcap` 标签页

## 识别模式

### ddddocr 模式（默认）

```python
OCR_MODE = "ddddocr"
```

- 本地识别，速度快（毫秒级）
- 无需额外配置
- 适合简单验证码、批量爆破

### AI 模式

```python
OCR_MODE = "ai"
AI_API_URL = "http://127.0.0.1:1234/v1/chat/completions"
AI_API_KEY = "sk-lm-xxx"
AI_MODEL = "qwen/qwen2.5-vl-7b"
```

- 调用本地大模型识别
- 准确率高，支持复杂验证码
- 适合计算型验证码、复杂场景

## 输出模式详解

在 Burp 插件的"输出模式"下拉框中，有以下选项：

### 普通验证码模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| 纯整数0-9 | 只保留数字 | 纯数字验证码，如 `1234` |
| 纯小写a-z | 只保留小写字母 | 纯小写验证码，如 `abcd` |
| 纯大写A-Z | 只保留大写字母 | 纯大写验证码，如 `ABCD` |
| 大小写英文 | 保留大小写字母 | 大小写混合验证码，如 `aBcD` |
| 小写+数字 | 保留小写字母和数字 | 小写+数字验证码，如 `a1b2` |
| 大写+数字 | 保留大写字母和数字 | 大写+数字验证码，如 `A1B2` |
| 大小写+数字 | 保留大小写字母和数字 | 最常见的验证码类型，如 `a1B2` |
| 默认字符库 | 使用 ddddocr 默认字符库 | 不确定验证码类型时使用 |

### 特殊模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| 不识别（高级模块） | 不进行 OCR 识别 | 验证码在响应包中直接返回，配合高级模式使用 |
| 计算型验证码 | 识别数学表达式并计算结果 | 如 `3+1=?`、`5*2=?`、`8/2=?` |

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

## 关键字说明

| 关键字 | 说明 |
|--------|------|
| `@killcap@1@` | 验证码 1 的识别结果 |
| `@killcap@2@` | 验证码 2 的识别结果 |
| `@killcap@3@` | 验证码 3 的识别结果 |
| `@killcap@4@` | 验证码 4 的识别结果 |
| `@killcap@5@` | 验证码 5 的识别结果 |
| `@killcap@x@` | 高级模式提取的数据（如 uuid） |

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

## 使用流程

### 1. 配置插件

在 `killcap` 标签页：

1. **OCR 接口**：`127.0.0.1:8899`（默认）
2. **验证码 URL**：填写获取验证码的接口地址
3. **输出模式**：根据验证码类型选择
4. **监控设置**：勾选需要监控的工具（Intruder/Repeater/Proxy）
5. 点击 **保存配置**

### 2. 构造请求

在 Intruder 或 Repeater 中，在需要替换验证码的位置使用关键字：

```
"code": "@killcap@1@"
```

### 3. 发送请求

通过 Intruder 爆破或 Repeater 发送请求，插件会自动：

1. 识别验证码图片
2. 替换关键字
3. 发送请求
4. 如果开启重试，识别错误时自动重试

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

### Q: 支持哪些验证码类型？

- 数字验证码：`1234`
- 字母验证码：`abcd`、`ABCD`
- 混合验证码：`a1B2`
- 计算型验证码：`3+1=?`、`5*2=?`
- JSON 响应中的验证码（配合高级模式）

## 编译说明

```bash
# 编译 Burp 插件
mkdir -p build
javac -source 1.8 -target 1.8 -classpath burp.jar -d build BurpExtender.java
cd build
jar cf ../killcap_v1.0.jar burp/
```

## 项目地址

- GitHub：https://github.com/lucksec/killcap
- 下载：https://github.com/lucksec/killcap/releases

## 致谢

- [xp_CAPTCHA（瞎跑）](https://github.com/smxiazi/NEW_xp_CAPTCHA) - 原作者算命縖子
- [ddddocr](https://github.com/sml2h3/ddddocr) - OCR 识别库
- [LM Studio](https://lmstudio.ai/) - 本地大模型运行环境

## 免责声明

本工具仅供安全研究和授权测试使用，请勿用于非法用途。使用本工具进行任何违法行为，一切后果由使用者自行承担。

## License

MIT License
