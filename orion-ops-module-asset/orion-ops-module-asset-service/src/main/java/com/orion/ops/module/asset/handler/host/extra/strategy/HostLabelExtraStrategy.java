package com.orion.ops.module.asset.handler.host.extra.strategy;

import com.orion.ops.framework.common.constant.Const;
import com.orion.ops.framework.common.handler.data.strategy.MapDataStrategy;
import com.orion.ops.module.asset.handler.host.extra.model.HostLabelExtraModel;
import org.springframework.stereotype.Component;

/**
 * 主机拓展信息 - 标签模型处理策略
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2024/2/29 23:16
 */
@Component
public class HostLabelExtraStrategy implements MapDataStrategy<HostLabelExtraModel> {

    @Override
    public HostLabelExtraModel getDefault() {
        return HostLabelExtraModel.builder()
                // 透明
                .color(Const.EMPTY)
                // 无别名
                .alias(Const.EMPTY)
                .build();
    }

    @Override
    public void updateFill(HostLabelExtraModel beforeModel, HostLabelExtraModel afterModel) {
        // 为空则覆盖
        if (afterModel.getAlias() == null) {
            afterModel.setAlias(beforeModel.getAlias());
        }
        if (afterModel.getColor() == null) {
            afterModel.setColor(beforeModel.getColor());
        }
    }

    @Override
    public void preValid(HostLabelExtraModel model) {
    }

    @Override
    public void valid(HostLabelExtraModel model) {
    }

}
