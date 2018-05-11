package com.topbaby.ecommerce.marketing.batch.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.ReportingPolicy;

import com.topbaby.ecommerce.marketing.entity.EcoGiftDetail;
import com.topbaby.ecommerce.marketing.entity.dto.EcoGiftDetailDTO;

/**
 * 
 * 生态圈实体转换
 * 
 * 
 * 
 * @author heshansen
 * 
 * @date 2017年5月5日 上午10:23:19
 * 
 * @version v2.3.2
 * 
 */

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EcosphereGiftDetailExportMapper {

    /**
     * 
     * 生态圈赠品明细DTO 转化
     * 
     * @param ecoGiftDetail
     * 
     * @param collectUser
     * 
     * @return 设定文件
     * 
     */

    @Mappings({
            @Mapping(source = "ecoGiftDetail.merchantGift.name", target = "giftName", defaultValue = "无"),
            @Mapping(source = "ecoGiftDetail.merchantGift.costPrice", target = "costPrice", numberFormat = "$#.00"),
            @Mapping(source = "ecoGiftDetail.brandshop.companyName", target = "companyName",defaultValue = "无"),
            @Mapping(source = "ecoGiftDetail.brandshop.name", target = "brandshopName", defaultValue = "无"),
            @Mapping(source = "ecoGiftDetail.brandshopUser.salesName", target = "salesName", defaultValue = "无"),
            @Mapping(source = "ecoGiftDetail.brandshopUser.cellphone", target = "salesPhone", defaultValue = "无"),
            @Mapping(source = "ecoGiftDetail.member.mobile", target = "memberMobile", defaultValue = "无"),
            @Mapping(source = "ecoGiftDetail.id", target = "id"),
            @Mapping(source = "ecoGiftDetail.reservePhone", target = "reservePhone", defaultValue = "无"),
            @Mapping(source = "ecoGiftDetail.receivedTime", target = "receivedTime"),
            @Mapping(source = "ecoGiftDetail.reserveTime", target = "reserveTime"),
            @Mapping(source = "ecoGiftDetail.reserveShopTime", target = "reserveShopTime"),
            @Mapping(source = "ecoGiftDetail.updateDate", target = "updateDate"),
            @Mapping(source = "ecoGiftDetail.memberGiftPacks.orderId", target = "orderId"),
            @Mapping(target = "giftType", expression="java(com.topbaby.ecommerce.gift.enums.GiftType.getName(ecoGiftDetail.getMerchantGift().getType()))"),
            @Mapping(target="status", expression="java(com.topbaby.ecommerce.enums.EcoGiftDetailUiStatus.getUiStatusName(ecoGiftDetail.getStatus()))"),
    })
    EcoGiftDetailDTO toEcoGiftDetailDTO(EcoGiftDetail ecoGiftDetail);

    /**
     * 
     * TODO(这里用一句话描述这个方法的作用)
     * 
     * @param details
     * 
     * @return 设定文件
     * 
     */

    List<EcoGiftDetailDTO> toEcoGiftDetailDTOs(List<EcoGiftDetail> details);

}
