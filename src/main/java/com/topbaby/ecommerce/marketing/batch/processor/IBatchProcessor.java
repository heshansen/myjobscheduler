/*
 * ================================================================
 * Copyright 2008-2015 AMT.
 * All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * AMT Corp. Ltd, ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with AMT.
 * 
 * 淘璞电商平台项目
 *
 * ================================================================
 *  创建人: fudaibao
 *	创建时间: 2016年1月3日 - 下午4:15:06
 */
package com.topbaby.ecommerce.marketing.batch.processor;

import java.util.Map;

/**
 * <p>
 *  批处理
 * </p>
 *
 * @author fudaibao
 *
 * @version 1.0.0
 *
 * @since 1.0.0
 *
 */
public interface IBatchProcessor {

	boolean process(Map<String,Object> param) throws Exception;
}
