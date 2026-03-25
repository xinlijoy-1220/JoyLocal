package com.joylocal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.joylocal.mapper")
@SpringBootApplication
public class JoyLocalApplication {
    //
    public static void main(String[] args) {
        SpringApplication.run(JoyLocalApplication.class, args);
    }

}
