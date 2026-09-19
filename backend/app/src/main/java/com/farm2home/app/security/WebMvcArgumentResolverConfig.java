package com.farm2home.app.security;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers {@link ServiceUserPrincipalArgumentResolver} <b>ahead of every built-in
 * resolver</b> on the MVC {@link RequestMappingHandlerAdapter}.
 *
 * <p>A resolver added the ordinary way ({@code WebMvcConfigurer#addArgumentResolvers}) lands in
 * {@code customArgumentResolvers}, which run <i>after</i> Spring Security's
 * {@code AuthenticationPrincipalArgumentResolver} — and that one already claims (and nulls out)
 * every {@code @AuthenticationPrincipal <svc>.config.UserPrincipal} parameter. Prepending to the
 * full resolver list via a {@code BeanPostProcessor} is the documented way to get in front of
 * it.
 */
@Configuration
public class WebMvcArgumentResolverConfig {

    @Bean
    static BeanPostProcessor serviceUserPrincipalResolverRegistrar() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof RequestMappingHandlerAdapter adapter) {
                    List<HandlerMethodArgumentResolver> existing = adapter.getArgumentResolvers();
                    if (existing != null) {
                        List<HandlerMethodArgumentResolver> updated = new ArrayList<>(existing.size() + 1);
                        updated.add(new ServiceUserPrincipalArgumentResolver());
                        updated.addAll(existing);
                        adapter.setArgumentResolvers(updated);
                    }
                }
                return bean;
            }
        };
    }
}
