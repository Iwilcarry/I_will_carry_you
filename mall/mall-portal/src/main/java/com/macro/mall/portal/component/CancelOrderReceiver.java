package com.macro.mall.portal.component;

import com.macro.mall.portal.service.OmsPortalOrderService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *  取消订单消息的接收者
 * Created by macro on 2018/9/14.
 */
@Component
@Slf4j
@RabbitListener(queues = "mall.order.cancel")
public class CancelOrderReceiver {

    @Autowired
    private OmsPortalOrderService portalOrderService;

    @RabbitHandler
    public void handle(Long orderId){
        portalOrderService.cancelOrder(orderId);
        log.info("process orderId:{}",orderId);
    }

//    private static final Logger LOGGER = LoggerFactory.getLogger(CancelOrderReceiver.class);
//    @Autowired
//    private OmsPortalOrderService portalOrderService;
//
//    @RabbitHandler
//    public void handle(Long orderId){
//        portalOrderService.cancelOrder(orderId);
//        LOGGER.info("process orderId:{}",orderId);
//    }
}
