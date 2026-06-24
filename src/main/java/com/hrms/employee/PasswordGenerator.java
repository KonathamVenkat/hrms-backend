//package com.hrms.employee;
//
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//
//public class PasswordGenerator {
//    public static void main(String[] args) {
//        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
//
//        String[] passwords = {"mgr123", "emp123"};
//
//        for (String pwd : passwords) {
//            System.out.println(pwd + " => " + encoder.encode(pwd));
//        }
//    }
//}