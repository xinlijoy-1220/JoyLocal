package com.joylocal.service;

import com.joylocal.dto.Result;
import com.joylocal.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    boolean createVoucherOrder(VoucherOrder voucherOrder);

    boolean handleVoucherOrder(VoucherOrder voucherOrder);

    Result payVoucherOrder(Long orderId);

    void cancelUnpaidOrder(Long orderId);
}
