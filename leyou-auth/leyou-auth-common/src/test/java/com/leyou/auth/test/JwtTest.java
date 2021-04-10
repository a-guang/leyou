package com.leyou.auth.test;

import com.leyou.common.pojo.UserInfo;
import com.leyou.common.utils.JwtUtils;
import com.leyou.common.utils.RsaUtils;
import org.junit.Before;
import org.junit.Test;

import java.security.PrivateKey;
import java.security.PublicKey;

public class JwtTest {

    private static final String pubKeyPath = "C:\\Users\\Jessy Guang\\IdeaProjects\\hm49\\code\\rsa\\rsa.pub";//公钥路径

    private static final String priKeyPath = "C:\\Users\\Jessy Guang\\IdeaProjects\\hm49\\code\\rsa\\rsa.pri";//私钥路径

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @Before
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        // 生成token
        String token = JwtUtils.generateToken(new UserInfo(20L, "jack"), privateKey, 5);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6MjAsInVzZXJuYW1lIjoiamFjayIsImV4cCI6MTU5MzIzNTgzNH0.jbU8AOsHndSrRhAzExla8x_gVZdPyQx_zUhH0I2KwmeVy7MWmLTa6ZfmnmG6KWVhMx_ZipAbanhOySFQUaUE9UNK436Em39AVw6iTZnK3UpaVWjDvmvgxKs2ctdeQhRcK4B9nQUuO1iXZkF7Ft8QB8JTRL2Isu2sG7GAXZ3nCb4";

        // 解析token
        UserInfo user = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + user.getId());
        System.out.println("userName: " + user.getUsername());
    }
}