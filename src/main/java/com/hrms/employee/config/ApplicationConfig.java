
package com.hrms.employee.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;

import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

 private final UserDetailsService userDetailsService;

 @Bean
 public PasswordEncoder passwordEncoder() {
     return new BCryptPasswordEncoder();
 }


 @Bean
 public AuthenticationManager authenticationManager(
         AuthenticationConfiguration config) throws Exception {
	 	DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
	   // provider.setUserDetailsService(userDetailsService);
	    provider.setPasswordEncoder(passwordEncoder());
	    provider.setHideUserNotFoundExceptions(true);
     return config.getAuthenticationManager();
 }
 
 @Bean                                    // ← moved here
 public ObjectMapper objectMapper() {
     return new ObjectMapper();
 }
 
}