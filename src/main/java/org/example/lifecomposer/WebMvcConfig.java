package org.example.RepositoryDemo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源访问路径
        registry.addResourceHandler("/description/**")
                .addResourceLocations("file:./external/static/description/")
                .setCachePeriod(3600) // 设置缓存时间为1小时
                .resourceChain(true);
    }
}