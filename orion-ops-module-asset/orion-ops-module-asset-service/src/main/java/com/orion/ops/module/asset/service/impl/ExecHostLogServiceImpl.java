package com.orion.ops.module.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orion.lang.utils.collect.Lists;
import com.orion.ops.framework.common.constant.ErrorMessage;
import com.orion.ops.framework.common.utils.Valid;
import com.orion.ops.module.asset.convert.ExecHostLogConvert;
import com.orion.ops.module.asset.dao.ExecHostLogDAO;
import com.orion.ops.module.asset.entity.domain.ExecHostLogDO;
import com.orion.ops.module.asset.entity.vo.ExecHostLogVO;
import com.orion.ops.module.asset.service.ExecHostLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 批量执行主机日志 服务实现类
 *
 * @author Jiahang Li
 * @version 1.0.1
 * @since 2024-3-11 14:05
 */
@Slf4j
@Service
public class ExecHostLogServiceImpl implements ExecHostLogService {

    @Resource
    private ExecHostLogDAO execHostLogDAO;

    @Override
    public List<ExecHostLogVO> getExecHostLogList(Long logId) {
        return execHostLogDAO.of()
                .createWrapper()
                .eq(ExecHostLogDO::getLogId, logId)
                .then()
                .list(ExecHostLogConvert.MAPPER::to);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer deleteExecHostLogByLogId(List<Long> logIdList) {
        log.info("ExecHostLogService-deleteExecHostLogByLogId logIdList: {}", logIdList);
        int effect = 0;
        if (Lists.isEmpty(logIdList)) {
            return effect;
        }
        // 分批次删除
        List<List<Long>> partitions = Lists.partition(logIdList, 500);
        for (List<Long> batch : partitions) {
            LambdaQueryWrapper<ExecHostLogDO> wrapper = execHostLogDAO.wrapper()
                    .in(ExecHostLogDO::getLogId, batch);
            effect += execHostLogDAO.delete(wrapper);
        }
        log.info("ExecHostLogService-deleteExecHostLogByLogId effect: {}", logIdList);
        return effect;
    }

    @Override
    public Integer deleteExecHostLogById(Long id) {
        log.info("ExecHostLogService-deleteExecHostLogById id: {}", id);
        // 检查数据是否存在
        ExecHostLogDO record = execHostLogDAO.selectById(id);
        Valid.notNull(record, ErrorMessage.DATA_ABSENT);
        // 删除
        int effect = execHostLogDAO.deleteById(id);
        log.info("ExecHostLogService-deleteExecHostLogById id: {}, effect: {}", id, effect);
        return effect;
    }

}
