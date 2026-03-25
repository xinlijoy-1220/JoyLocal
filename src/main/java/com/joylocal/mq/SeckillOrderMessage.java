package com.joylocal.mq;

import com.joylocal.entity.VoucherOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SeckillOrderMessage {
    private Long orderId;
    private Long userId;
    private Long voucherId;

    public VoucherOrder toVoucherOrder() {
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        return voucherOrder;
    }
}
