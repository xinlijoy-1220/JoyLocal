package com.joylocal.mq;

public final class MqConstants {

    private MqConstants() {
    }

    public static final String SECKILL_ORDER_TOPIC = "joylocal-seckill-order";
    public static final String SECKILL_ORDER_CREATE_TAG = "create";
    public static final String SECKILL_ORDER_DESTINATION = SECKILL_ORDER_TOPIC + ":" + SECKILL_ORDER_CREATE_TAG;
    public static final String SECKILL_ORDER_CONSUMER_GROUP = "joylocal-seckill-order-consumer";

    public static final String ORDER_TIMEOUT_TOPIC = "joylocal-order-timeout";
    public static final String ORDER_TIMEOUT_CHECK_TAG = "check";
    public static final String ORDER_TIMEOUT_DESTINATION = ORDER_TIMEOUT_TOPIC + ":" + ORDER_TIMEOUT_CHECK_TAG;
    public static final String ORDER_TIMEOUT_CONSUMER_GROUP = "joylocal-order-timeout-consumer";

    public static final long ORDER_AUTO_CANCEL_MILLIS = 15 * 60 * 1000L;
}
