package com.dy.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dy.common.R;
import com.dy.common.SystemConstant;
import com.dy.common.WeixinProperties;
import com.dy.entity.User;

import com.dy.service.UserService;

import com.dy.utils.*;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private RedisUtils redisUtils;

    @Autowired
    private WeixinProperties weixinProperties;

    @Autowired
    private UserService userService;

    @Autowired
    private Environment environment;

    @Autowired
    private HttpClientUtil httpClientUtil;

    /**
     * @param user
     * @return
     */
    @PostMapping("/sendMsg")
    public R<String> sendMsg(@RequestBody User user) {
        //获取手机号
        String phone = user.getPhone();
        if (StringUtils.isNotEmpty(phone)) {
            //生成随机验证码
            String code = ValidateCodeUtils.generateValidateCode(6).toString();
            //调用腾讯云Api完成发送短信
            log.info("code={}", code);
            SMSUtils.sendShortMessage(SMSUtils.VALIDATE_CODE,phone,code);
            redisUtils.saveValidateCode2Redis(phone, code);
            return R.success("验证码发送成功");
        }
        return R.error("验证码发送失败");

    }

    @PostMapping("/wxLogin")
    public R<HashMap<String, String>> wxLogin(@RequestBody User userInfo) throws Exception {
        System.out.println(userInfo.getCode());
        String jscode2sessionUrl = environment.getProperty("weixin.jscode2sessionUrl")  + "?appid=" + environment.getProperty("weixin.appid") + "&secret=" +environment.getProperty("weixin.secret") + "&js_code=" + userInfo.getCode() + "&grant_type=authorization_code";
        System.out.println(jscode2sessionUrl);
        String result = httpClientUtil.sendHttpGet(jscode2sessionUrl);
        System.out.println(result);
        JSONObject jsonObject = JSON.parseObject(result);
        String openid = jsonObject.get("openid").toString();
        System.out.println(openid);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getOpenid, openid);
        User user = userService.getOne(queryWrapper);
        if (user == null) {
            userInfo.setOpenid(openid);
            userInfo.setCreateTime(LocalDateTime.now());
            userService.save(userInfo);
        } else {
            user.setNickName(userInfo.getNickName());
            user.setAvatarUrl(userInfo.getAvatarUrl());
            userService.updateById(user);

        }
        User one = userService.getOne(queryWrapper);
        String userId = one.getId().toString();
        String phone = one.getPhone();

        String token = JwtUtils.createJWT(userId, userInfo.getNickName(), SystemConstant.JWT_TTL);
        Claims claims = JwtUtils.parseJWT(token);
        String id = claims.getId();
        System.out.println("userId" + id);
        HashMap<String,String> map = new HashMap<>();
        map.put("token",token);
        map.put("phone",phone);

        return R.success(map);

    }

    @PostMapping("wxGetPhone")
    public R<JSONObject> wxGetPhone(@RequestBody User userInfo) {
        String code = userInfo.getCode();
        System.out.println("code--" + code);
        String token_url = environment.getProperty("weixin.getAccessTokenUrl") + "appid=" + environment.getProperty("weixin.appid") + "&secret=" + environment.getProperty("weixin.secret");
        System.out.println("token_url--" + token_url);
        String token = httpClientUtil.sendHttpGet(token_url);
        System.out.println("token--" + token);

        String access_token = JSON.parseObject(token).get("access_token").toString();
        System.out.println("access_token--" + access_token);
        String url = environment.getProperty("weixin.getPhoneNumberUrl") + access_token;
        System.out.println("url--" + url);

        JSONObject params = new JSONObject();
        params.put("code", code);
        String result = httpClientUtil.sendHttpPostJson(url, params);
        JSONObject parse = (JSONObject) JSON.parse(result);
        System.out.println(parse);
        Object phone_info = parse.get("phone_info");
        System.out.println(phone_info);
        JSONObject phoneInfo = (JSONObject) JSON.parse(String.valueOf(phone_info));
        String phoneNumber = (String) phoneInfo.get("phoneNumber");
        System.out.println(phoneNumber);
        System.out.println("UserId" + UserThreadLocal.get().getId());
        User byId = userService.getById(UserThreadLocal.get().getId());
        log.info(byId.getPhone());
        if(StringUtils.isEmpty(byId.getPhone())){
            byId.setPhone(phoneNumber);
            userService.updateById(byId);
        }
        //if (!byId.getPhone().equals(phoneNumber)){
        //    byId.setPhone(phoneNumber);
        //    userService.updateById(byId);
        //}



        return R.success(parse);
    }

    @PostMapping("/login")
    public R<User> login(@RequestBody Map map) {

        return userService.login(map);
    }

    @PostMapping("/loginout")
    public R<String> logout() {
        return userService.logout();
    }

    @GetMapping("/getUserInfo/{phone}")
    public R<User> getUserInfo(@PathVariable String phone) {

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(phone != null, User::getPhone, phone);
        User byId = userService.getOne(queryWrapper);
        if (byId != null) {
            return R.success(byId);
        }
        return R.error("用户信息查询失败！");

    }

    @PutMapping
    public R<String> updateUser(@RequestBody User user) {
        userService.updateUserById(user);
        return R.success("修改成功！");
    }

    @GetMapping("/getUserCount")
    public R<Long> getUserCount() {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        long count = userService.count(queryWrapper);
        return R.success(count);
    }

    /**
     * 会员分页条件查询
     *
     * @param page
     * @param pageSize
     * @param phone
     * @return
     */
    @GetMapping("/page")
    public R<Page> page(int page, int pageSize, String phone) {
        //构造分页构造器
        Page pageInfo = new Page<>(page, pageSize);
        return userService.pageQuery(pageInfo, phone);
    }

    /**
     * 根据ID修改会员信息
     *
     * @param user
     * @return
     */
    @PutMapping("/updateStatus")
    public R<String> update(@RequestBody User user) {
        userService.updateById(user);
        return R.success("会员信息修改成功！");
    }

    /**
     * 修改页面，根据ID回显数据
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public R<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) {
            return R.success(user);
        }
        return R.error("没有查询到对应会员信息");
    }


}
