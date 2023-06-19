package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 2.查询blog有关用户
        queryBolgUser(blog);
        // 3.是否已经点赞
        isLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBolgUser(blog);
            isLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取用户信息
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null){
            // 3.如果未点赞
            // 3.1 数据库blog表点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2 redis set列表增加用户id
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else {
            // 4.如果已点赞
            // 4.1 数据库blog表点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2 redis set列表移除用户id
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1.查询前5个点赞用户id
        Set<String> top5Id = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 2. 解析用户id
        if (top5Id == null || top5Id.isEmpty()) return Result.ok(Collections.emptyList());
        List<Long> ids = top5Id.stream().map(Long::valueOf).collect(Collectors.toList());
        // 3. 根据用户id查询用户
        String idStr = StrUtil.join(",", ids);
        List<User> users = userService.query().in("id", ids)
                .last("ORDER BY FIELD(id, " + idStr + ")").list();
        // List<User> users = userService.listByIds(ids);
        // 4. 返回
        List<UserDTO> data = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(data);
    }

    private void isLiked(Blog blog) {
        if (blog.getUserId() == null) {
            return;
        }
        String userId = blog.getUserId().toString();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        blog.setIsLike(score != null);
    }


    private void queryBolgUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
