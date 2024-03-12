package com.orion.ops.module.asset.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.orion.lang.id.UUIds;
import com.orion.lang.utils.Strings;
import com.orion.lang.utils.collect.Maps;
import com.orion.lang.utils.json.matcher.NoMatchStrategy;
import com.orion.lang.utils.json.matcher.ReplacementFormatter;
import com.orion.lang.utils.json.matcher.ReplacementFormatters;
import com.orion.lang.utils.time.Dates;
import com.orion.ops.framework.biz.operator.log.core.utils.OperatorLogs;
import com.orion.ops.framework.common.constant.Const;
import com.orion.ops.framework.common.constant.ErrorMessage;
import com.orion.ops.framework.common.file.FileClient;
import com.orion.ops.framework.common.security.LoginUser;
import com.orion.ops.framework.common.utils.Valid;
import com.orion.ops.framework.security.core.utils.SecurityUtils;
import com.orion.ops.module.asset.dao.ExecHostLogDAO;
import com.orion.ops.module.asset.dao.ExecLogDAO;
import com.orion.ops.module.asset.dao.HostDAO;
import com.orion.ops.module.asset.entity.domain.ExecHostLogDO;
import com.orion.ops.module.asset.entity.domain.ExecLogDO;
import com.orion.ops.module.asset.entity.domain.HostDO;
import com.orion.ops.module.asset.entity.request.exec.ExecCommandRequest;
import com.orion.ops.module.asset.entity.request.exec.ExecInterruptRequest;
import com.orion.ops.module.asset.entity.vo.ExecVO;
import com.orion.ops.module.asset.enums.ExecHostStatusEnum;
import com.orion.ops.module.asset.enums.ExecSourceEnum;
import com.orion.ops.module.asset.enums.ExecStatusEnum;
import com.orion.ops.module.asset.enums.HostConfigTypeEnum;
import com.orion.ops.module.asset.handler.host.exec.ExecTaskExecutors;
import com.orion.ops.module.asset.handler.host.exec.dto.ExecCommandDTO;
import com.orion.ops.module.asset.handler.host.exec.dto.ExecCommandHostDTO;
import com.orion.ops.module.asset.service.AssetAuthorizedDataService;
import com.orion.ops.module.asset.service.ExecService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 批量执行服务实现
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2024/3/11 12:03
 */
@Slf4j
@Service
public class ExecServiceImpl implements ExecService {

    private static final ReplacementFormatter FORMATTER = ReplacementFormatters.create("@{{ ", " }}")
            .noMatchStrategy(NoMatchStrategy.EMPTY);

    @Resource
    private FileClient logsFileClient;

    @Resource
    private ExecLogDAO execLogDAO;

    @Resource
    private ExecHostLogDAO execHostLogDAO;

    @Resource
    private HostDAO hostDAO;

