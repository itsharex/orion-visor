package com.orion.ops.module.asset.handler.host.terminal.session;

import com.alibaba.fastjson.JSON;
import com.orion.lang.utils.awt.Clipboards;
import com.orion.lang.utils.io.Streams;
import com.orion.net.host.SessionStore;
import com.orion.net.host.ssh.TerminalType;
import com.orion.net.host.ssh.shell.ShellExecutor;
import com.orion.ops.framework.common.constant.Const;
import com.orion.ops.framework.websocket.core.utils.WebSockets;
import com.orion.ops.module.asset.define.AssetThreadPools;
import com.orion.ops.module.asset.handler.host.terminal.entity.Message;
import com.orion.ops.module.asset.handler.host.terminal.enums.OutputOperatorTypeEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 终端会话
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2024/1/2 17:28
 */
@Slf4j
public class TerminalSession implements ITerminalSession {

    @Getter
    private final String token;

    @Getter
    private final WebSocketSession session;

    private final SessionStore sessionStore;

    private ShellExecutor executor;

    private volatile boolean close;

    public TerminalSession(String token,
                           WebSocketSession session,
                           SessionStore sessionStore) {
        this.token = token;
        this.session = session;
        this.sessionStore = sessionStore;
    }

    @Override
    public void connect(int cols, int rows) {
        this.executor = sessionStore.getShellExecutor();
        executor.terminalType(TerminalType.XTERM_256_COLOR);
        executor.size(cols, rows);
        executor.streamHandler(this::streamHandler);
        executor.connect();
        // 开始监听输出
        AssetThreadPools.TERMINAL_SCHEDULER.execute(executor);
    }

    @Override
    public void resize(int cols, int rows) {
        if (!executor.isConnected()) {
            executor.connect();
        }
        executor.size(cols, rows);
        executor.resize();
    }

    @Override
    public void write(String b) {
        executor.write(b);
    }

    @Override
    public void write(byte[] b) {
        executor.write(b);
    }

    @Override
    public void close() {
        if (close) {
            return;
        }
        this.close = true;
        try {
            Streams.close(executor);
            Streams.close(sessionStore);
        } catch (Exception e) {
            log.error("terminal 断开连接 失败 token: {}, {}", token, e);
        }
    }

    /**
     * 标准输出处理
     *
     * @param inputStream stream
     */
    private void streamHandler(InputStream inputStream) {
        byte[] bs = new byte[Const.BUFFER_KB_4];
        BufferedInputStream in = new BufferedInputStream(inputStream, Const.BUFFER_KB_4);
        int read;
        try {
            while (session.isOpen() && (read = in.read(bs)) != -1) {
                // 响应
                String body = new String(bs, 0, read, StandardCharsets.UTF_8);
                // TODO lastline
                Message<?> msg = Message.builder()
                        .session(token)
                        .type(OutputOperatorTypeEnum.OUTPUT.getType())
                        // FIXME TERMINAL charset
                        .body(body)
                        .build();
                WebSockets.sendJson(session, msg);
            }
        } catch (IOException ex) {
            log.error("terminal 读取流失败", ex);
        }
        // eof
        if (close) {
            return;
        }
        log.info("terminal eof回调 {}", token);
    }

}
