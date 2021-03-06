package per.kirito.pack.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.kirito.pack.mapper.AdminMapper;
import per.kirito.pack.mapper.CodeMapper;
import per.kirito.pack.mapper.PackMapper;
import per.kirito.pack.mapper.UserMapper;
import per.kirito.pack.myEnum.Status;
import per.kirito.pack.util.PackUtil;
import per.kirito.pack.util.PickCodeUtil;
import per.kirito.pack.util.TypeConversion;
import per.kirito.pack.pojo.*;
import per.kirito.pack.pojo.utilPojo.PackResult;
import per.kirito.pack.pojo.utilPojo.Page;
import per.kirito.pack.service.inter.PackService;

import java.util.*;

/**
 * @version 1.0
 * @Author: kirito
 * @Date: 2020/12/23
 * @Time: 15:36
 * @description: Pack 的 Service 层，PackService 接口的实现类
 */
@Service
public class PackServiceImpl implements PackService {

	@Autowired
	private PackMapper packMapper;

	@Autowired
	private AdminMapper adminMapper;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private CodeMapper codeMapper;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	private static final int INTO_CODE = Status.INTO_SUCCESS.getCode();
	private static final int PICK_CODE = Status.PICK_SUCCESS.getCode();
	private static final int INFO_CODE = Status.INFO_SUCCESS.getCode();
	private static final int DO_CODE = Status.DO_SUCCESS.getCode();
	private static final int IS_USE = Status.IS_USE.getCode();
	private static final int NOT_USE = Status.NOT_USE.getCode();
	// 快递状态
	private static final int USE_CODE = Status.IS_USE.getCode();
	private static final int PACK_CODE_1 = Status.PACK_STATUS_1.getCode();
	private static final int PACK_CODE_0 = Status.PACK_STATUS_0.getCode();
	private static final int PACK_CODE__1 = Status.PACK_STATUS__1.getCode();
	// 取件码使用状态
	private static final int CODE_STATUS_1 = Status.CODE_STATUS_1.getCode();
	private static final int CODE_STATUS_0 = Status.CODE_STATUS_0.getCode();

	private static final String INTO_SUCCESS = Status.INTO_SUCCESS.getEnMsg();
	private static final String INTO_FAIL = Status.INTO_FAIL.getEnMsg();
	private static final String PICK_SUCCESS = Status.PICK_SUCCESS.getEnMsg();
	private static final String PICK_FAIL = Status.PICK_FAIL.getEnMsg();
	private static final String INFO_SUCCESS = Status.INFO_SUCCESS.getEnMsg();
	private static final String INFO_FAIL = Status.INFO_FAIL.getEnMsg();
	private static final String DO_SUCCESS = Status.DO_SUCCESS.getEnMsg();
	private static final String DO_FAIL = Status.DO_FAIL.getEnMsg();
	private static final String NOT_EXIST = Status.NOT_EXIST.getEnMsg();
	private static final String TAKE_SUCCESS = Status.TAKE_SUCCESS.getEnMsg();
	private static final String LOGIN_TO_DO = Status.LOGIN_TO_DO.getEnMsg();

	// 驿站已有取件码的最大快递数量
	private static final int MAX_PACKS = 2400;

	private static Code MAX_CODE_UNUSED = new Code("20-6-20", "", NOT_USE, "");

	/**
	 * @Description: 驿站管理员添加快递入站
	 * @Param: [id]
	 * @Return: java.lang.String
	 **/
	@Transactional(rollbackFor = Exception.class)
	@Override
	public String addPack(String id, String token) {
		if (stringRedisTemplate.hasKey(token)) {
			try {
				// 随机获取一个用户，用于绑定快递的收件信息
				Pack pack = new Pack();
				pack.setId(id);
				User user = userMapper.getUserByRand();
				pack.setPer_name(user.getName());
				pack.setPer_tel(user.getPhone());
				pack.setPer_addr(user.getAddr());
				// 根据快递单号id获取驿站相关信息
				pack = PackUtil.addInfo(pack);
				String addr = pack.getAddr();
				// 取出目前驿站信息
				Admin admin = adminMapper.getAdminByAddr(addr);
				int count = admin.getCount();
				// 查询最大取件码有无被使用
				MAX_CODE_UNUSED.setAddr(addr);
				int isUse = codeMapper.findMaxCode(MAX_CODE_UNUSED);
				String code = "";
				// 驿站现存快递数量小于能有取件码的快递数量
				if (count < MAX_PACKS) {
					// 此入站快递能有取件码
					Code coder = new Code();
					pack.setStatus(PACK_CODE_1);
					// 判断取件码有无被使用
					if (isUse == USE_CODE) {
						// 最大取件码已被使用，根据已被使用的取件码释放先后赋予取件码
						coder = codeMapper.findCodeFreeMin(addr);
						code = coder.getCode();
					} else {
						// 最大取件码未被使用，此时按照该驿站该快递未入站之前的快递数量生成取件码
						code = PickCodeUtil.generate(count);
						coder.setFree("");
						coder.setAddr(addr);
					}
					coder.setCode(code);
					coder.setStatus(IS_USE);
					// 更新取件码信息
					codeMapper.updateCode(coder);
				} else {
					// 该快递没法有取件码
					pack.setStatus(PACK_CODE__1);
					code = "";
				}
				pack.setCode(code);
				packMapper.addPack(pack);
				// 更新Admin的count数
				adminMapper.updateCountPlusByPackId(id);
				// 更新User的count数
				userMapper.updateCountPlusByPackId(id);
				return INTO_SUCCESS;
			} catch (Exception e) {
				// 有异常，返回入站失败
				e.printStackTrace();
				return INTO_FAIL;
			}
		} else {
			return LOGIN_TO_DO;
		}
	}

