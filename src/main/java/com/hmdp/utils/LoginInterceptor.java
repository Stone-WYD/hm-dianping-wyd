package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

public class LoginInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        /*
        使用 session 实现
        // 1. 获取session
        HttpSession session = request.getSession();
        
        // 2. 获取session中的用户
        UserDTO userDTO = (UserDTO) session.getAttribute("user");
        
        // 3. 判断用户是否存在
        if (userDTO == null) {
            // 4. 不存在，拦截
            response.setStatus(401);
            return false;
        }
        
        // 5. 存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);

        // 6. 放行
        return true;*/


        /*
        使用 redis 实现
        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 2. 不存在，拦截
            response.setStatus(401);
            return false;
        }
        String key = LOGIN_USER_KEY + token;
        // 3.基于 token 获取 redis 中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().
                entries(key);

        // 4. 判断用户是否存在
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }

        // 5. 将查询到的 Hash 数据转化为 UserDTO 对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 6. 存在，保存用户信息到 ThreadLocal 中
        UserHolder.saveUser(userDTO);

        // 7. 刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8. 放行
        return true;*/

        // 1. 判断是否需要拦截（ThreadLocal 中是否能取出用户）
        /*if (UserHolder.getUser() == null) {
            // 没有，需要拦截
            response.setStatus(401);
            return false;
        }*/

        // 1. 获取请求头中的 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 2. 不存在，拦截
            response.setStatus(401);
            return false;
        }
        String key = LOGIN_USER_KEY + token;
        // 3.基于 token 获取 redis 中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        return !userMap.isEmpty();

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex){

    }
}
