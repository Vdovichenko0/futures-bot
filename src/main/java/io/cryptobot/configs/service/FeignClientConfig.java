//package io.cryptobot.configs.service;
//
//import feign.RequestInterceptor;
//import feign.RequestTemplate;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class FeignClientConfig {
//
//    @Value("${spring.application.name}")
//    private String serviceName;
//
//    @Value("${symbols.key}")
//    private String serviceKey;
//
//    @Value("${accounting.key}")
//    private String serviceAccountingKey;
//
//    @Bean
//    public RequestInterceptor symbolsRequestInterceptor() {
//        return (RequestTemplate template) -> {
//            if (template.feignTarget().name().equalsIgnoreCase("symbols-service")) {
//                template.header("X-ServiceName", serviceName);
//                template.header("X-Service-Key", serviceKey);
//            }
//        };
//    }
//
//    @Bean
//    public RequestInterceptor accountingRequestInterceptor() {
//        return (RequestTemplate template) -> {
//            if (template.feignTarget().name().equalsIgnoreCase("accounting")) {
//                template.header("X-ServiceName", serviceName);
//                template.header("X-Service-Key", serviceAccountingKey);
//            }
//        };
//    }
//}