	/**
	 * @Description: User 进行取件，必须传入驿站地址和取件码
	 * @Param: [id, code]
	 * @Return: java.lang.String
	 **/
	@Transactional(rollbackFor = Exception.class)
	@Override
	public String pickPackByUser(String addr, String code, String token) {
		String msg = PICK_FAIL;
		try {
			// 如果token令牌存在
			if (stringRedisTemplate.hasKey(token)) {
				Pack pack = packMapper.getPackByAddrAndCode(addr, code);
				String card = stringRedisTemplate.opsForValue().get(token);
				User user = userMapper.getUserById(card);
				String name = user.getName();
				if (pack != null) {
					// 更新管理员信息
					Admin admin = adminMapper.getAdminByAddr(addr);
					adminMapper.updateCountLessByPackId(pack.getId());
					// 如果为本人签收
					if (pack.getPer_tel().equals(user.getPhone())) {
						// 本人签收
						msg = PICK_SUCCESS;
					} else {
						// 他人代取
						user = userMapper.getUserByPhone(pack.getPer_tel());
						msg = TAKE_SUCCESS;
					}
					// 更新用户信息
					userMapper.updateCountLessByPackId(pack.getId());
					pack.setStatus(PACK_CODE_0);
					String time = TypeConversion.getTime();
					pack.setEnd(time);
					// 更新签收人
					pack.setPick(name);
					// 更新包裹状态、取件时间
					packMapper.updatePack(pack);
					// 更新取件码使用状态与释放时间
					Code coder = new Code(code, addr, CODE_STATUS_0, time);
					codeMapper.updateCode(coder);
					int count = admin.getCount() - 1;
					if (count >= MAX_PACKS) {
						// 当前快递取出后，驿站剩余未取快递数大于等于2400，则需要为有取件码的快递根据最早入站时间赋予取件码
						pack = packMapper.getPackByStartMin(addr);
						pack.setCode(code);
						pack.setStatus(PACK_CODE_1);
						packMapper.updatePack(pack);
						// 重新设置该code为使用状态
						coder.setStatus(CODE_STATUS_1);
						codeMapper.updateCode(coder);
					}
				} else {
					// 该快递不存在
					msg = NOT_EXIST;
				}
			} else {
				// 请登录再操作
				msg = LOGIN_TO_DO;
			}
			return msg;
		} catch (Exception e) {
			e.printStackTrace();
			// 取件失败
			return PICK_FAIL;
		}
	}