    @Resource
    private AssetAuthorizedDataService assetAuthorizedDataService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExecVO execCommand(ExecCommandRequest request) {
        log.info("ExecService.startExecCommand start params: {}", JSON.toJSONString(request));
        LoginUser user = Objects.requireNonNull(SecurityUtils.getLoginUser());
        Long userId = user.getId();
        String command = request.getCommand();
        List<Long> hostIdList = request.getHostIdList();
        // 检查主机权限
        List<Long> authorizedHostIdList = assetAuthorizedDataService.getUserAuthorizedHostId(userId, HostConfigTypeEnum.SSH);
        hostIdList.removeIf(s -> !authorizedHostIdList.contains(s));
        Valid.notEmpty(hostIdList, ErrorMessage.CHECK_AUTHORIZED_HOST);
        List<HostDO> hosts = hostDAO.selectBatchIds(hostIdList);
        // 插入日志
        ExecLogDO execLog = ExecLogDO.builder()
                .userId(userId)
                .username(user.getUsername())
                .source(ExecSourceEnum.BATCH.name())
                .description(Strings.ifBlank(request.getDescription(), Strings.retain(command, 60) + Const.OMIT))
                .command(command)
                .timeout(request.getTimeout())
                .status(ExecStatusEnum.WAITING.name())
                .build();
        execLogDAO.insert(execLog);
        Long execId = execLog.getId();
        // 获取内置参数
        Map<String, Object> builtinsParams = getBaseBuiltinsParams(user, execId, request.getParameter());
        // 设置主机日志
        List<ExecHostLogDO> execHostLogs = hosts.stream()
                .map(s -> {
                    String parameter = JSON.toJSONString(this.getHostParams(builtinsParams, s));
                    return ExecHostLogDO.builder()
                            .logId(execId)
                            .hostId(s.getId())
                            .hostName(s.getName())
                            .status(ExecHostStatusEnum.WAITING.name())
                            .command(FORMATTER.format(command, parameter))
                            .parameter(parameter)
                            .logPath(this.buildLogPath(execId, s.getId()))
                            .build();
                }).collect(Collectors.toList());
        execHostLogDAO.insertBatch(execHostLogs);
        // 开始执行
        ExecCommandDTO exec = ExecCommandDTO.builder()
                .logId(execId)
                .timeout(request.getTimeout())
                .hosts(execHostLogs.stream()
                        .map(s -> ExecCommandHostDTO.builder()
                                .hostId(s.getHostId())
                                .hostLogId(s.getId())
                                .command(s.getCommand())
                                .timeout(request.getTimeout())
                                .logPath(s.getLogPath())
                                .build())
                        .collect(Collectors.toList()))
                .build();
        ExecTaskExecutors.start(exec);
        // 操作日志
        OperatorLogs.add(OperatorLogs.ID, execId);
        // 返回
        Map<String, Long> hostIdRel = execHostLogs.stream()
                .collect(Collectors.toMap(s -> String.valueOf(s.getHostId()), ExecHostLogDO::getId));
        return ExecVO.builder()
                .id(execId)
                .hostIdRel(hostIdRel)
                .build();
    }

    @Override
    public void interruptCommand(ExecInterruptRequest request) {

    }

    /**
     * 构建日志路径
     *
     * @param logId  logId
     * @param hostId hostId
     * @return logPath
     */
    private String buildLogPath(Long logId, Long hostId) {
        String logFile = "/exec/" + logId + "/" + hostId + ".log";
        return logsFileClient.getReturnPath(logFile);
    }

    /**
     * 获取基础内置参数
     *
     * @param user   user
     * @param execId execId
     * @return params
     */
    private Map<String, Object> getBaseBuiltinsParams(LoginUser user, Long execId, String inputParam) {
        String uuid = UUIds.random();
        Date date = new Date();
        // 输入参数
        JSONObject inputParams = JSON.parseObject(inputParam);
        // 内置参数
        Map<String, Object> params = Maps.newMap(inputParams);
        params.put("userId", user.getId());
        params.put("username", user.getId());
        params.put("execId", execId);
        params.put("uuid", uuid);
        params.put("uuidShort", uuid.replace("-", Strings.EMPTY));
        params.put("timestampMillis", date.getTime());
        params.put("timestamp", date.getTime() / Dates.SECOND_STAMP);
        params.put("date", Dates.format(date, Dates.YMD));
        params.put("datetime", Dates.format(date, Dates.YMD_HMS));
        return params;
    }

    /**
     * 获取主机参数
     *
     * @param baseParams baseParams
     * @param host       host
     * @return params
     */
    private Map<String, Object> getHostParams(Map<String, Object> baseParams, HostDO host) {
        String uuid = UUIds.random();
        Map<String, Object> params = Maps.newMap(baseParams);
        params.put("hostId", host.getId());
        params.put("hostName", host.getName());
        params.put("hostCode", host.getCode());
        params.put("hostAddress", host.getAddress());
        params.put("hostUuid", uuid);
        params.put("hostUuidShort", uuid.replace("-", Strings.EMPTY));
        return params;
    }

}
