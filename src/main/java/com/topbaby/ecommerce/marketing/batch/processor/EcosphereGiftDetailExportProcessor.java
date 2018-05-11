package com.topbaby.ecommerce.marketing.batch.processor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.qiniu.common.QiniuException;
import com.qiniu.common.Zone;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import com.topbaby.ecomerce.utils.JacksonUtils;
import com.topbaby.ecommerce.common.entity.QiniuFile;
import com.topbaby.ecommerce.common.service.IQiniuFileService;
import com.topbaby.ecommerce.enums.EcoGiftUserField;
import com.topbaby.ecommerce.gift.entity.CollectAttributeValue;
import com.topbaby.ecommerce.marketing.batch.mapper.EcosphereGiftDetailExportMapper;
import com.topbaby.ecommerce.marketing.entity.CollectAttributeValueExport;
import com.topbaby.ecommerce.marketing.entity.dto.CollectUserDTO;
import com.topbaby.ecommerce.marketing.entity.dto.EcoGiftDetailDTO;
import com.topbaby.ecommerce.marketing.service.EcosystemStatisticsService;

/**
 * 
 * 定时导出当月生态圈赠品领取明细数据
 * 
 * @author heshansen
 * 
 * @date 2017年6月23日 上午10:53:52
 * 
 * @version v2.4.1
 * 
 */

public class EcosphereGiftDetailExportProcessor implements IBatchProcessor {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private SimpleDateFormat firstDayDf = new SimpleDateFormat("yyyy-MM-01 00:00:00");

    private SimpleDateFormat monthDf = new SimpleDateFormat("yyyy-MM");
    
    private SimpleDateFormat fullDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 如果是Windows情况下，格式是 D:\\qiniu\\uploadFiles\\fileName
    @Value("${emall.topbaby.qiniu.upload.exportPath:/opt/topbaby/reports/oms/}")
    private String exportPath;
    
    private final String PreFix_OMS = "oms/export/marketing/ecosphere/";

    /** 七牛云配置 0-测试空间 1-生产空间 n-其他空间 */
    @Value("${emall.topbaby.qiniu.upload.type:0}")
    private String type;

    @Autowired
    private EcosystemStatisticsService statisticsService;

    @Autowired
    private EcosphereGiftDetailExportMapper ecosphereGiftDetailExportMapper;

    @Autowired
    private IQiniuFileService qiniuFileService;

