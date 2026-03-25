package com.joylocal.mq;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RocketMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void sendSeckillOrderMessage(SeckillOrderMessage message) {
        rocketMQTemplate.syncSend(MqConstants.SECKILL_ORDER_DESTINATION, JSONUtil.toJsonStr(message));
        log.info("Sent seckill order message, orderId={}", message.getOrderId());
    }

    public void sendOrderTimeoutMessage(Long orderId) {
        long deliverTime = System.currentTimeMillis() + MqConstants.ORDER_AUTO_CANCEL_MILLIS;
        rocketMQTemplate.syncSendDeliverTimeMills(
                MqConstants.ORDER_TIMEOUT_DESTINATION,
                JSONUtil.toJsonStr(new OrderTimeoutMessage(orderId)),
                deliverTime
        );
        log.info("Sent order timeout message, orderId={}, deliverTime={}", orderId, deliverTime);
    }
}
