package com.orion.ops.module.asset.handler.host.terminal.entity.request;

import com.alibaba.fastjson.annotation.JSONField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 终端连接请求 实体对象
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2023/12/29 16:20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TerminalConnectRequest", description = "终端连接请求 实体对象")
public class TerminalConnectRequest {

    // 连接主机 {"t":"co","s": "1001","b":{"c":100,"r":20}}

    @JSONField(name = "c")
    @Schema(description = "列数")
    private Integer cols;

    @JSONField(name = "r")
    @Schema(description = "行数")
    private Integer rows;

}
