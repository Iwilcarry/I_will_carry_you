package com.macro.mall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.macro.mall.bo.AdminUserDetails;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.util.RequestUtil;
import com.macro.mall.dao.UmsAdminRoleRelationDao;
import com.macro.mall.dto.UmsAdminParam;
import com.macro.mall.dto.UpdateAdminPasswordParam;
import com.macro.mall.mapper.UmsAdminLoginLogMapper;
import com.macro.mall.mapper.UmsAdminMapper;
import com.macro.mall.mapper.UmsAdminRoleRelationMapper;
import com.macro.mall.model.*;
import com.macro.mall.security.util.JwtTokenUtil;
import com.macro.mall.security.util.SpringUtil;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.UmsAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 后台用户管理Service实现类
 * Created by macro on 2018/4/26.
 */
@Service
public class UmsAdminServiceImpl implements UmsAdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UmsAdminServiceImpl.class);
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UmsAdminMapper adminMapper;
    @Autowired
    private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Autowired
    private UmsAdminRoleRelationDao adminRoleRelationDao;
    @Autowired
    private UmsAdminLoginLogMapper loginLogMapper;

    @Override
    public UmsAdmin getAdminByUsername(String username) {
        UmsAdmin umsAdmin = getCacheService().getAdmin(username);
        //如果能从缓存中直接获取到那么就直接返回
        if (umsAdmin != null){
            return umsAdmin;
        }
        //缓存中没有从数据库中获取
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(username);
        //从数据库中查询
        List<UmsAdmin> adminList = adminMapper.selectByExample(example);
        //查询到的结果存放到缓存中并且返回
        if (adminList != null && adminList.size() > 0){
            umsAdmin = adminList.get(0);
            getCacheService().setAdmin(umsAdmin);
            return umsAdmin;
        }
        //如果没有查到那么就直接返回空
        return null;


//        //先从缓存中获取数据
//        UmsAdmin admin = getCacheService().getAdmin(username);
//        if (admin != null) return admin;
//        //缓存中没有从数据库中获取
//        UmsAdminExample example = new UmsAdminExample();
//        example.createCriteria().andUsernameEqualTo(username);
//        List<UmsAdmin> adminList = adminMapper.selectByExample(example);
//        if (adminList != null && adminList.size() > 0) {
//            admin = adminList.get(0);
//            //将数据库中的数据存入缓存中
//            getCacheService().setAdmin(admin);
//            return admin;
//        }
//        return null;
    }

    @Override
    public UmsAdmin register(UmsAdminParam umsAdminParam) {

        UmsAdmin umsAdmin = new UmsAdmin();
        BeanUtils.copyProperties(umsAdminParam,umsAdmin);

        umsAdmin.setStatus(1);
        umsAdmin.setCreateTime(new Date());

        UmsAdminExample umsAdminExample = new UmsAdminExample();

        umsAdminExample.createCriteria().andUsernameEqualTo(umsAdmin.getUsername());
        List<UmsAdmin> umsAdminList = adminMapper.selectByExample(umsAdminExample);

        if (umsAdminList.size() > 0){
            return null;
        }

        String encodePassword = passwordEncoder.encode(umsAdmin.getPassword());

        umsAdmin.setPassword(encodePassword);

        adminMapper.insert(umsAdmin);

        return umsAdmin;

//        UmsAdmin umsAdmin = new UmsAdmin();
//        BeanUtils.copyProperties(umsAdminParam, umsAdmin); //将umsAdminParam的内容拷贝到usmAdmin里面
//        umsAdmin.setCreateTime(new Date());
//        //设置状态
//        umsAdmin.setStatus(1);
//        //查询是否有相同用户名的用户
//        UmsAdminExample example = new UmsAdminExample();
//
//        example.createCriteria().andUsernameEqualTo(umsAdmin.getUsername());
//        List<UmsAdmin> umsAdminList = adminMapper.selectByExample(example);
//
//        if (umsAdminList.size() > 0) {
//            return null;
//        }
//
//        //将密码进行加密操作
//        String encodePassword = passwordEncoder.encode(umsAdmin.getPassword());
//        umsAdmin.setPassword(encodePassword);
//        adminMapper.insert(umsAdmin);
//
//        return umsAdmin;
    }

    @Override
    public String login(String username, String password) {
        String token = null;
        //密码需要客户端加密后传递
        try {
            UserDetails userDetails = loadUserByUsername(username);
            if (!passwordEncoder.matches(password, userDetails.getPassword())) {
                Asserts.fail("账户名或密码不正确");
            }
            if (!userDetails.isEnabled()) {
                Asserts.fail("账号已被禁止使用");
            }
            //spring security中认证功能需要一个封装类authentication 我们这里创建一个并且传入用户名和密码以及权限信息
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            //创建一个token
            token = jwtTokenUtil.generateToken(userDetails);

            insertLoginLog(username);

            }catch(AuthenticationException e){
                LOGGER.warn("登录异常:{}", e.getMessage());
            }
            return token;
    }

    /**
     * 添加登录记录
     * @param username 用户名
     */
    private void insertLoginLog(String username) {
        UmsAdmin admin = getAdminByUsername(username);
        if (admin == null) return;
        UmsAdminLoginLog loginLog = new UmsAdminLoginLog();//一个内含各种登录信息的类
        //设置id和创建日期
        loginLog.setAdminId(admin.getId());
        loginLog.setCreateTime(new Date());
        //获取http中的请求属性
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();//获取上下文
        HttpServletRequest request = servletRequestAttributes.getRequest();
        //获取请求的ip地址
        loginLog.setIp(RequestUtil.getRequestIp(request));

        //插入到数据库中
        loginLogMapper.insert(loginLog);

//        UmsAdmin admin = getAdminByUsername(username);
//        if(admin==null) return;
//        UmsAdminLoginLog loginLog = new UmsAdminLoginLog();
//        loginLog.setAdminId(admin.getId());
//        loginLog.setCreateTime(new Date());
//        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
//        HttpServletRequest request = attributes.getRequest();
//        loginLog.setIp(RequestUtil.getRequestIp(request));
//        loginLogMapper.insert(loginLog);
    }

    /**
     * 根据用户名修改登录时间
     */
    private void updateLoginTimeByUsername(String username) {
        UmsAdmin record = new UmsAdmin();
        record.setLoginTime(new Date());
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(username);
        adminMapper.updateByExampleSelective(record, example);
    }

    @Override
    public String refreshToken(String oldToken) {
        return jwtTokenUtil.refreshHeadToken(oldToken);
    }

    @Override
    public UmsAdmin getItem(Long id) {
        return adminMapper.selectByPrimaryKey(id);
    }

    @Override
    public List<UmsAdmin> list(String keyword, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        UmsAdminExample example = new UmsAdminExample();
        UmsAdminExample.Criteria criteria = example.createCriteria();
        if (!StrUtil.isEmpty(keyword)) {
            criteria.andUsernameLike("%" + keyword + "%");
            example.or(example.createCriteria().andNickNameLike("%" + keyword + "%"));
        }
        return adminMapper.selectByExample(example);
    }

    @Override
    public int update(Long id, UmsAdmin admin) {
        admin.setId(id);
        UmsAdmin rawAdmin = adminMapper.selectByPrimaryKey(id);
        if (admin.getPassword().equals(rawAdmin.getPassword())){
            admin.setPassword(null);
        }else{
            if (StrUtil.isEmpty(admin.getPassword())){
                admin.setPassword(null);
            }else{
                admin.setPassword(passwordEncoder.encode(admin.getPassword()));
            }
        }
        int count = adminMapper.updateByPrimaryKeySelective(admin);
        getCacheService().delAdmin(admin.getId());
        return count;

//        admin.setId(id);
//        UmsAdmin rawAdmin = adminMapper.selectByPrimaryKey(id);
//        if (rawAdmin.getPassword().equals(admin.getPassword())){
//            admin.setPassword(null);
//        }else{
//            if (StrUtil.isEmpty(admin.getPassword())){
//                admin.setPassword(null);
//            }else{
//                admin.setPassword(passwordEncoder.encode(admin.getPassword()));
//            }
//        }
//        int count = adminMapper.updateByPrimaryKeySelective(admin);
//        getCacheService().delAdmin(id);
//        return count;
//        admin.setId(id);
//        UmsAdmin rawAdmin = adminMapper.selectByPrimaryKey(id);
//        if(rawAdmin.getPassword().equals(admin.getPassword())){
//            //与原加密密码相同的不需要修改
//            admin.setPassword(null);
//        }else{
//            //与原加密密码不同的需要加密修改
//            if(StrUtil.isEmpty(admin.getPassword())){
//                admin.setPassword(null);
//            }else{
//                admin.setPassword(passwordEncoder.encode(admin.getPassword()));
//            }
//        }
//        int count = adminMapper.updateByPrimaryKeySelective(admin);
//        getCacheService().delAdmin(id);
//        return count;
    }

    @Override
    public int delete(Long id) {
        getCacheService().delAdmin(id);
        int count = adminMapper.deleteByPrimaryKey(id);
        getCacheService().delResourceList(id);
        return count;
    }

    @Override
    public int updateRole(Long adminId, List<Long> roleIds) {
        int count = roleIds == null ? 0 : roleIds.size();
        //先删除原来的关系
        UmsAdminRoleRelationExample adminRoleRelationExample = new UmsAdminRoleRelationExample();
        adminRoleRelationExample.createCriteria().andAdminIdEqualTo(adminId);
        adminRoleRelationMapper.deleteByExample(adminRoleRelationExample);
        //建立新关系
        if (!CollectionUtils.isEmpty(roleIds)) {
            List<UmsAdminRoleRelation> list = new ArrayList<>();
            for (Long roleId : roleIds) {
                UmsAdminRoleRelation roleRelation = new UmsAdminRoleRelation();
                roleRelation.setAdminId(adminId);
                roleRelation.setRoleId(roleId);
                list.add(roleRelation);
            }
            adminRoleRelationDao.insertList(list);
        }
        getCacheService().delResourceList(adminId);
        return count;
    }

    @Override
    public List<UmsRole> getRoleList(Long adminId) {
        return adminRoleRelationDao.getRoleList(adminId);
    }

    @Override
    public List<UmsResource> getResourceList(Long adminId) {
        //先从缓存中获取数据
        List<UmsResource> resourceList = getCacheService().getResourceList(adminId);
        if(CollUtil.isNotEmpty(resourceList)){
            return resourceList;
        }
        //缓存中没有从数据库中获取
        resourceList = adminRoleRelationDao.getResourceList(adminId);
        if(CollUtil.isNotEmpty(resourceList)){
            //将数据库中的数据存入缓存中
            getCacheService().setResourceList(adminId,resourceList);
        }
        return resourceList;
    }

    @Override
    public int updatePassword(UpdateAdminPasswordParam param) {
        if (StrUtil.isEmpty(param.getUsername())
                || StrUtil.isEmpty(param.getOldPassword())
                || StrUtil.isEmpty(param.getNewPassword())){
            //提交格式有问题
            return -1;
        }
        UmsAdminExample example = new UmsAdminExample();
        example.createCriteria().andUsernameEqualTo(param.getUsername());
        List<UmsAdmin> adminList = adminMapper.selectByExample(example);
        if (CollUtil.isEmpty(adminList)){
            //查无此人
            return -2;
        }
        UmsAdmin admin = adminList.get(0);
        if (!admin.getPassword().equals(param.getOldPassword())){
            //旧密码错误
            return -3;
        }
        admin.setPassword(passwordEncoder.encode(param.getNewPassword()));
        int count = adminMapper.updateByPrimaryKey(admin);
        getCacheService().delAdmin(admin.getId());
        return count;
//        if(StrUtil.isEmpty(param.getUsername())
//                ||StrUtil.isEmpty(param.getOldPassword())
//                ||StrUtil.isEmpty(param.getNewPassword())){
//            return -1;
//        }
//        UmsAdminExample example = new UmsAdminExample();
//        example.createCriteria().andUsernameEqualTo(param.getUsername());
//        List<UmsAdmin> adminList = adminMapper.selectByExample(example);
//        if(CollUtil.isEmpty(adminList)){
//            return -2;
//        }
//        UmsAdmin umsAdmin = adminList.get(0);
//        if(!passwordEncoder.matches(param.getOldPassword(),umsAdmin.getPassword())){
//            return -3;
//        }
//        umsAdmin.setPassword(passwordEncoder.encode(param.getNewPassword()));
//        adminMapper.updateByPrimaryKey(umsAdmin);
//        getCacheService().delAdmin(umsAdmin.getId());
//        return 1;
    }

    @Override
    public UserDetails loadUserByUsername(String username){
        //获取用户信息
        UmsAdmin admin = getAdminByUsername(username);
        if (admin != null) {
            //查询权限
            List<UmsResource> resourceList = getResourceList(admin.getId());
            return new AdminUserDetails(admin,resourceList);
        }
        throw new UsernameNotFoundException("用户名或密码错误");
    }

    @Override
    public UmsAdminCacheService getCacheService() {
        return SpringUtil.getBean(UmsAdminCacheService.class);
    }
}
