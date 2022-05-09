package com.dy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dy.common.R;
import com.dy.entity.OrderDetail;

import java.util.List;

public interface OrderDetailService extends IService<OrderDetail> {
    /**
     * 查询订单详细信息
     * @param id
     * @return
     */
    R<List<OrderDetail>> getByOrderId(Long id);
}
