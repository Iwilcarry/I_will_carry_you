package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 购物车管理Service实现类
 * Created by macro on 2018/8/2.
 */
@Service
public class OmsCartItemServiceImpl implements OmsCartItemService {
    @Autowired
    private OmsCartItemMapper cartItemMapper;
    @Autowired
    private PortalProductDao productDao;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public int add(OmsCartItem cartItem) {
        UmsMember umsMember = memberService.getCurrentMember();
        int count = 0;
        cartItem.setMemberId(umsMember.getId());
        cartItem.setMemberNickname(umsMember.getNickname());
        cartItem.setDeleteStatus(0);
        //查询购物车内是否有商品
        OmsCartItem product = getCartItem(cartItem);
        if (product == null){
            cartItem.setCreateDate(new Date());
            cartItemMapper.insert(cartItem);
        }else{
            cartItem.setModifyDate(new Date());
            cartItem.setQuantity(cartItem.getQuantity() + product.getQuantity());
            count = cartItemMapper.updateByPrimaryKey(product);
        }
        return count;
//        int count;
//        //从SpringSecurity中获取到当前用户
//        UmsMember currentMember =memberService.getCurrentMember();
//        cartItem.setMemberId(currentMember.getId());
//        cartItem.setMemberNickname(currentMember.getNickname());
//        cartItem.setDeleteStatus(0);
//        //查询该用户的购物车中是否有商品
//        OmsCartItem existCartItem = getCartItem(cartItem);
//        if (existCartItem == null) {
//            //设置创建时间
//            cartItem.setCreateDate(new Date());
//            //插入到数据库中
//            count = cartItemMapper.insert(cartItem);
//        } else {
//            //设置修改时间
//            cartItem.setModifyDate(new Date());
//            //设置购买数量
//            existCartItem.setQuantity(existCartItem.getQuantity() + cartItem.getQuantity());
//            //插入到数据库中
//            count = cartItemMapper.updateByPrimaryKey(existCartItem);
//        }
//        return count;
    }

    /**
     * 根据会员id,商品id和规格获取购物车中商品
     */
    private OmsCartItem getCartItem(OmsCartItem cartItem) {
        OmsCartItemExample example = new OmsCartItemExample();
        OmsCartItemExample.Criteria criteria = example.createCriteria().andMemberIdEqualTo(cartItem.getMemberId()).andProductIdEqualTo(cartItem.getProductId()).andDeleteStatusEqualTo(0);
        //根据商品规格添加到条件构造器中
        if(cartItem.getProductSkuId() != null){
            criteria.andProductSkuIdEqualTo(cartItem.getProductSkuId());
        }
        List<OmsCartItem> list = cartItemMapper.selectByExample(example);
        if (list != null){
            return list.get(0);
        }
        return null;


//        OmsCartItemExample example = new OmsCartItemExample();
//        OmsCartItemExample.Criteria criteria = example.createCriteria().andMemberIdEqualTo(cartItem.getMemberId())
//                .andProductIdEqualTo(cartItem.getProductId()).andDeleteStatusEqualTo(0);
//        if (cartItem.getProductSkuId()!=null) {
//            criteria.andProductSkuIdEqualTo(cartItem.getProductSkuId());
//        }
//        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
//        if (!CollectionUtils.isEmpty(cartItemList)) {
//            return cartItemList.get(0);
//        }
//        return null;
    }

    @Override
    public List<OmsCartItem> list(Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andMemberIdEqualTo(memberId);
        return cartItemMapper.selectByExample(example);
    }

    @Override
    public List<CartPromotionItem> listPromotion(Long memberId, List<Long> cartIds) {
        List<OmsCartItem> cartItemList = list(memberId);
        if(CollUtil.isNotEmpty(cartIds)){
            cartItemList = cartItemList.stream().filter(item->cartIds.contains(item.getId())).collect(Collectors.toList());
        }
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        if(!CollectionUtils.isEmpty(cartItemList)){
            cartPromotionItemList = promotionService.calcCartPromotion(cartItemList);
        }
        return cartPromotionItemList;
    }

    @Override
    public int updateQuantity(Long id, Long memberId, Integer quantity) {
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setQuantity(quantity);
        OmsCartItemExample example = new OmsCartItemExample();
        //条件
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(cartItem, example);
    }

    @Override
    public int delete(Long memberId, List<Long> ids) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        //将商品状态设置为1
        example.createCriteria().andIdIn(ids).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record, example);
    }

    @Override
    public CartProduct getCartProduct(Long productId) {
        return productDao.getCartProduct(productId);
    }

    @Override
    public int updateAttr(OmsCartItem cartItem) {
        //删除原购物车信息
        OmsCartItem updateCart = new OmsCartItem();
        //先将购物车内的消息删除
        updateCart.setId(cartItem.getId());
        updateCart.setModifyDate(new Date());
        updateCart.setDeleteStatus(1);
        cartItemMapper.updateByPrimaryKeySelective(updateCart);
        cartItem.setId(null);
        //插入新的数据
        add(cartItem);
        return 1;
    }

    @Override
    public int clear(Long memberId) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andMemberIdEqualTo(memberId);
        //只需要将状态设置为1就行了
        return cartItemMapper.updateByExampleSelective(record,example);
    }
}
