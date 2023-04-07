# 代码使用说明
黑马redis学习项目：
- 学习项目，完整项目在下面项目的master分支上
- 获取方式见下方
## 下载
克隆完整项目
```git
git clone https://gitee.com/huyi612/hm-dianping.git
```
切换分支
```git
git checkout init
```



## 学习笔记

### 短信登录

基于 session 实现注意事项：

1. 生成短信验证码，将验证码存入 session 中
2. 登录时校验验证码登录，保存用户信息到数据库和 session 中，存入session中是为了其他需要使用用户信息的接口方便查出
3. 使用拦截器，对于某些需要使用用户信息的接口，需要先确认 session 中是否有 user 信息，有的话放行并将用户数据放在一个 ThreadLocal 对象中，没有的话拦截并报错
4. 使用 session 时，浏览器会存储 cookie ，通过 cookie 中的 sessionId 可以在 tomcat中找到对应的session对象
5. 存在的问题：session共享问题，多台tomcat并不能共享session存储空间

基于 redis 的实现：

1. 生成验证码，验证码存入 redis 中，其中 key 是电话号码，value 是验证码
2. 登录时校验验证码，因为登录时需要传入电话号码，所以可以很容易从 redis 中取出验证码来进行校验。校验通过后保存用户信息时，保存到数据库的操作和使用 session 一样，但是保存到 redis 中时，需要生成一个 token 作为 key，并且将 key 返回给客户端
3. 同上3，但是客户端存储了 token 后，通过 token 获取用户信息，此处使用 token 的原因是不会泄露用户信息。

