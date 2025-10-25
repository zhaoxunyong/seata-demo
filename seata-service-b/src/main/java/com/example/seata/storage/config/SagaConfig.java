package com.example.seata.storage.config;

import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import io.seata.saga.engine.impl.DefaultStateMachineConfig;
import io.seata.saga.rm.StateMachineEngineHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

/**
 * Saga模式配置类
 */
@Configuration
public class SagaConfig {

    /**
     * 状态机引擎
     *
     * @return 状态机引擎
     */
    @Bean
    public StateMachineEngine stateMachineEngine() throws IOException {
        ProcessCtrlStateMachineEngine engine = new ProcessCtrlStateMachineEngine();
        DefaultStateMachineConfig config = new DefaultStateMachineConfig();
        config.setResources(new PathMatchingResourcePatternResolver().getResources("classpath*:saga/*.json"));
        engine.setStateMachineConfig(config);
        return engine;
    }

    /**
     * 状态机引擎持有者
     *
     * @param stateMachineEngine 状态机引擎
     * @return 状态机引擎持有者
     */
    @Bean
    public StateMachineEngineHolder stateMachineEngineHolder(StateMachineEngine stateMachineEngine) {
        StateMachineEngineHolder holder = new StateMachineEngineHolder();
        holder.setStateMachineEngine(stateMachineEngine);
        return holder;
    }
}