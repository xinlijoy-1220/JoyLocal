package com.joylocal.listener;

import cn.hutool.json.JSONUtil;
import com.joylocal.mq.MqConstants;
import com.joylocal.mq.OrderTimeoutMessage;
import com.joylocal.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
        topic = MqConstants.ORDER_TIMEOUT_TOPIC,
        selectorExpression = MqConstants.ORDER_TIMEOUT_CHECK_TAG,
        consumerGroup = MqConstants.ORDER_TIMEOUT_CONSUMER_GROUP,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final IVoucherOrderService voucherOrderService;

    @Override
    public void onMessage(String message) {
        OrderTimeoutMessage timeoutMessage = JSONUtil.toBean(message, OrderTimeoutMessage.class);
        log.info("Received timeout check message, orderId={}", timeoutMessage.getOrderId());
        voucherOrderService.cancelUnpaidOrder(timeoutMessage.getOrderId());
    }
}
