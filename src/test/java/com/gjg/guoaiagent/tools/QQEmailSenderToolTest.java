package com.gjg.guoaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.mail.MessagingException;

@SpringBootTest
public class QQEmailSenderToolTest {

    @Autowired
    QQEmailSenderTool qqEmailSenderTool;

    @Test
    void sendTextEmail() {
        try {
            String s;
            s = qqEmailSenderTool.sendTextEmail(
                    "guojiageng1024@163.com",  // 替换为目标邮箱
                    "测试邮件",
                    "这是一封通过Java发送的测试邮件。"
            );
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    @Test
    void sendHtmlEmail() {
        try {
            String html = "<h2>🎉 这是一封HTML测试邮件</h2>" +
                          "<p><b>加粗内容</b>，<a href='https://example.com'>点击链接</a></p>";
            qqEmailSenderTool.sendHtmlEmail("recipient@example.com", "HTML测试", html);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
