package com.leyou.user.service;

import com.leyou.common.utils.NumberUtils;
import com.leyou.user.mapper.UserMapper;
import com.leyou.user.pojo.User;
import com.leyou.user.utils.CodecUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AmqpTemplate amqpTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private static final String KEY_PREFIX = "user:verify:";

    /**
     * 校验数据是否可用,注册过的用户不可再注册
     * @param data
     * @param type
     * @return
     */
    public Boolean checkUser(String data, Integer type) {
        User record = new User();
        if(type == 1){
            record.setUsername(data);
        } else if(type == 2){
            record.setPhone(data);
        } else {
            return null;
        }
        return this.userMapper.selectCount(record) == 0;
    }

    /**
     * 发送验证码
     * @param phone
     */
    public void sendVerifyCode(String phone) {
        if(StringUtils.isBlank(phone)){
            return;
        }
        //生成验证码
        String code = NumberUtils.generateCode(6);

        //发送消息到rabbitmq
        Map<String,String> msg = new HashMap<>();
        msg.put("phone",phone);
        msg.put("code",code);
        this.amqpTemplate.convertAndSend("LEYOU.SMS.EXCHANGE","verifycode.sms",msg);
        //git

        //把验证码保存到redis中
        this.redisTemplate.opsForValue().set(KEY_PREFIX + phone, code, 5, TimeUnit.MINUTES);

    }

    /**
     * 实现用户注册功能
     * @param user
     * @param code
     */
    public void register(User user, String code) {
        //查询redis中的验证码
        String redisCode = this.redisTemplate.opsForValue().get(KEY_PREFIX + user.getPhone());
        //1.校验验证码
        if(!StringUtils.equals(code,redisCode)){
            return;
        }
        //2.生成盐
        String salt = CodecUtils.generateSalt();
        user.setSalt(salt);
        //3.加盐加密
        user.setPassword(CodecUtils.md5Hex(user.getPassword(),salt));
        //4.新增用户
        user.setId(null);
        user.setCreated(new Date());
        this.userMapper.insertSelective(user);
    }

    /**
     * 根据用户名和密码查询用户
     * @param username
     * @param password
     * @return
     */
    public User queryUser(String username, String password) {
        User record = new User();
        record.setUsername(username);
        User user = this.userMapper.selectOne(record);

        //判断user是否为空
        if(user == null){
            return null;
        }
        //获取盐,对用户输入的密码加盐加密
        password = CodecUtils.md5Hex(password, user.getSalt());
        //和数据库中的密码比较
        if (StringUtils.equals(password,user.getPassword())){
            return user;
        }
        return null;
    }
}
