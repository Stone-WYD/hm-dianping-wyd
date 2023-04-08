package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST_SIZE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        List<ShopType> resultList;
        // 1 查询缓存
        // 1.1 查询list的大小
        String listSize = stringRedisTemplate.opsForValue().get(SHOP_TYPE_LIST_SIZE_KEY);
        if (listSize!=null && StrUtil.isNotBlank(listSize)) {
            // 1.2 查询list
            List<String> shopTypeList = stringRedisTemplate.opsForList().range(SHOP_TYPE_LIST_KEY, 0, Long.parseLong(listSize));
            if (shopTypeList!=null && shopTypeList.size() > 0) {
                // 1.3 缓存命中 list元素类型转换
                resultList = shopTypeList.stream().
                        map( shopString -> JSONUtil.toBean(shopString, ShopType.class))
                        .collect(Collectors.toList());
                return Result.ok(resultList);
            }
        }

        // 2. 缓存没有命中，查询数据库
        resultList = query().orderByAsc("sort").list();

        // 3. 数据库没有查到数据
        if (resultList.size() <= 0) {
            return Result.fail("未能查询到类型数据！");
        }
        // 4. 返回数据转换类型，存入redis
        // 存入size
        stringRedisTemplate.opsForValue().set(SHOP_TYPE_LIST_SIZE_KEY, String.valueOf(resultList.size()));
        // 存入list
        stringRedisTemplate.opsForList().rightPushAll(SHOP_TYPE_LIST_KEY,
                resultList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList()));
        // 5. 返回结果
        return Result.ok(resultList);
    }
}