    /**
     * 
     * 导出当月生态圈赠品领取明细数据
     * 
     * @param param
     * 
     * @return
     * 
     * @throws ParseException
     * 
     * @throws Exception
     *             设定文件
     * 
     */
    @Override
    public boolean process(Map<String, Object> param) {
        logger.info("导出当月生态圈赠品领取明细数据开始...");
        Map<String, Object> searchParam = new HashMap<String, Object>();

        // 赠品类型（新旧版赠品明细区分字段）
        searchParam.put("giftTypeKey", "=");
        searchParam.put("giftpack", "giftpack");
        // 当月时间范围（1号0点0分0秒--当前时间点）
        Date now = new Date();
        try {
            searchParam.put("receivedBeginTime", firstDayDf.parse(firstDayDf.format(now)));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        searchParam.put("receivedEndTime", now);

        List<EcoGiftDetailDTO> list = this.ecosphereGiftDetailExportMapper.toEcoGiftDetailDTOs(this.statisticsService.getGiftDetailsList(searchParam));
        CollectUserDTO userData = this.toCollectUserDTO(list);
        logger.info("生成报表文件开始...");
        String fileName = this.writeToExlFile(list, userData);
        if (StringUtils.isNotEmpty(fileName)) {
            logger.info("上传报表开始...");
            File file = new File(this.exportPath + fileName);
            if (file.exists()) {
                DefaultPutRet putRet = this.uploadToQiniu(fileName);
                logger.info("导出任务结束。");
                return true;
            }
        }
        logger.error("导出任务失败！");
        return false;
    }

    /**
     * 
     * 上传报表到七牛云
     * 
     * @param fileName
     * 
     * @return 设定文件
     * 
     */
    private DefaultPutRet uploadToQiniu(String fileName) {
        // 构造一个带指定Zone对象的配置类
        Configuration cfg = new Configuration(Zone.zone0());
        // ...其他参数参考类注释
        UploadManager uploadManager = new UploadManager(cfg);
        
        // ...生成上传凭证，然后准备上传
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("type", type);
        param.put("property", "private");
        QiniuFile qiniuCfg = qiniuFileService.getQiniuConfig(param);
        String accessKey = qiniuCfg.getAccessKey();
        String secretKey = qiniuCfg.getSecretKey();
        String bucket = qiniuCfg.getBucket();
        String localFilePath = this.exportPath + fileName;
        
        // 默认不指定key的情况下，以文件内容的hash值作为文件名
        String key = this.PreFix_OMS+fileName;
        Auth auth = Auth.create(accessKey, secretKey);
        String upToken = auth.uploadToken(bucket,key);
        try {
            Response response = uploadManager.put(localFilePath, key, upToken);
            // 解析上传成功的结果
            logger.info("上传报表返回："+response.bodyString());
            DefaultPutRet putRet = JacksonUtils.getInstance().readValue(response.bodyString(),DefaultPutRet.class);//将建json对象转换为DefaultPutRet对象
            logger.info("上传报表成功！result:key=" + putRet.key + ";hash=" + putRet.hash);
            return putRet;
        } catch (QiniuException ex) {
            Response r = ex.response;
            logger.error("上传报表失败：" + r.toString());
            return null;
        } catch (Exception e) {
            logger.error("解析上传结果失败：" +e.getMessage());
            return null;
        }
    }

    /**
     * 
     * 生成报表文件
     * 
     * @param list
     * 
     * @param userData
     *            设定文件
     * 
     */
    private String writeToExlFile(List<EcoGiftDetailDTO> list, CollectUserDTO userData) {
        String currentMonth = monthDf.format(new Date());
        String filename = "生态圈赠品领取明细报表_" + currentMonth + ".xls";
        HSSFWorkbook workbook = new HSSFWorkbook();

        // 标题样式
        HSSFFont titleFont  = workbook.createFont();
        titleFont.setFontName("黑体");
        titleFont.setFontHeightInPoints((short) 18);//设置字体大小
        titleFont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);//粗体显示
        HSSFCellStyle titleStyle = workbook.createCellStyle(); // 样式对象
        titleStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_CENTER);//垂直居中
        titleStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER);// 水平居中
        titleStyle.setFont(titleFont);

        // 表头样式
        HSSFFont headerFont  = workbook.createFont();
        headerFont.setFontName("宋体");
        headerFont.setFontHeightInPoints((short) 12);//设置字体大小
        headerFont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);//粗体显示
        HSSFCellStyle headerStyle = workbook.createCellStyle(); // 样式对象
        headerStyle.setBorderBottom(HSSFCellStyle.BORDER_THIN);// 下边框
        headerStyle.setBorderLeft(HSSFCellStyle.BORDER_THIN);// 左边框
        headerStyle.setBorderRight(HSSFCellStyle.BORDER_THIN);// 右边框
        headerStyle.setBorderTop(HSSFCellStyle.BORDER_THIN);// 上边框
        headerStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_CENTER);//垂直居中
        headerStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER);// 水平居中
        headerStyle.setFont(headerFont);
        
        //数据行样式
        HSSFFont dataFont = workbook.createFont();
        dataFont.setFontName("宋体");
        dataFont.setFontHeightInPoints((short) 12);//设置字体大小
        HSSFCellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_CENTER);//垂直居中
        dataStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER);// 水平居中
        headerStyle.setFont(dataFont);
        
        // 创建第一个表单
        HSSFSheet sheet = workbook.createSheet("赠品明细表");
        sheet.setDefaultRowHeightInPoints(16);
        sheet.setDefaultColumnWidth(15);
        String title = "生态圈赠品领取明细报表-" + currentMonth;
        String[] headers = { "ID", "赠品名称", "赠品类型", "赠品成本", "公司名称","门店名称", "导购员姓名", "导购员手机号","订单编号", "会员手机号", "预约手机号", "领取时间", "预约时间", "预约到店时间", "兑换时间", "状态" };
        // 第一行标题
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length-1));
        HSSFRow titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(24);
        HSSFCell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);
        // 第二行表头
        HSSFRow headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            HSSFCell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(headers[i]);
            headerCell.setCellStyle(headerStyle);
        }
        // 第三行及之后的数据
        int rowNum = 2;
        for (EcoGiftDetailDTO detailDTO : list) {
            if (detailDTO != null) {
                HSSFRow dataRow = sheet.createRow(rowNum++);
                for (int i = 0; i < headers.length; i++) {
                    HSSFCell dataCell = dataRow.createCell(i);
                    dataCell.setCellStyle(dataStyle);
                    if (i==0) {
                        dataCell.setCellValue(detailDTO.getId());
                    }else if (i==1) {
                        dataCell.setCellValue(detailDTO.getGiftName());
                    }else if (i==2) {
                        dataCell.setCellValue(detailDTO.getGiftType());
                    }else if (i==3) {
                        dataCell.setCellValue(detailDTO.getCostPrice().doubleValue());
                    }else if (i==4) {
                        dataCell.setCellValue(detailDTO.getCompanyName());
                    }else if (i==5) {
                        dataCell.setCellValue(detailDTO.getBrandshopName());
                    }else if (i==6) {
                        dataCell.setCellValue(detailDTO.getSalesName());
                    }else if (i==7) {
                        dataCell.setCellValue(detailDTO.getSalesPhone());
                    }else if(i==8){
                    	dataCell.setCellValue(detailDTO.getOrderId());
                    }else if (i==9) {
                        dataCell.setCellValue(detailDTO.getMemberMobile());
                    }else if (i==10) {
                        dataCell.setCellValue(detailDTO.getReservePhone());
                    }else if (i==11) {
                        if (detailDTO.getReceivedTime() != null) {
                            dataCell.setCellValue(fullDf.format(detailDTO.getReceivedTime()));
                        }else {
                            dataCell.setCellValue("--");
                        }
                    }else if (i==12) {
                        if (detailDTO.getReserveTime() != null) {
                            dataCell.setCellValue(fullDf.format(detailDTO.getReserveTime()));
                        }else {
                            dataCell.setCellValue("--");
                        }
                    }else if (i==13) {
                        if (detailDTO.getReserveShopTime() != null) {
                            dataCell.setCellValue(fullDf.format(detailDTO.getReserveShopTime()));
                        }else {
                            dataCell.setCellValue("--");
                        }
                    }else if (i==14) {
                        if (detailDTO.getUpdateDate() != null) {
                            dataCell.setCellValue(fullDf.format(detailDTO.getUpdateDate()));
                        }else {
                            dataCell.setCellValue("--");
                        }
                    }else if (i==15) {
                        dataCell.setCellValue(detailDTO.getStatus());
                    }
                    
                }
            }
        }
        
        //创建第二个表单
        HSSFSheet sheet2 = workbook.createSheet("会员填写信息");
        sheet2.setDefaultRowHeightInPoints(15);
        sheet2.setDefaultColumnWidth(15);
        String title2 = "赠品会员填写信息-" + currentMonth;
        logger.info("headers="+userData.getHeaderMap());
        // 第一行标题
        int mergeAcross = userData.getHeaderMap().size();//合并单元格坐标
        if (mergeAcross<3) {
            mergeAcross=3;
        }
        sheet2.addMergedRegion(new CellRangeAddress(0, 0, 0, mergeAcross));
        HSSFRow title2Row = sheet2.createRow(0);
        title2Row.setHeightInPoints(24);
        HSSFCell title2Cell = title2Row.createCell(0);
        title2Cell.setCellValue(title2);
        title2Cell.setCellStyle(titleStyle);
        // 第二行表头
        HSSFRow header2Row = sheet2.createRow(1);
        header2Row.setHeightInPoints(22);
        HSSFCell header2FirstCell = header2Row.createCell(0);
        header2FirstCell.setCellValue("ID");
        header2FirstCell.setCellStyle(headerStyle);
        int header2CellIdx = 1;
        for (String name : userData.getHeaderMap().values()) {
            HSSFCell header2Cell = header2Row.createCell(header2CellIdx++);
            header2Cell.setCellValue(name);
            header2Cell.setCellStyle(headerStyle);
        }
     // 第三行及之后的数据
        int data2RowIdx = 2;
        if (CollectionUtils.isNotEmpty(userData.getCollecterList())) {
            for (CollectAttributeValueExport exportLine : userData.getCollecterList()) {
                if (exportLine != null ) {
                    HSSFRow data2Row = sheet2.createRow(data2RowIdx++);
                    data2Row.createCell(0).setCellValue(exportLine.getId());
                    int data2CellIdx = 1;
                    //特别提示:1.当导出数据包含多个子活动时,会员领取收集信息的字段个数是不确定的,必须按表头名一一对应取出数据!2.而且会员账号Id必须和数据行的id一致!
                    for (String key : userData.getHeaderMap().keySet()) {
                        HSSFCell data2Cell = data2Row.createCell(data2CellIdx++);
                        for (CollectAttributeValue collectAttributeValue : exportLine.getCollectAttributeValues()) {
                            if (collectAttributeValue != null && key.equals(collectAttributeValue.getName()) && exportLine.getId().equals(collectAttributeValue.getMemberGiftAccountId())) {
                                data2Cell.setCellValue(collectAttributeValue.getValue());
                                data2Cell.setCellStyle(dataStyle);
                            }
                        }
                    }
                }
            }
        }
        
        //生成报表文件
        try {
            File folder = new File(this.exportPath);
            if(!folder.exists()){
                folder.mkdirs();     ///如果不存在，创建目录
            }

            File file = new File(folder,filename);
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            FileOutputStream fileOut = new FileOutputStream(this.exportPath + filename);
            workbook.write(fileOut);
            fileOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return "";
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
        logger.info("生成报表["+filename+"]成功！");
        return filename;
    }


    /**
     * 会员填写信息列表
     * 
     * @param list
     * @return 设定文件
     */
    private CollectUserDTO toCollectUserDTO(List<EcoGiftDetailDTO> list) {
        CollectUserDTO userDTO = new CollectUserDTO();
        List<CollectAttributeValueExport> userDTOs = new ArrayList<CollectAttributeValueExport>(list.size());
        Map<String, String> headMap = new LinkedHashMap<String, String>();
        for (EcoGiftDetailDTO ecoGiftDetailDTO : list) {
            CollectAttributeValueExport collectUser = new CollectAttributeValueExport();
            //id
            collectUser.setId(ecoGiftDetailDTO.getId());
            //查会员收集信息
            Map<String, Object> param = new HashMap<String, Object>();
            param.put("memberGiftAccountId", ecoGiftDetailDTO.getId());
            List<CollectAttributeValue> collectAttributeValues = this.statisticsService.getCollectUserList(param);
            collectUser.setCollectAttributeValues(collectAttributeValues);
            //表头
            if (CollectionUtils.isNotEmpty(collectAttributeValues)) {
                for (CollectAttributeValue collectAttributeValue : collectAttributeValues) {
                    if (!headMap.containsKey(collectAttributeValue.getName())) {
                        headMap.put(collectAttributeValue.getName(), collectAttributeValue.getLabel());
                    }
                }
            }
            userDTOs.add(collectUser);
        }
        userDTO.setCollecterList(userDTOs);
        userDTO.setHeaderMap(headMap);
        return userDTO;
    }

}