	/**
	 * @Description: Admin 进行取件，仅传入快递单号即可
	 * @Param: [id, token]
	 * @Return: java.lang.String
	 **/
	@Transactional(rollbackFor = Exception.class)
	@Override
	public String pickPackByAdmin(String id, String token) {
		try {
			if (stringRedisTemplate.hasKey(token)) {
				Pack pack = packMapper.getPackById(id);
				if (pack == null) {
					// 没有该快递
					return INFO_FAIL;
				} else {
					// 更新管理员信息
					String addr = pack.getAddr();
					Admin admin = adminMapper.getAdminByAddr(addr);
					adminMapper.updateCountLessByPackId(id);
					// 更新用户信息
					userMapper.updateCountLessByPackId(id);
					// 更新包裹状态、取件时间
					pack.setStatus(PACK_CODE_0);
					String time = TypeConversion.getTime();
					pack.setEnd(time);
					packMapper.updatePack(pack);
					int count = admin.getCount() - 1;
					// 更新取件码使用状态与释放时间
					String code = pack.getCode();
					if (code != null && !"".equals(code)) {
						Code coder = new Code(code, addr, CODE_STATUS_0, time);
						codeMapper.updateCode(coder);
						if (count >= MAX_PACKS) {
							// 当前快递取出后，驿站剩余未取快递数大于等于2400，则需要为有取件码的快递根据最早入站时间赋予取件码
							pack = packMapper.getPackByStartMin(addr);
							pack.setCode(code);
							pack.setStatus(PACK_CODE_1);
							packMapper.updatePack(pack);
							// 重新设置该code为使用状态
							coder.setStatus(CODE_STATUS_1);
							codeMapper.updateCode(coder);
						}
					}
					return PICK_SUCCESS;
				}
			} else {
				return LOGIN_TO_DO;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return PICK_FAIL;
		}
	}

	/**
	 * @Description: 根据快递单号删除此快递
	 * @Param: [id, token]
	 * @Return: java.lang.String
	 **/
	@Transactional(rollbackFor = Exception.class)
	@Override
	public String deletePackById(String id, String token) {
		try {
			if (stringRedisTemplate.hasKey(token)) {
				int status = packMapper.getStatusById(id);
				// 已取快递不更新Admin与User的count值
				if (status != PACK_CODE_0) {
					// 更新User的count数
					userMapper.updateCountLessByPackId(id);
					// 更新Admin的count数
					adminMapper.updateCountLessByPackId(id);
				}
				// 删除快递
				packMapper.deletePackById(id);
				return DO_SUCCESS;
			} else {
				return LOGIN_TO_DO;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return DO_FAIL;
		}
	}

	/**
	 * -----------------------------------------------------------------------------------------------------------------
	 * User 相关
	 **/

	/**
	 * @Description: 分页获取 User 所有的快递，包括已取出和未取出的快递；如果没有 token 令牌，则返回获取信息失败
	 * @Param: [currentPage, pageSize, token]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getUserPackByPage(int currentPage, int pageSize, String token) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			// 取出User登录的card
			String card = stringRedisTemplate.opsForValue().get(token);
			// 根据card查询出该User已取快递集合
			List<Pack> packs = packMapper.getUserPacks(card);
			List<PackResult> packResultList = PackUtil.getPackResult(packs);
			// 获取分页方式的结果集
			Page<PackResult> resultPage = PackUtil.getPackByPage(currentPage, pageSize, packResultList);
			map.put("pack_result", resultPage);
		} else {
			map.put("fail", INFO_FAIL);
		}
		return map;
	}

	/**
	 * @Description: 分页获取 User 已取出的快递；如果没有 token 令牌，则返回获取信息失败
	 * @Param: [currentPage, pageSize, token]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getUserIsPick(int currentPage, int pageSize, String token) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			// 取出User登录的card
			String card = stringRedisTemplate.opsForValue().get(token);
			// 根据card查询出该User已取快递集合
			List<Pack> packs = packMapper.getUserIsPick(card);
			List<PackResult> packResultList = PackUtil.getPackResult(packs);
			// 获取分页方式的结果集
			Page<PackResult> resultPage = PackUtil.getPackByPage(currentPage, pageSize, packResultList);
			map.put("pack_result", resultPage);
		} else {
			map.put("fail", INFO_FAIL);
		}
		return map;
	}

	/**
	 * @Description: 分页获取 User 所未取出的快递， 无论有无取件码；如果没有 token 令牌，则返回获取信息失败
	 * @Param: [currentPage, pageSize, token]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getUserNoPick(int currentPage, int pageSize, String token) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			// 取出User登录的card
			String card = stringRedisTemplate.opsForValue().get(token);
			// 根据card查询出该User已取快递集合
			List<Pack> packs = packMapper.getUserNoPick(card);
			List<PackResult> packResultList = PackUtil.getPackResult(packs);
			// 获取分页方式的结果集
			Page<PackResult> resultPage = PackUtil.getPackByPage(currentPage, pageSize, packResultList);
			map.put("pack_result", resultPage);
		} else {
			map.put("fail", INFO_FAIL);
		}
		return map;
	}

	/**
	 * @Description: 获取 User 所有快递总数、已取快递数量、未取快递数量
	 * @Param: [token]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getUserTotalNum(String token) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			// 取出用户手机号
			String card = stringRedisTemplate.opsForValue().get(token);
			User user = userMapper.getUserById(card);
			String phone = user.getPhone();
			int allTotal = packMapper.getUserAllTotalNum(phone);
			int isTotal = packMapper.getUserIsTotalNum(phone);
			int noTotal = packMapper.getUserNoTotalNum(phone);
			map.put("allTotal", allTotal);
			map.put("isTotal", isTotal);
			map.put("noTotal", noTotal);
			map.put("result", INFO_SUCCESS);
		} else {
			map.put("result", INFO_FAIL);
		}
		return map;
	}

	/**
	 * -----------------------------------------------------------------------------------------------------------------
	 * Admin 相关
	 **/

	/**
	 * @Description: 分页获取 Admin 所有的快递，包括已取出和未取出的快递；如果没有 token 令牌，则返回获取信息失败
	 * @Param: [currentPage, pageSize, token]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getAdminPackByPage(int currentPage, int pageSize, String token) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			// 取出Admin登录的card
			String card = stringRedisTemplate.opsForValue().get(token);
			// 根据card查询出该Admin已取快递集合
			List<Pack> packs = packMapper.getAdminPacks(card);
			// int -> String 转换
			List<PackResult> packResultList = PackUtil.getPackResult(packs);
			// 获取分页方式的结果集
			Page<PackResult> resultPage = PackUtil.getPackByPage(currentPage, pageSize, packResultList);
			map.put("pack_result", resultPage);
		} else {
			map.put("fail", INFO_FAIL);
		}
		return map;
	}

	/**
	 * @Description: 分页获取当前驿站的已取出快递；如果没有 token 令牌，则返回获取信息失败
	 * @Param: [currentPage, pageSize, token]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getAdminIsPick(int currentPage, int pageSize, String token) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			// 取出Admin登录的card
			String card = stringRedisTemplate.opsForValue().get(token);
			// 根据card查询出该Admin已取快递集合
			List<Pack> packs = packMapper.getAdminIsPick(card);
			List<PackResult> packResultList = PackUtil.getPackResult(packs);
			// 获取分页方式的结果集
			Page<PackResult> resultPage = PackUtil.getPackByPage(currentPage, pageSize, packResultList);
			map.put("pack_result", resultPage);
		} else {
			map.put("fail", INFO_FAIL);
		}
		return map;
	}

	/**
	 * @Description: 分页获取当前驿站的未取出快递，无论有无取件码；如果没有 token令 牌，则返回获取信息失败
	 * @Param: [currentPage, pageSize, token]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getAdminNoPick(int currentPage, int pageSize, String token) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			// 取出Admin登录的card
			String card = stringRedisTemplate.opsForValue().get(token);
			// 根据card查询出该Admin未取快递集合
			List<Pack> packs = packMapper.getAdminNoPick(card);
			List<PackResult> packResultList = PackUtil.getPackResult(packs);
			// 获取分页方式结果集
			Page<PackResult> resultPage = PackUtil.getPackByPage(currentPage, pageSize, packResultList);
			map.put("pack_result", resultPage);
		} else {
			map.put("fail", INFO_FAIL);
		}
		return map;
	}

	/**
	 * @Description: 获取 Admin 所有快递总数、已取快递数量、未取快递数量
	 * @Param: [token]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getAdminTotalNum(String token) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			// 取出驿站地址
			String card = stringRedisTemplate.opsForValue().get(token);
			Admin admin = adminMapper.getAdminById(card);
			String addr = admin.getAddr();
			int allTotal = packMapper.getAdminAllTotalNum(addr);
			int isTotal = packMapper.getAdminIsTotalNum(addr);
			int noTotal = packMapper.getAdminNoTotalNum(addr);
			float percentage = 100;
			if (noTotal < MAX_PACKS) {
				percentage = noTotal / (float) MAX_PACKS * 100;
				if (percentage < 1) {
					percentage = 1;
				}
			}
			map.put("percentage", percentage);
			map.put("allTotal", allTotal);
			map.put("isTotal", isTotal);
			map.put("noTotal", noTotal);
			map.put("result", INFO_SUCCESS);
		} else {
			map.put("result", INFO_FAIL);
		}
		return map;
	}

	/**
	 * @Description: 根据驿站地址和货架取出当前货架的所有快递
	 * @Param: [card, shelf]
	 * @Return: java.util.Map<java.lang.String,java.lang.Object>
	 **/
	@Override
	public Map<String, Object> getShelfPack(String token, String shelf) {
		Map<String, Object> map = new HashMap<>();
		if (stringRedisTemplate.hasKey(token)) {
			String card = stringRedisTemplate.opsForValue().get(token);
			List<Pack> packs = packMapper.getShelfPack(card, shelf);
			map.put("packs", packs);
			map.put("result", INFO_SUCCESS);
		} else {
			map.put("result", INFO_FAIL);
		}
		return map;
	}

}
