package com.joylocal.listener;

import cn.hutool.json.JSONUtil;
import com.joylocal.entity.VoucherOrder;
import com.joylocal.mq.MqConstants;
import com.joylocal.mq.RocketMqProducer;
import com.joylocal.mq.SeckillOrderMessage;
import com.joylocal.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
        topic = MqConstants.SECKILL_ORDER_TOPIC,
        selectorExpression = MqConstants.SECKILL_ORDER_CREATE_TAG,
        consumerGroup = MqConstants.SECKILL_ORDER_CONSUMER_GROUP,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING
)
public class SeckillOrderConsumer implements RocketMQListener<String> {

    private static final int ORDER_STATUS_PENDING_PAYMENT = 1;

    private final IVoucherOrderService voucherOrderService;
    private final RocketMqProducer rocketMqProducer;

    @Override
    public void onMessage(String message) {
        SeckillOrderMessage orderMessage = JSONUtil.toBean(message, SeckillOrderMessage.class);
        log.info("Received seckill order message, orderId={}", orderMessage.getOrderId());
        boolean created = voucherOrderService.handleVoucherOrder(orderMessage.toVoucherOrder());
        VoucherOrder persistedOrder = voucherOrderService.getById(orderMessage.getOrderId());
        if (created || (persistedOrder != null
                && Objects.equals(persistedOrder.getStatus(), ORDER_STATUS_PENDING_PAYMENT))) {
            rocketMqProducer.sendOrderTimeoutMessage(orderMessage.getOrderId());
        }
    }
}
