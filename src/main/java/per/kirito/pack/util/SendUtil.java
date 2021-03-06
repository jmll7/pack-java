package per.kirito.pack.util;

import per.kirito.pack.myEnum.Express;
import per.kirito.pack.pojo.Send;
import per.kirito.pack.pojo.utilPojo.Page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @version 1.0
 * @Author: kirito
 * @Date: 2021/1/5
 * @Time: 17:15
 * @description: 寄件工具类
 */
public class SendUtil {

	private static final String ZTO = String.valueOf(Express.中通);
	private static final String YD = String.valueOf(Express.韵达);
	private static final String EMS = String.valueOf(Express.EMS);

	private static final String ZTO_TYPE = Express.中通.getExpressEngName();
	private static final String YD_TYPE = Express.韵达.getExpressEngName();
	private static final String EMS_TYPE = Express.EMS.getExpressEngName();

	/**
	 * @Description: 抽取出来的分页方法，仅需传入当前页面、每页条数、快递结果集
	 * @Param: [currentPage, pageSize, sendList]
	 * @Return: per.kirito.pack.pojo.utilPojo.Page<per.kirito.pack.pojo.Send>
	 **/
	public static Page<Send> getSendByPage(int currentPage, int pageSize, List<Send> sendList) {
		Page<Send> sendPage = new Page<>();
		sendPage.setCurrentPage(currentPage);
		sendPage.setPageSize(pageSize);
		List<Send> list = new ArrayList<>();
		Send send;
		int index = (currentPage - 1) * pageSize;
		int end = currentPage * pageSize;
		int size = sendList.size();
		// 当最后一页记录条数不足每页记录条数时，end置为结果集的长度
		if (end > size) {
			end = size;
		}
		// 遍历结果集，获取到当前页数下的数据集
		for (int i = index; i < end; i++) {
			send = sendList.get(i);
			list.add(send);
		}
		sendPage.setList(list);
		// 获取结果集数量
		int total = sendList.size();
		sendPage.setTotal(total);
		return sendPage;
	}

	public static Map<String, String> getSendIdAndOrg(String postAddr) {
		Map<String, String> map = new HashMap<>();
		String id = "";
		String org = "";
		switch (postAddr) {
			case "中苑":
				id = PackIdUtil.generate(ZTO_TYPE);
				org = ZTO;
				break;
			case "西苑":
				id = PackIdUtil.generate(YD_TYPE);
				org = YD;
				break;
			case "北苑":
				id = PackIdUtil.generate(EMS_TYPE);
				org = EMS;
				break;
			default: break;
		}
		map.put("id", id);
		map.put("org", org);
		return map;
	}

	/**
	 * @Description: 抽取出来的传入驿站编号，返回寄件快递公司/种类方法
	 * @Param: [card]
	 * @Return: java.lang.String
	 **/
	public static String getSendOrg(String card) {
		String org = "";
		switch (card) {
			case "2101":
				org = ZTO;
				break;
			case "2102":
				org = YD;
				break;
			case "2103":
				org = EMS;
				break;
			default: break;
		}
		return org;
	}

}
