package com.finance.risk.dashboard.service.impl;

import com.alibaba.fastjson.JSON;
import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.dao.AlertDao;
import com.finance.risk.dashboard.dto.AlertInputDTO;
import com.finance.risk.dashboard.entity.AlertResult;
import com.finance.risk.dashboard.service.AlertService;
import com.finance.risk.dashboard.vo.AlertVO;
import com.finance.risk.dashboard.websocket.RiskWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertServiceImpl implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertServiceImpl.class);

    @Resource
    private AlertDao alertDao;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RiskWebSocketHandler riskWebSocketHandler;

    private static final DateTimeFormatter DB_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Map<String, String> CITY_COORDS = new HashMap<>();
    static {
        CITY_COORDS.put("北京", "116.41,39.90"); CITY_COORDS.put("天津", "117.20,39.09"); CITY_COORDS.put("上海", "121.47,31.23"); CITY_COORDS.put("重庆", "106.55,29.56");
        CITY_COORDS.put("九龙", "114.17,22.33"); CITY_COORDS.put("新界", "114.20,22.34"); CITY_COORDS.put("香港", "114.18,22.27"); CITY_COORDS.put("路环", "113.56,22.12");
        CITY_COORDS.put("澳门", "113.55,22.20"); CITY_COORDS.put("氹仔", "113.58,22.16"); CITY_COORDS.put("台中", "120.68,24.14"); CITY_COORDS.put("台北", "121.57,25.04");
        CITY_COORDS.put("台南", "120.28,23.17"); CITY_COORDS.put("嘉义", "120.45,23.48"); CITY_COORDS.put("高雄", "120.29,22.62"); CITY_COORDS.put("基隆", "121.75,25.13");
        CITY_COORDS.put("新北", "121.47,25.01"); CITY_COORDS.put("石家庄", "114.51,38.04"); CITY_COORDS.put("唐山", "118.18,39.63"); CITY_COORDS.put("秦皇岛", "119.60,39.94");
        CITY_COORDS.put("邯郸", "114.54,36.63"); CITY_COORDS.put("邢台", "114.50,37.07"); CITY_COORDS.put("保定", "115.46,38.87"); CITY_COORDS.put("张家口", "114.89,40.82");
        CITY_COORDS.put("承德", "117.96,40.95"); CITY_COORDS.put("沧州", "116.84,38.30"); CITY_COORDS.put("廊坊", "116.68,39.54"); CITY_COORDS.put("衡水", "115.67,37.74");
        CITY_COORDS.put("郑州", "113.62,34.75"); CITY_COORDS.put("开封", "114.31,34.80"); CITY_COORDS.put("洛阳", "112.45,34.62"); CITY_COORDS.put("平顶山", "113.19,33.77");
        CITY_COORDS.put("安阳", "114.39,36.10"); CITY_COORDS.put("鹤壁", "114.30,35.75"); CITY_COORDS.put("新乡", "113.93,35.30"); CITY_COORDS.put("焦作", "113.24,35.22");
        CITY_COORDS.put("濮阳", "115.03,35.76"); CITY_COORDS.put("许昌", "113.85,34.04"); CITY_COORDS.put("漯河", "114.02,33.58"); CITY_COORDS.put("三门峡", "111.20,34.77");
        CITY_COORDS.put("南阳", "112.53,32.99"); CITY_COORDS.put("商丘", "115.66,34.41"); CITY_COORDS.put("信阳", "114.09,32.15"); CITY_COORDS.put("周口", "114.70,33.63");
        CITY_COORDS.put("驻马店", "114.02,33.01"); CITY_COORDS.put("济南", "117.12,36.65"); CITY_COORDS.put("青岛", "120.38,36.07"); CITY_COORDS.put("淄博", "118.05,36.81");
        CITY_COORDS.put("枣庄", "117.32,34.81"); CITY_COORDS.put("东营", "118.67,37.43"); CITY_COORDS.put("烟台", "121.45,37.46"); CITY_COORDS.put("潍坊", "119.16,36.71");
        CITY_COORDS.put("济宁", "116.59,35.41"); CITY_COORDS.put("泰安", "117.09,36.20"); CITY_COORDS.put("威海", "122.12,37.51"); CITY_COORDS.put("日照", "119.53,35.42");
        CITY_COORDS.put("莱芜", "117.68,36.21"); CITY_COORDS.put("临沂", "118.36,35.10"); CITY_COORDS.put("德州", "116.36,37.44"); CITY_COORDS.put("聊城", "115.99,36.46");
        CITY_COORDS.put("滨州", "117.97,37.38"); CITY_COORDS.put("菏泽", "115.48,35.23"); CITY_COORDS.put("太原", "112.56,37.88"); CITY_COORDS.put("大同", "113.30,40.08");
        CITY_COORDS.put("阳泉", "113.58,37.86"); CITY_COORDS.put("长治", "113.12,36.20"); CITY_COORDS.put("晋城", "112.85,35.49"); CITY_COORDS.put("朔州", "112.44,39.36");
        CITY_COORDS.put("晋中", "112.75,37.69"); CITY_COORDS.put("运城", "111.01,35.03"); CITY_COORDS.put("忻州", "112.73,38.42"); CITY_COORDS.put("临汾", "111.52,36.09");
        CITY_COORDS.put("吕梁", "111.14,37.52"); CITY_COORDS.put("沈阳", "123.46,41.68"); CITY_COORDS.put("大连", "121.61,38.91"); CITY_COORDS.put("鞍山", "122.99,41.11");
        CITY_COORDS.put("抚顺", "123.96,41.88"); CITY_COORDS.put("本溪", "123.77,41.29"); CITY_COORDS.put("丹东", "124.36,40.00"); CITY_COORDS.put("锦州", "121.13,41.10");
        CITY_COORDS.put("营口", "122.23,40.67"); CITY_COORDS.put("阜新", "121.67,42.02"); CITY_COORDS.put("辽阳", "123.24,41.27"); CITY_COORDS.put("盘锦", "122.07,41.12");
        CITY_COORDS.put("铁岭", "123.84,42.29"); CITY_COORDS.put("朝阳", "120.45,41.57"); CITY_COORDS.put("葫芦岛", "120.84,40.71"); CITY_COORDS.put("长春", "125.32,43.82");
        CITY_COORDS.put("吉林", "126.55,43.84"); CITY_COORDS.put("四平", "124.35,43.17"); CITY_COORDS.put("辽源", "125.14,42.89"); CITY_COORDS.put("通化", "125.94,41.73");
        CITY_COORDS.put("白山", "126.42,41.94"); CITY_COORDS.put("松原", "124.83,45.14"); CITY_COORDS.put("白城", "122.84,45.62"); CITY_COORDS.put("延边", "129.51,42.89");
        CITY_COORDS.put("哈尔滨", "126.54,45.80"); CITY_COORDS.put("齐齐哈尔", "123.92,47.35"); CITY_COORDS.put("鸡西", "130.97,45.30"); CITY_COORDS.put("鹤岗", "130.30,47.35");
        CITY_COORDS.put("双鸭山", "131.16,46.65"); CITY_COORDS.put("大庆", "125.11,46.60"); CITY_COORDS.put("伊春", "128.84,47.73"); CITY_COORDS.put("佳木斯", "130.32,46.80");
        CITY_COORDS.put("七台河", "131.00,45.77"); CITY_COORDS.put("牡丹江", "129.63,44.55"); CITY_COORDS.put("黑河", "127.53,50.25"); CITY_COORDS.put("绥化", "126.97,46.65");
        CITY_COORDS.put("大兴安岭", "124.59,51.92"); CITY_COORDS.put("南京", "118.80,32.06"); CITY_COORDS.put("无锡", "120.31,31.49"); CITY_COORDS.put("徐州", "117.29,34.20");
        CITY_COORDS.put("常州", "119.97,31.81"); CITY_COORDS.put("苏州", "120.58,31.30"); CITY_COORDS.put("南通", "120.89,31.98"); CITY_COORDS.put("连云港", "119.22,34.60");
        CITY_COORDS.put("淮安", "119.02,33.61"); CITY_COORDS.put("盐城", "120.16,33.35"); CITY_COORDS.put("扬州", "119.41,32.39"); CITY_COORDS.put("镇江", "119.42,32.19");
        CITY_COORDS.put("泰州", "119.93,32.46"); CITY_COORDS.put("宿迁", "118.28,33.96"); CITY_COORDS.put("杭州", "120.16,30.27"); CITY_COORDS.put("宁波", "121.55,29.87");
        CITY_COORDS.put("温州", "120.70,27.99"); CITY_COORDS.put("嘉兴", "120.76,30.75"); CITY_COORDS.put("湖州", "120.09,30.89"); CITY_COORDS.put("绍兴", "120.58,30.03");
        CITY_COORDS.put("金华", "119.65,29.08"); CITY_COORDS.put("衢州", "118.87,28.94"); CITY_COORDS.put("舟山", "122.21,29.99"); CITY_COORDS.put("台州", "121.42,28.66");
        CITY_COORDS.put("丽水", "119.92,28.47"); CITY_COORDS.put("合肥", "117.23,31.82"); CITY_COORDS.put("芜湖", "118.43,31.35"); CITY_COORDS.put("蚌埠", "117.39,32.92");
        CITY_COORDS.put("淮南", "117.00,32.63"); CITY_COORDS.put("马鞍山", "118.51,31.67"); CITY_COORDS.put("淮北", "116.80,33.95"); CITY_COORDS.put("铜陵", "117.81,30.94");
        CITY_COORDS.put("安庆", "117.06,30.54"); CITY_COORDS.put("黄山", "118.34,29.72"); CITY_COORDS.put("滁州", "118.32,32.30"); CITY_COORDS.put("阜阳", "115.81,32.89");
        CITY_COORDS.put("宿州", "116.96,33.65"); CITY_COORDS.put("六安", "116.52,31.73"); CITY_COORDS.put("亳州", "115.78,33.84"); CITY_COORDS.put("池州", "117.49,30.66");
        CITY_COORDS.put("宣城", "118.76,30.94"); CITY_COORDS.put("福州", "119.30,26.07"); CITY_COORDS.put("厦门", "118.09,24.48"); CITY_COORDS.put("莆田", "119.01,25.45");
        CITY_COORDS.put("三明", "117.64,26.26"); CITY_COORDS.put("泉州", "118.68,24.87"); CITY_COORDS.put("漳州", "117.65,24.51"); CITY_COORDS.put("南平", "118.12,27.33");
        CITY_COORDS.put("龙岩", "117.02,25.08"); CITY_COORDS.put("宁德", "119.55,26.67"); CITY_COORDS.put("南昌", "115.86,28.68"); CITY_COORDS.put("景德镇", "117.18,29.27");
        CITY_COORDS.put("萍乡", "113.85,27.62"); CITY_COORDS.put("九江", "116.00,29.71"); CITY_COORDS.put("新余", "114.92,27.82"); CITY_COORDS.put("鹰潭", "117.07,28.26");
        CITY_COORDS.put("赣州", "114.93,25.83"); CITY_COORDS.put("吉安", "114.99,27.11"); CITY_COORDS.put("宜春", "114.42,27.81"); CITY_COORDS.put("抚州", "116.36,27.95");
        CITY_COORDS.put("上饶", "117.94,28.45"); CITY_COORDS.put("武汉", "114.31,30.59"); CITY_COORDS.put("黄石", "115.04,30.20"); CITY_COORDS.put("十堰", "110.80,32.63");
        CITY_COORDS.put("宜昌", "111.29,30.69"); CITY_COORDS.put("襄阳", "112.12,32.01"); CITY_COORDS.put("鄂州", "114.89,30.39"); CITY_COORDS.put("荆门", "112.20,31.04");
        CITY_COORDS.put("孝感", "113.92,30.92"); CITY_COORDS.put("荆州", "112.24,30.33"); CITY_COORDS.put("黄冈", "114.87,30.45"); CITY_COORDS.put("咸宁", "114.32,29.84");
        CITY_COORDS.put("随州", "113.38,31.69"); CITY_COORDS.put("恩施", "109.49,30.27"); CITY_COORDS.put("长沙", "112.94,28.23"); CITY_COORDS.put("株洲", "113.13,27.83");
        CITY_COORDS.put("湘潭", "112.94,27.83"); CITY_COORDS.put("衡阳", "112.57,26.89"); CITY_COORDS.put("邵阳", "111.47,27.24"); CITY_COORDS.put("岳阳", "113.13,29.36");
        CITY_COORDS.put("常德", "111.70,29.03"); CITY_COORDS.put("张家界", "110.48,29.12"); CITY_COORDS.put("益阳", "112.36,28.55"); CITY_COORDS.put("郴州", "113.01,25.77");
        CITY_COORDS.put("永州", "111.61,26.42"); CITY_COORDS.put("怀化", "110.00,27.57"); CITY_COORDS.put("娄底", "111.99,27.70"); CITY_COORDS.put("湘西", "109.74,28.31");
        CITY_COORDS.put("广州", "113.26,23.13"); CITY_COORDS.put("韶关", "113.60,24.81"); CITY_COORDS.put("深圳", "114.06,22.54"); CITY_COORDS.put("珠海", "113.58,22.27");
        CITY_COORDS.put("汕头", "116.68,23.35"); CITY_COORDS.put("佛山", "113.12,23.02"); CITY_COORDS.put("江门", "113.08,22.58"); CITY_COORDS.put("湛江", "110.36,21.27");
        CITY_COORDS.put("茂名", "110.93,21.66"); CITY_COORDS.put("肇庆", "112.47,23.05"); CITY_COORDS.put("惠州", "114.42,23.11"); CITY_COORDS.put("梅州", "116.12,24.29");
        CITY_COORDS.put("汕尾", "115.38,22.79"); CITY_COORDS.put("河源", "114.70,23.74"); CITY_COORDS.put("阳江", "111.98,21.86"); CITY_COORDS.put("清远", "113.06,23.68");
        CITY_COORDS.put("东莞", "113.75,23.02"); CITY_COORDS.put("中山", "113.39,22.52"); CITY_COORDS.put("潮州", "116.62,23.66"); CITY_COORDS.put("揭阳", "116.37,23.55");
        CITY_COORDS.put("云浮", "112.04,22.92"); CITY_COORDS.put("海口", "110.20,20.04"); CITY_COORDS.put("三亚", "109.51,18.25"); CITY_COORDS.put("三沙", "112.33,16.83");
        CITY_COORDS.put("儋州", "109.58,19.52"); CITY_COORDS.put("成都", "104.06,30.57"); CITY_COORDS.put("自贡", "104.78,29.34"); CITY_COORDS.put("攀枝花", "101.72,26.58");
        CITY_COORDS.put("泸州", "105.44,28.87"); CITY_COORDS.put("德阳", "104.40,31.13"); CITY_COORDS.put("绵阳", "104.68,31.47"); CITY_COORDS.put("广元", "105.84,32.44");
        CITY_COORDS.put("遂宁", "105.59,30.53"); CITY_COORDS.put("内江", "105.06,29.58"); CITY_COORDS.put("乐山", "103.77,29.55"); CITY_COORDS.put("南充", "106.11,30.84");
        CITY_COORDS.put("眉山", "103.85,30.08"); CITY_COORDS.put("宜宾", "104.64,28.75"); CITY_COORDS.put("广安", "106.63,30.46"); CITY_COORDS.put("达州", "107.47,31.21");
        CITY_COORDS.put("雅安", "103.04,30.01"); CITY_COORDS.put("巴中", "106.75,31.87"); CITY_COORDS.put("资阳", "104.63,30.13"); CITY_COORDS.put("阿坝", "102.22,31.90");
        CITY_COORDS.put("甘孜", "101.96,30.05"); CITY_COORDS.put("凉山", "102.27,27.88"); CITY_COORDS.put("贵阳", "106.63,26.65"); CITY_COORDS.put("六盘水", "104.83,26.59");
        CITY_COORDS.put("遵义", "106.93,27.73"); CITY_COORDS.put("安顺", "105.95,26.25"); CITY_COORDS.put("毕节", "105.31,27.30"); CITY_COORDS.put("铜仁", "109.18,27.69");
        CITY_COORDS.put("黔西南", "104.90,25.09"); CITY_COORDS.put("黔东南", "107.98,26.58"); CITY_COORDS.put("黔南", "107.52,26.25"); CITY_COORDS.put("昆明", "102.83,24.88");
        CITY_COORDS.put("曲靖", "103.80,25.49"); CITY_COORDS.put("玉溪", "102.55,24.35"); CITY_COORDS.put("保山", "99.16,25.11"); CITY_COORDS.put("昭通", "103.72,27.34");
        CITY_COORDS.put("丽江", "100.23,26.86"); CITY_COORDS.put("普洱", "100.97,22.83"); CITY_COORDS.put("临沧", "100.09,23.88"); CITY_COORDS.put("楚雄", "101.53,25.04");
        CITY_COORDS.put("红河", "103.38,23.36"); CITY_COORDS.put("文山", "104.22,23.40"); CITY_COORDS.put("西双版纳", "100.80,22.01"); CITY_COORDS.put("大理", "100.27,25.61");
        CITY_COORDS.put("德宏", "98.58,24.43"); CITY_COORDS.put("怒江", "98.86,25.82"); CITY_COORDS.put("迪庆", "99.70,27.82"); CITY_COORDS.put("西安", "108.94,34.34");
        CITY_COORDS.put("铜川", "108.95,34.90"); CITY_COORDS.put("宝鸡", "107.24,34.36"); CITY_COORDS.put("咸阳", "108.71,34.33"); CITY_COORDS.put("渭南", "109.51,34.50");
        CITY_COORDS.put("延安", "109.49,36.59"); CITY_COORDS.put("汉中", "107.02,33.07"); CITY_COORDS.put("榆林", "109.73,38.29"); CITY_COORDS.put("安康", "109.03,32.68");
        CITY_COORDS.put("商洛", "109.94,33.87"); CITY_COORDS.put("兰州", "103.83,36.06"); CITY_COORDS.put("嘉峪关", "98.29,39.77"); CITY_COORDS.put("金昌", "102.19,38.52");
        CITY_COORDS.put("白银", "104.14,36.54"); CITY_COORDS.put("天水", "105.72,34.58"); CITY_COORDS.put("武威", "102.64,37.93"); CITY_COORDS.put("张掖", "100.45,38.93");
        CITY_COORDS.put("平凉", "106.67,35.54"); CITY_COORDS.put("酒泉", "98.49,39.73"); CITY_COORDS.put("庆阳", "107.64,35.71"); CITY_COORDS.put("定西", "104.63,35.58");
        CITY_COORDS.put("陇南", "104.92,33.40"); CITY_COORDS.put("临夏", "103.21,35.60"); CITY_COORDS.put("甘南", "102.91,34.98"); CITY_COORDS.put("西宁", "101.78,36.62");
        CITY_COORDS.put("海东", "102.40,36.48"); CITY_COORDS.put("海北", "100.90,36.95"); CITY_COORDS.put("黄南", "102.02,35.52"); CITY_COORDS.put("海南", "100.62,36.29");
        CITY_COORDS.put("果洛", "100.24,34.47"); CITY_COORDS.put("玉树", "97.01,33.01"); CITY_COORDS.put("海西", "97.37,37.38"); CITY_COORDS.put("南宁", "108.37,22.82");
        CITY_COORDS.put("柳州", "109.42,24.33"); CITY_COORDS.put("桂林", "110.29,25.27"); CITY_COORDS.put("梧州", "111.28,23.48"); CITY_COORDS.put("北海", "109.12,21.48");
        CITY_COORDS.put("防城港", "108.35,21.69"); CITY_COORDS.put("钦州", "108.65,21.98"); CITY_COORDS.put("贵港", "109.60,23.11"); CITY_COORDS.put("玉林", "110.18,22.65");
        CITY_COORDS.put("百色", "106.62,23.90"); CITY_COORDS.put("贺州", "111.57,24.40"); CITY_COORDS.put("河池", "108.09,24.69"); CITY_COORDS.put("来宾", "109.22,23.75");
        CITY_COORDS.put("崇左", "107.36,22.38"); CITY_COORDS.put("呼和浩特", "111.75,40.84"); CITY_COORDS.put("包头", "109.84,40.66"); CITY_COORDS.put("乌海", "106.80,39.65");
        CITY_COORDS.put("赤峰", "118.89,42.26"); CITY_COORDS.put("通辽", "122.24,43.65"); CITY_COORDS.put("鄂尔多斯", "109.78,39.61"); CITY_COORDS.put("呼伦贝尔", "119.77,49.21");
        CITY_COORDS.put("巴彦淖尔", "107.39,40.74"); CITY_COORDS.put("乌兰察布", "113.13,40.99"); CITY_COORDS.put("兴安", "122.04,46.08"); CITY_COORDS.put("锡林郭勒", "116.05,43.93");
        CITY_COORDS.put("阿拉善", "105.73,38.85"); CITY_COORDS.put("银川", "106.23,38.49"); CITY_COORDS.put("石嘴山", "106.38,38.98"); CITY_COORDS.put("吴忠", "106.20,38.00");
        CITY_COORDS.put("固原", "106.24,36.02"); CITY_COORDS.put("中卫", "105.20,37.50"); CITY_COORDS.put("拉萨", "91.11,29.64"); CITY_COORDS.put("日喀则", "88.88,29.27");
        CITY_COORDS.put("昌都", "97.17,31.14"); CITY_COORDS.put("林芝", "94.36,29.65"); CITY_COORDS.put("山南", "91.77,29.24"); CITY_COORDS.put("那曲", "92.05,31.48");
        CITY_COORDS.put("阿里", "81.15,30.40"); CITY_COORDS.put("乌鲁木齐", "87.62,43.83"); CITY_COORDS.put("克拉玛依", "84.89,45.58"); CITY_COORDS.put("吐鲁番", "89.19,42.95");
        CITY_COORDS.put("哈密", "93.52,42.82"); CITY_COORDS.put("昌吉", "87.31,44.01"); CITY_COORDS.put("博尔塔拉", "82.07,44.91"); CITY_COORDS.put("巴音郭楞", "86.15,41.76");
        CITY_COORDS.put("阿克苏", "80.26,41.17"); CITY_COORDS.put("克孜勒苏", "76.17,39.72"); CITY_COORDS.put("喀什", "75.99,39.47"); CITY_COORDS.put("和田", "79.92,37.11");
        CITY_COORDS.put("伊犁", "81.32,43.92"); CITY_COORDS.put("塔城", "82.98,46.75"); CITY_COORDS.put("阿勒泰", "88.14,47.85");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int receiveAlerts(List<AlertInputDTO> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            log.warn("告警数据列表为空，跳过处理");
            return 0;
        }

        List<AlertResult> entities = alerts.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());

        int count = alertDao.batchInsert(entities);
        log.info("批量接收告警数据完成，成功写入 {} 条", count);

        if (count > 0) {
            try {
                redisTemplate.opsForList().leftPushAll(
                        Constants.REDIS_ALERT_LIST,
                        entities.stream().map(JSON::toJSONString).toArray(String[]::new));
                redisTemplate.opsForList().trim(Constants.REDIS_ALERT_LIST, 0, 199);
            } catch (Exception e) {
                log.error("Redis 缓存写入失败: {}", e.getMessage());
            }
            pushRealtimeAlerts(entities);
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean receiveAlert(AlertInputDTO alert) {
        if (alert == null) {
            return false;
        }
        AlertResult entity = convertToEntity(alert);
        boolean success = alertDao.insert(entity) > 0;
        if (success) {
            cacheAlert(entity);
            pushRealtimeAlert(entity);
        }
        return success;
    }

    @Override
    public Map<String, Object> queryAlertList(String riskLevel, String status,
                                               String startTime, String endTime,
                                               int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AlertResult> list = alertDao.findList(riskLevel, status, startTime, endTime, offset, pageSize);
        Long total = alertDao.count(riskLevel, status, startTime, endTime);

        List<AlertVO> vos = list.stream().map(this::convertToVO).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("list", vos);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public boolean updateAlertStatus(String alertId, String status, String handler, String remark) {
        Long handleTime = System.currentTimeMillis();
        int rows = alertDao.updateStatus(alertId, status, handler, handleTime, remark);
        if (rows > 0) {
            log.info("告警 {} 状态更新: {} -> handler={}", alertId, status, handler);
            return true;
        }
        return false;
    }

    @Override
    public List<AlertVO> getRecentAlerts(int limit) {
        try {
            List<Object> cachedList = redisTemplate.opsForList()
                    .range(Constants.REDIS_ALERT_LIST, 0, limit - 1);
            if (cachedList != null && !cachedList.isEmpty()) {
                return cachedList.stream()
                        .map(o -> JSON.parseObject(o.toString(), AlertResult.class))
                        .map(this::convertToVO)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败，回退到数据库查询: {}", e.getMessage());
        }

        List<AlertResult> list = alertDao.findList(null, null, null, null, 0, limit);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<AlertVO> getRecentSevereAlerts(int limit) {
        List<AlertResult> list = alertDao.findSevereList(limit);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> countByRiskLevel() {
        String sinceTime = LocalDateTime.now().minusDays(1).format(DB_TIME_FMT);
        List<AlertDao.RiskLevelCount> counts = alertDao.countByRiskLevel(sinceTime);

        return counts.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", c.getRiskLevel());
            map.put("value", c.getCnt());
            map.put("color", getRiskColor(c.getRiskLevel()));
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> countByHitRule() {
        String sinceTime = LocalDateTime.now().minusDays(1).format(DB_TIME_FMT);
        List<AlertDao.RuleCount> counts = alertDao.countByHitRule(sinceTime);

        return counts.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", c.getHitRules());
            map.put("value", c.getCnt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> countHighRiskByCity(int limit) {
        String sinceTime = LocalDateTime.now().minusDays(1).format(DB_TIME_FMT);
        List<AlertDao.CityAlertCount> counts = alertDao.countHighRiskByCity(sinceTime, limit);

        return counts.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("city", c.getAlertLoc());
            map.put("count", c.getCnt());
            map.put("riskLevel", c.getRiskLevel());
            String city = c.getAlertLoc();
            String coords = CITY_COORDS.getOrDefault(city, null);
            // 去掉市/省/自治州等后缀再试一次
            if (coords == null) coords = CITY_COORDS.getOrDefault(stripSuffix(city), "116.40,39.90");
            String[] parts = coords.split(",");
            if (parts.length == 2) {
                try {
                    map.put("longitude", Double.parseDouble(parts[0].trim()));
                    map.put("latitude", Double.parseDouble(parts[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> countByCategory() {
        String sinceTime = LocalDateTime.now().minusDays(7).format(DB_TIME_FMT);
        // 查询最近7天所有告警的 hit_rules
        List<AlertResult> alerts = alertDao.findList(null, null, sinceTime, null, 0, 10000);

        // A-I 类别计数
        String[] catLabels = {"A 账户安全", "B 设备安全", "C 金额特征", "D 地理位置", "E 时间特征", "F 收款方风险", "G 操作行为", "H 资金链路", "I 环境网络"};
        int[] counts = new int[9];

        for (AlertResult alert : alerts) {
            String rules = alert.getHitRules();
            if (rules == null || rules.isEmpty()) continue;
            for (String rule : rules.split(";")) {
                rule = rule.trim();
                if (rule.isEmpty()) continue;
                char c = rule.charAt(0);
                if (c >= 'A' && c <= 'I') counts[c - 'A']++;
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", catLabels[i]);
            item.put("value", counts[i]);
            item.put("color", getCategoryColor((char) ('A' + i)));
            list.add(item);
        }
        return list;
    }

    private String getCategoryColor(char cat) {
        switch (cat) {
            case 'A': return "#DC2626"; // 账户-红
            case 'B': return "#F97316"; // 设备-橙
            case 'C': return "#F59E0B"; // 金额-黄
            case 'D': return "#3B82F6"; // 地理-蓝
            case 'E': return "#8B5CF6"; // 时间-紫
            case 'F': return "#EC4899"; // 收款方-粉
            case 'G': return "#06B6D4"; // 操作-青
            case 'H': return "#991B1B"; // 链路-深红
            case 'I': return "#6B7280"; // 环境-灰
            default: return "#909399";
        }
    }

    private void cacheAlert(AlertResult entity) {
        try {
            redisTemplate.opsForList().leftPush(Constants.REDIS_ALERT_LIST, JSON.toJSONString(entity));
            redisTemplate.opsForList().trim(Constants.REDIS_ALERT_LIST, 0, 199);
        } catch (Exception e) {
            log.warn("Redis alert cache write failed: {}", e.getMessage());
        }
    }

    private void pushRealtimeAlerts(List<AlertResult> entities) {
        for (AlertResult entity : entities) {
            pushRealtimeAlert(entity);
        }
    }

    private void pushRealtimeAlert(AlertResult entity) {
        if (!isSevereRisk(entity)) {
            return;
        }
        if (entity.getCreateTime() == null || entity.getCreateTime().isEmpty()) {
            entity.setCreateTime(LocalDateTime.now().format(DB_TIME_FMT));
        }
        riskWebSocketHandler.broadcastAlert(JSON.toJSONString(convertToVO(entity)));
    }

    private boolean isSevereRisk(AlertResult entity) {
        if (entity == null) {
            return false;
        }
        String riskLevel = entity.getRiskLevel();
        if (riskLevel != null) {
            String trimmed = riskLevel.trim();
            if ("极度危险".equals(trimmed) || "高危".equals(trimmed)) {
                return true;
            }
        }
        return entity.getFinalScore() != null && entity.getFinalScore() >= 80;
    }

    // ==================== 内部转换方法 ====================

    private AlertResult convertToEntity(AlertInputDTO dto) {
        return AlertResult.builder()
                .alertId(dto.getAlertId())
                .transId(dto.getTransId())
                .userId(dto.getUserId())
                .hitRules(dto.getHitRules())
                .amount(dto.getAmount())
                .finalScore(dto.getFinalScore())
                .riskLevel(dto.getRiskLevel())
                .city(dto.getCity())
                .alertLoc(dto.getAlertLoc())
                .status(dto.getStatus() != null ? dto.getStatus() : "pending")
                .counterpartyId(dto.getCounterpartyId())
                .ipAddress(dto.getIpAddress())
                .isNewDevice(dto.getIsNewDevice() == null ? 0 : dto.getIsNewDevice())
                .isNewCounterparty(dto.getIsNewCounterparty() == null ? 0 : dto.getIsNewCounterparty())
                .chainId(dto.getChainId())
                .rawJson(dto.getRawJson())
                .createTime(LocalDateTime.now().format(DB_TIME_FMT))
                .build();
    }

    private AlertVO convertToVO(AlertResult entity) {
        return AlertVO.builder()
                .alertId(entity.getAlertId())
                .transId(entity.getTransId())
                .userId(maskUserId(entity.getUserId()))
                .hitRules(entity.getHitRules())
                .amount(entity.getAmount())
                .finalScore(entity.getFinalScore())
                .riskLevel(entity.getRiskLevel())
                .city(entity.getCity())
                .alertLoc(entity.getAlertLoc())
                .action(riskLevelToAction(entity.getRiskLevel()))
                .status(entity.getStatus())
                .handler(entity.getHandler())
                .handleTime(entity.getHandleTime())
                .handleRemark(entity.getHandleRemark())
                .counterpartyId(entity.getCounterpartyId())
                .ipAddress(entity.getIpAddress())
                .isNewDevice(entity.getIsNewDevice())
                .isNewCounterparty(entity.getIsNewCounterparty())
                .chainId(entity.getChainId())
                .createTime(entity.getCreateTime())
                .build();
    }

    private String riskLevelToAction(String riskLevel) {
        if ("极度危险".equals(riskLevel)) return "BLOCK";
        if ("高危".equals(riskLevel)) return "BLOCK";
        if ("中危".equals(riskLevel)) return "VERIFY";
        return "PASS";
    }

    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 7) {
            return userId;
        }
        return userId.substring(0, 3) + "****" + userId.substring(userId.length() - 4);
    }

    /** 去掉城市名后缀：市、地区、自治州、盟等 */
    private String stripSuffix(String city) {
        if (city == null) return null;
        return city.replaceAll("(市|地区|自治州|自治县|盟|林区|新区|特别行政区)$", "");
    }

    private String getRiskColor(String riskLevel) {
        if ("硬阻断".equals(riskLevel)) return "#991B1B";
        if ("极度危险".equals(riskLevel)) return "#DC2626";
        if ("高危".equals(riskLevel)) return "#F97316";
        if ("中危".equals(riskLevel)) return "#F59E0B";
        return "#22C55E";
    }
}
