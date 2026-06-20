package burp;
import java.nio.charset.StandardCharsets;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemListener;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BurpExtender implements IBurpExtender, ITab, IHttpListener, IContextMenuFactory
{
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private JSplitPane splitPane;
    public PrintWriter stdout;
    int switchs = 1;
    int clicks_Repeater = 0;
    int clicks_Intruder = 0;
    int clicks_Proxy = 0;
    int xiapao_count = 0;
    String XiaPao_api_HOST = "127.0.0.1";
    int XiaPao_api_Port = 8899;
    JLabel jl_status;
    Map<Integer, List<String>> yzm_set_map = new HashMap<>();
    JTextArea jta;
    JTextField jps_txtfield_1;
    JTextField jtf_captcha_url;
    JComboBox jb_1;
    JComboBox jb_captcha_sel;
    JLabel jl_hint;
    JLabel[] jl_captcha_urls;
    Boolean re_switch = false;
    String xp_version = "1.1";
    String plugin_name = "killcap";

    int retry_switch = 0;
    int retry_max = 3;
    String retry_keywords = "验证码错误,验证码已失效,验证码不正确";
    Map<Integer, byte[]> originalRequests = new HashMap<>();
    Map<Integer, Integer> retryCountMap = new HashMap<>();

    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks)
    {
        this.stdout = new PrintWriter(callbacks.getStdout(), true);
        this.stdout.println("[*] " + plugin_name + " V" + xp_version + " loaded!");
        this.callbacks = callbacks;
        helpers = callbacks.getHelpers();
        callbacks.setExtensionName(plugin_name + " V" + xp_version);

        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                // ==================== 主分割：左(9/10) 右(1/10) ====================
                splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
                splitPane.setDividerLocation(1200);
                splitPane.setResizeWeight(0.9); // 左侧占90%

                // ==================== 左侧主面板 ====================
                JPanel jp_left = new JPanel(new BorderLayout(5, 5));
                jp_left.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                // --- 左上：验证码配置 ---
                JPanel jp_config = new JPanel(new BorderLayout(5, 5));

                // 接口+编号 行
                JPanel jp_top_row = new JPanel(new GridLayout(1, 2, 8, 0));
                JPanel jp_api = new JPanel(new BorderLayout(3, 0));
                jp_api.setBorder(BorderFactory.createTitledBorder(" OCR接口 "));
                JTextField txtfield_0 = new JTextField("127.0.0.1:8899");
                jp_api.add(txtfield_0, BorderLayout.CENTER);
                JPanel jp_sel = new JPanel(new BorderLayout(3, 0));
                jp_sel.setBorder(BorderFactory.createTitledBorder(" 验证码编号 "));
                JComboBox jb_set = new JComboBox();
                jb_captcha_sel = jb_set;
                for (int i = 1; i <= 5; i++) jb_set.addItem("验证码 " + i);
                jp_sel.add(jb_set, BorderLayout.CENTER);
                jp_top_row.add(jp_api);
                jp_top_row.add(jp_sel);

                // 关键字+URL 行
                JPanel jp_mid_row = new JPanel(new BorderLayout(5, 5));
                JPanel jp_keyword = new JPanel(new BorderLayout(3, 0));
                jp_keyword.setBorder(BorderFactory.createTitledBorder(" 关键字 "));
                jl_status = new JLabel(" @killcap@1@ ", SwingConstants.CENTER);

                jl_status.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
                jl_status.setForeground(new Color(0, 153, 255));
                jl_status.setOpaque(true);
                jl_status.setBackground(new Color(240, 248, 255));
                jp_keyword.add(jl_status, BorderLayout.CENTER);
                JPanel jp_url = new JPanel(new BorderLayout(3, 0));
                jp_url.setBorder(BorderFactory.createTitledBorder(" 验证码URL "));
                JTextField txtfield_1 = new JTextField();
                jtf_captcha_url = txtfield_1;
                jp_url.add(txtfield_1, BorderLayout.CENTER);
                jp_mid_row.add(jp_keyword, BorderLayout.WEST);
                jp_mid_row.add(jp_url, BorderLayout.CENTER);

                // 模式+输出 行
                JPanel jp_bot_row = new JPanel(new GridLayout(1, 2, 8, 0));
                JPanel jp_mode = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                jp_mode.setBorder(BorderFactory.createTitledBorder(" 请求模式 "));
                ButtonGroup group_1 = new ButtonGroup();
                JRadioButton rb_url_1_1 = new JRadioButton("简单模式", true);
                JRadioButton rb_url_1_2 = new JRadioButton("复杂模式");
                group_1.add(rb_url_1_1);
                group_1.add(rb_url_1_2);
                jp_mode.add(rb_url_1_1);
                jp_mode.add(rb_url_1_2);
                JPanel jp_output = new JPanel(new BorderLayout(3, 0));
                jp_output.setBorder(BorderFactory.createTitledBorder(" 输出模式 "));
                JComboBox jb_set_case_sensitive = new JComboBox();
                jb_set_case_sensitive.addItem("纯整数0-9");
                jb_set_case_sensitive.addItem("纯小写a-z");
                jb_set_case_sensitive.addItem("纯大写A-Z");
                jb_set_case_sensitive.addItem("大小写英文");
                jb_set_case_sensitive.addItem("小写+数字");
                jb_set_case_sensitive.addItem("大写+数字");
                jb_set_case_sensitive.addItem("大小写+数字");
                jb_set_case_sensitive.addItem("默认字符库");
                jb_set_case_sensitive.addItem("不识别（高级模块）");
                jb_set_case_sensitive.addItem("计算型验证码");
                jb_set_case_sensitive.setSelectedIndex(6);
                jp_output.add(jb_set_case_sensitive, BorderLayout.CENTER);
                jp_bot_row.add(jp_mode);
                jp_bot_row.add(jp_output);

                // 状态提示
                jl_hint = new JLabel(" ");
                jl_hint.setForeground(Color.RED);
                jl_hint.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

                // 组装配置区
                jp_config.add(jp_top_row, BorderLayout.NORTH);
                jp_config.add(jp_mid_row, BorderLayout.CENTER);
                JPanel jp_bottom = new JPanel(new BorderLayout(0, 3));
                jp_bottom.add(jp_bot_row, BorderLayout.CENTER);
                jp_bottom.add(jl_hint, BorderLayout.SOUTH);
                jp_config.add(jp_bottom, BorderLayout.SOUTH);

                // --- 左下：高级模式 + 已配置列表 ---
                JPanel jp_bottom_area = new JPanel(new GridLayout(1, 2, 5, 0));

                // 高级模式
                JPanel jp_adv = new JPanel(new BorderLayout(3, 3));
                jp_adv.setBorder(BorderFactory.createTitledBorder(" 高级模式 "));
                JPanel jp_adv_form = new JPanel(new GridLayout(5, 1, 3, 3));
                JLabel jl_adv_title = new JLabel("验证码 1 设置");
                jl_adv_title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                JPanel jp_src = new JPanel(new BorderLayout(3, 0));
                jb_1 = new JComboBox();
                jb_1.addItem("响应体");
                jb_1.addItem("响应头");
                jp_src.add(new JLabel("数据源:"), BorderLayout.WEST);
                jp_src.add(jb_1, BorderLayout.CENTER);
                JPanel jp_re = new JPanel(new BorderLayout(3, 0));
                jps_txtfield_1 = new JTextField("\"uuid\":\"(.*?)\"");
                jps_txtfield_1.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                jp_re.add(new JLabel("正则:"), BorderLayout.WEST);
                jp_re.add(jps_txtfield_1, BorderLayout.CENTER);
                JLabel jl_adv_hint = new JLabel("关键字: @killcap@x@");
                jl_adv_hint.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
                jl_adv_hint.setForeground(new Color(0, 153, 255));
                JButton jps_2_bt_1 = new JButton("开启高级模式");
                JLabel jps_2_4 = new JLabel("");
                jps_2_4.setForeground(Color.red);
                jp_adv_form.add(jl_adv_title);
                jp_adv_form.add(jp_src);
                jp_adv_form.add(jp_re);
                jp_adv_form.add(jl_adv_hint);
                jp_adv_form.add(jps_2_bt_1);

                // 复杂模式请求包（放在高级模式下方）
                JPanel jp_complex = new JPanel(new BorderLayout());
                jp_complex.setBorder(BorderFactory.createTitledBorder(" 复杂模式请求包 "));
                String dome = "POST /captcha HTTP/1.1\nHost: example.com\nCookie: session=xxx;\nContent-Type: application/x-www-form-urlencoded\n\nparam=value";
                JTextArea jta_1 = new JTextArea(dome);
                jta_1.setEditable(false);
                jta_1.setBackground(new Color(245, 245, 245));
                jta_1.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                jp_complex.add(new JScrollPane(jta_1), BorderLayout.CENTER);

                // 左侧下半部分：上下分割
                JSplitPane splitLeftBottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
                splitLeftBottom.setTopComponent(jp_adv_form);
                splitLeftBottom.setBottomComponent(jp_complex);
                splitLeftBottom.setDividerLocation(150);

                // 已配置列表
                JPanel jp_list = new JPanel(new GridLayout(6, 1, 2, 2));
                jp_list.setBorder(BorderFactory.createTitledBorder(" 已配置 "));
                JLabel jl_list_title = new JLabel("编号 | 模式 | URL");
                jl_list_title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                jp_list.add(jl_list_title);
                jl_captcha_urls = new JLabel[5];
                for (int i = 0; i < 5; i++) {
                    jl_captcha_urls[i] = new JLabel("  " + (i+1) + ": 未配置");
                    jl_captcha_urls[i].setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                    jp_list.add(jl_captcha_urls[i]);
                }

                jp_bottom_area.add(splitLeftBottom);
                jp_bottom_area.add(jp_list);

                // 组装左侧
                jp_left.add(jp_config, BorderLayout.NORTH);
                jp_left.add(jp_bottom_area, BorderLayout.CENTER);

                // ==================== 右侧面板(辅助) ====================
                JPanel jp_right = new JPanel(new BorderLayout(5, 5));
                jp_right.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                // --- 右上：控制区 ---
                JPanel jp_ctrl = new JPanel();
                jp_ctrl.setLayout(new BoxLayout(jp_ctrl, BoxLayout.Y_AXIS));

                // 插件信息
                JPanel jp_info = new JPanel(new GridLayout(3, 1));
                jp_info.setBorder(BorderFactory.createTitledBorder(" " + plugin_name + " V" + xp_version + " "));
                JLabel jl_name = new JLabel("Author: luckone");
                jl_name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                JLabel jl_desc = new JLabel("OCR + 大模型");
                JLabel jl_ver = new JLabel("验证码识别插件");
                jp_info.add(jl_name);
                jp_info.add(jl_desc);
                jp_info.add(jl_ver);

                // 监控设置
                JPanel jp_mon = new JPanel(new GridLayout(4, 1, 2, 2));
                jp_mon.setBorder(BorderFactory.createTitledBorder(" 监控 "));
                JCheckBox chkbox1 = new JCheckBox("启动插件", true);
                JCheckBox chkbox2 = new JCheckBox("Intruder");
                JCheckBox chkbox3 = new JCheckBox("Repeater");
                JCheckBox chkbox4 = new JCheckBox("Proxy");
                jp_mon.add(chkbox1);
                jp_mon.add(chkbox2);
                jp_mon.add(chkbox3);
                jp_mon.add(chkbox4);

                // 重试设置（使用标签而非文本框）
                JPanel jp_retry = new JPanel(new GridLayout(4, 1, 2, 2));
                jp_retry.setBorder(BorderFactory.createTitledBorder(" 重试 "));
                JCheckBox chkbox5 = new JCheckBox("错误重试");
                JLabel jl_retry_info = new JLabel(" 次数: 3");
                jl_retry_info.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                JLabel jl_retry_kw = new JLabel(" 关键词: 验证码错误");
                jl_retry_kw.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                jl_retry_kw.setToolTipText("验证码错误,验证码已失效,验证码不正确");
                jp_retry.add(chkbox5);
                jp_retry.add(jl_retry_info);
                jp_retry.add(jl_retry_kw);
                jp_retry.add(new JLabel(""));

                // 按钮
                JPanel jp_btn = new JPanel(new GridLayout(2, 1, 3, 3));
                JButton btn1 = new JButton("保存配置");
                btn1.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                btn1.setBackground(new Color(0, 120, 215));
                btn1.setForeground(Color.WHITE);
                btn1.setFocusPainted(false);
                JButton btn2 = new JButton("清空日志");
                jp_btn.add(btn1);
                jp_btn.add(btn2);

                // 组装右上
                jp_ctrl.add(jp_info);
                jp_ctrl.add(Box.createVerticalStrut(3));
                jp_ctrl.add(jp_mon);
                jp_ctrl.add(Box.createVerticalStrut(3));
                jp_ctrl.add(jp_retry);
                jp_ctrl.add(Box.createVerticalStrut(3));
                jp_ctrl.add(jp_btn);

                // --- 右下：日志 ---
                JPanel jp_log = new JPanel(new BorderLayout());
                jp_log.setBorder(BorderFactory.createTitledBorder(" 日志 "));
                jta = new JTextArea();
                jta.setLineWrap(true);
                jta.setEditable(false);
                jta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                jp_log.add(new JScrollPane(jta), BorderLayout.CENTER);

                jp_right.add(jp_ctrl, BorderLayout.NORTH);
                jp_right.add(jp_log, BorderLayout.CENTER);

                // ==================== 组装 ====================
                splitPane.setLeftComponent(jp_left);
                splitPane.setRightComponent(jp_right);

                // ==================== 事件 ====================
                jb_set.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        int idx = jb_set.getSelectedIndex() + 1;
                        jl_status.setText(" @killcap@" + idx + "@ ");
                        jl_adv_title.setText("验证码 " + idx + " 设置");
                        if (yzm_set_map.containsKey(idx)) {
                            List<String> d = yzm_set_map.get(idx);
                            txtfield_1.setText(d.get(0));
                            rb_url_1_1.setSelected(d.get(1).equals("1"));
                            rb_url_1_2.setSelected(d.get(1).equals("2"));
                            jb_set_case_sensitive.setSelectedIndex(Integer.parseInt(d.get(2)));
                            jta_1.setText(d.get(3));
                            jb_1.setSelectedIndex(Integer.parseInt(d.get(4)));
                            jps_txtfield_1.setText(d.get(5));
                            re_switch = d.get(6).equals("true");
                            jps_2_bt_1.setText(re_switch ? "关闭高级" : "开启高级");
                        } else {
                            txtfield_1.setText("");
                            jb_set_case_sensitive.setSelectedIndex(6);
                            rb_url_1_1.setSelected(true);
                            jl_hint.setText(" ");
                            jta_1.setText(dome);
                            jb_1.setSelectedIndex(0);
                            jps_txtfield_1.setText("\"uuid\":\"(.*?)\"");
                            re_switch = false;
                            jps_2_bt_1.setText("开启高级");
                        }
                    }
                });

                rb_url_1_1.addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            jta_1.setEditable(false);
                            jta_1.setBackground(new Color(245, 245, 245));
                        }
                    }
                });
                rb_url_1_2.addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            jta_1.setEditable(true);
                            jta_1.setBackground(Color.WHITE);
                        }
                    }
                });

                jps_2_bt_1.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        re_switch = !re_switch;
                        jps_2_bt_1.setText(re_switch ? "关闭高级" : "开启高级");
                        jps_2_4.setText("请保存配置");
                    }
                });

                jb_1.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        jps_txtfield_1.setText(jb_1.getSelectedIndex() == 1 ?
                            "Set-Cookie|SESSION=(.*?);" : "\"uuid\":\"(.*?)\"");
                    }
                });

                chkbox1.addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) { switchs = chkbox1.isSelected() ? 1 : 0; }
                });
                chkbox2.addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) { clicks_Intruder = chkbox2.isSelected() ? 32 : 0; }
                });
                chkbox3.addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) { clicks_Repeater = chkbox3.isSelected() ? 64 : 0; }
                });
                chkbox4.addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) { clicks_Proxy = chkbox4.isSelected() ? 4 : 0; }
                });
                chkbox5.addItemListener(new ItemListener() {
                    public void itemStateChanged(ItemEvent e) { retry_switch = chkbox5.isSelected() ? 1 : 0; }
                });

                btn1.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        try {
                            String[] hp = txtfield_0.getText().split(":");
                            XiaPao_api_HOST = hp[0];
                            XiaPao_api_Port = Integer.parseInt(hp[1]);
                        } catch (Exception ex) {
                            jta.insert("[!] 接口地址格式错误\n", 0);
                            return;
                        }
                        String url = txtfield_1.getText();
                        if (url.isEmpty()) { jta.insert("[!] URL为空\n", 0); return; }

                        String mode = rb_url_1_1.isSelected() ? "1" : "2";
                        String modeName = rb_url_1_1.isSelected() ? "简单" : "复杂";
                        int outIdx = jb_set_case_sensitive.getSelectedIndex();
                        int advSrc = jb_1.getSelectedIndex();
                        int idx = jb_set.getSelectedIndex() + 1;

                        List<String> cfg = new ArrayList<>();
                        cfg.add(url); cfg.add(mode); cfg.add(String.valueOf(outIdx));
                        cfg.add(jta_1.getText()); cfg.add(String.valueOf(advSrc));
                        cfg.add(jps_txtfield_1.getText()); cfg.add(String.valueOf(re_switch));
                        yzm_set_map.put(idx, cfg);

                        String shortUrl = url.length() > 30 ? url.substring(0, 30) + ".." : url;
                        jl_captcha_urls[idx-1].setText("  " + idx + ": " + modeName + " | " + shortUrl);
                        jl_hint.setText("已保存: 验证码 " + idx);
                        jl_hint.setForeground(new Color(0, 153, 255));
                        jta.insert("[*] 保存: 验证码 " + idx + " (" + modeName + ")\n", 0);
                    }
                });

                btn2.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) { jta.setText(""); }
                });

                callbacks.customizeUiComponent(splitPane);
                callbacks.addSuiteTab(BurpExtender.this);
                callbacks.registerHttpListener(BurpExtender.this);
                callbacks.registerContextMenuFactory(BurpExtender.this);
            }
        });
    }

    @Override public String getTabCaption() { return plugin_name; }
    @Override public Component getUiComponent() { return splitPane; }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo)
    {
        if (switchs != 1) return;
        if (toolFlag != clicks_Repeater && toolFlag != clicks_Intruder && toolFlag != clicks_Proxy) return;

        if (messageIsRequest) {
            String req = helpers.bytesToString(messageInfo.getRequest());
            if (req.indexOf("@killcap@") == -1) return;
            Matcher m = Pattern.compile("@killcap@(\\d)@").matcher(req);
            if (m.find()) xiapao_count = Integer.parseInt(m.group(1));
            originalRequests.put(messageInfo.hashCode(), messageInfo.getRequest());
            checkVul(messageInfo, xiapao_count);
        } else {
            if (retry_switch == 1 && messageInfo.getResponse() != null) {
                checkResponseForRetry(messageInfo);
            }
        }
    }

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        List<JMenuItem> menuItems = new ArrayList<>();
        JMenuItem item = new JMenuItem("发送到 killcap 验证码");
        item.addActionListener(e -> {
            IHttpRequestResponse[] selected = invocation.getSelectedMessages();
            if (selected == null || selected.length == 0) return;

            IHttpRequestResponse msg = selected[0];
            IRequestInfo info = helpers.analyzeRequest(msg);
            String url = info.getUrl().toString();

            // 切到当前验证码编号并填入URL
            int idx = jb_captcha_sel.getSelectedIndex() + 1;
            jtf_captcha_url.setText(url);

            jl_hint.setText("已导入: " + (url.length() > 50 ? url.substring(0, 50) + "..." : url));
            jl_hint.setForeground(new Color(0, 153, 255));
            jta.insert("[*] 导入验证码URL: " + url + "\n", 0);
        });
        menuItems.add(item);
        return menuItems;
    }

    private void checkVul(IHttpRequestResponse baseRequestResponse, int xiapao_count) {
        if (!yzm_set_map.containsKey(xiapao_count)) {
            jta.insert("[!] 验证码 " + xiapao_count + " 未配置\n", 0);
            return;
        }
        List<String> cfg = yzm_set_map.get(xiapao_count);
        String captcha_url = cfg.get(0);
        if (captcha_url.length() <= 1) {
            jta.insert("[!] 验证码 " + xiapao_count + " URL为空\n", 0);
            return;
        }

        List<String> headers = helpers.analyzeRequest(baseRequestResponse).getHeaders();
        String request = helpers.bytesToString(baseRequestResponse.getRequest());
        String cookies = "";
        for (String h : headers) { if (h.indexOf("Cookie") != -1) cookies = h; }

        String body = "xp_url=" + helpers.base64Encode(captcha_url) +
            "&xp_type=" + cfg.get(1) +
            "&xp_cookie=" + helpers.base64Encode(cookies.length() >= 1 ? cookies : "null=null;") +
            "&xp_set_ranges=" + cfg.get(2) +
            "&xp_complex_request=" + helpers.base64Encode(cfg.get(3)) +
            "&xp_rf=" + cfg.get(4) +
            "&xp_re=" + helpers.base64Encode(cfg.get(5)) +
            "&xp_is_re_run=" + cfg.get(6);

        List<String> h = new ArrayList<>();
        h.add("POST /imgurl HTTP/1.1");
        h.add("Host: " + XiaPao_api_HOST);

        IHttpService svc = helpers.buildHttpService(XiaPao_api_HOST, XiaPao_api_Port, "http");
        IHttpRequestResponse resp = callbacks.makeHttpRequest(svc, helpers.buildHttpMessage(h, body.getBytes()));

        if (resp.getResponse() == null || resp.getResponse().length == 0) {
            jta.insert("[!] OCR服务无响应\n", 0);
            return;
        }

        String[] lines = helpers.bytesToString(resp.getResponse()).split("\\n");
        String result = lines[lines.length - 1].replace("\r", "");
        String regex = "";

        if (cfg.get(6).equals("true")) {
            String[] p = result.split("\\|");
            result = p[0];
            regex = p.length > 1 ? p[1] : "";
            jta.insert("[+] 验证码" + xiapao_count + ": " + result + " | uuid: " + regex + "\n", 0);
        } else {
            jta.insert("[+] 验证码" + xiapao_count + ": " + result + "\n", 0);
        }

        IRequestInfo info = helpers.analyzeRequest(baseRequestResponse);
        String newBody = request.substring(info.getBodyOffset()).replaceAll("@killcap@\\d@", result);
        if (cfg.get(6).equals("true")) {
            newBody = newBody.replaceAll("@killcap@x@", regex);
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).indexOf("@killcap@x@") != -1)
                    headers.set(i, headers.get(i).replaceAll("@killcap@x@", regex));
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).indexOf("@killcap@") != -1)
                headers.set(i, headers.get(i).replaceAll("@killcap@\\d@", result));
        }
        baseRequestResponse.setRequest(helpers.buildHttpMessage(headers, newBody.getBytes(StandardCharsets.UTF_8)));
    }

    private void checkResponseForRetry(IHttpRequestResponse messageInfo) {
        try {
            String resp = helpers.bytesToString(messageInfo.getResponse());
            boolean err = false;
            for (String kw : retry_keywords.split(",")) {
                if (resp.contains(kw.trim())) { err = true; break; }
            }
            int hash = messageInfo.hashCode();
            if (err) {
                int cnt = retryCountMap.getOrDefault(hash, 0);
                if (cnt < retry_max) {
                    jta.insert("[!] 重试 " + (cnt+1) + "/" + retry_max + "\n", 0);
                    retryCountMap.put(hash, cnt + 1);
                    byte[] orig = originalRequests.get(hash);
                    if (orig != null) { messageInfo.setRequest(orig); checkVul(messageInfo, xiapao_count); }
                } else {
                    jta.insert("[!] 达到最大重试\n", 0);
                    retryCountMap.remove(hash);
                    originalRequests.remove(hash);
                }
            } else {
                retryCountMap.remove(hash);
                originalRequests.remove(hash);
            }
        } catch (Exception ex) { stdout.println("[-] 重试异常: " + ex.getMessage()); }
    }
}
