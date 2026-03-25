package com.joylocal.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joylocal.dto.Result;
import com.joylocal.entity.VoucherOrder;
import com.joylocal.mapper.VoucherOrderMapper;
import com.joylocal.mq.RocketMqProducer;
import com.joylocal.mq.SeckillOrderMessage;
import com.joylocal.service.ISeckillVoucherService;
import com.joylocal.service.IVoucherOrderService;
import com.joylocal.utils.RedisIdWorker;
import com.joylocal.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

import static com.joylocal.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.joylocal.utils.RedisConstants.SECKILL_STOCK_KEY;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final int ORDER_STATUS_PENDING_PAYMENT = 1;
    private static final int ORDER_STATUS_PAID = 2;
    private static final int ORDER_STATUS_CANCELED = 4;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RocketMqProducer rocketMqProducer;

    @Lazy
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        int code = result == null ? -1 : result.intValue();
        if (code != 0) {
            if (code == 1) {
                return Result.fail("Insufficient stock");
            }
            if (code == 2) {
                return Result.fail("Duplicate order is not allowed");
            }
            return Result.fail("Seckill validation failed");
        }

        SeckillOrderMessage orderMessage = new SeckillOrderMessage();
        orderMessage.setOrderId(orderId);
        orderMessage.setUserId(userId);
        orderMessage.setVoucherId(voucherId);
        try {
            rocketMqProducer.sendSeckillOrderMessage(orderMessage);
        } catch (Exception e) {
            restoreRedisReservation(voucherId, userId);
            log.error("Failed to send seckill order message, orderId={}", orderId, e);
            return Result.fail("System busy, please retry later");
        }
        return Result.ok(orderId);
    }

    @Override
    public boolean handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.warn("Duplicate create request ignored, orderId={}, userId={}", voucherOrder.getId(), userId);
            return false;
        }
        try {
            return voucherOrderService.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public boolean createVoucherOrder(VoucherOrder voucherOrder) {
        VoucherOrder existedOrder = getById(voucherOrder.getId());
        if (existedOrder != null) {
            return false;
        }

        int count = query()
                .eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .ne("status", ORDER_STATUS_CANCELED)
                .count();
        if (count > 0) {
            log.warn("User already ordered this voucher, userId={}, voucherId={}",
                    voucherOrder.getUserId(), voucherOrder.getVoucherId());
            return false;
        }

        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            restoreRedisReservation(voucherOrder.getVoucherId(), voucherOrder.getUserId());
            log.warn("DB stock deduction failed, rollback redis reservation, voucherId={}", voucherOrder.getVoucherId());
            return false;
        }

        voucherOrder.setStatus(ORDER_STATUS_PENDING_PAYMENT);
        if (voucherOrder.getPayType() == null) {
            voucherOrder.setPayType(1);
        }
        boolean saved = save(voucherOrder);
        if (!saved) {
            throw new IllegalStateException("Failed to persist voucher order");
        }
        return true;
    }

    @Override
    @Transactional
    public Result payVoucherOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder voucherOrder = getById(orderId);
        if (voucherOrder == null || !Objects.equals(voucherOrder.getUserId(), userId)) {
            return Result.fail("Order not found");
        }
        boolean updated = lambdaUpdate()
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getStatus, ORDER_STATUS_PENDING_PAYMENT)
                .set(VoucherOrder::getStatus, ORDER_STATUS_PAID)
                .set(VoucherOrder::getPayTime, LocalDateTime.now())
                .update();
        if (!updated) {
            return Result.fail("Order cannot be paid");
        }
        return Result.ok();
    }

    @Override
    @Transactional
    public void cancelUnpaidOrder(Long orderId) {
        VoucherOrder voucherOrder = getById(orderId);
        if (voucherOrder == null) {
            return;
        }
        if (!Objects.equals(voucherOrder.getStatus(), ORDER_STATUS_PENDING_PAYMENT)) {
            log.info("Skip timeout cancel, orderId={}, status={}", orderId, voucherOrder.getStatus());
            return;
        }

        boolean canceled = lambdaUpdate()
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getStatus, ORDER_STATUS_PENDING_PAYMENT)
                .set(VoucherOrder::getStatus, ORDER_STATUS_CANCELED)
                .update();
        if (!canceled) {
            return;
        }

        seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .update();
        restoreRedisReservation(voucherOrder.getVoucherId(), voucherOrder.getUserId());
        log.info("Canceled unpaid order and rolled back stock, orderId={}", orderId);
    }

    private void restoreRedisReservation(Long voucherId, Long userId) {
        stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
        stringRedisTemplate.opsForSet().remove(SECKILL_ORDER_KEY + voucherId, userId.toString());
    }
}
