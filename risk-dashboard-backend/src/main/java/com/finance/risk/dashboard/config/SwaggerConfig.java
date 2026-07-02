package com.finance.risk.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * Swagger API 文档配置
 * 访问地址: http://localhost:8080/swagger-ui/index.html
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.OAS_30)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.finance.risk.dashboard.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("金融交易风险实时监控平台 - API 文档")
                .description("基于 Lambda 架构的金融风控系统可视化展示端接口文档\n\n"
                        + "**数据接入接口** — 供上游数据处理程序 (Spark Streaming/Spark SQL) 调用\n"
                        + "**展示查询接口** — 供前端 Vue 大屏调用\n\n"
                        + "对应需求文档版本: v1.0")
                .version("1.0.0")
                .contact(new Contact("Risk Dashboard Team", "", ""))
                .build();
    }
}
