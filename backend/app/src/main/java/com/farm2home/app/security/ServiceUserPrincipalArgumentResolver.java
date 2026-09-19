package com.farm2home.app.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Adapts the one JVM-wide {@link SpikePrincipal} (established from the JWT by
 * {@link SpikeJwtAuthenticationFilter}) to each embedded service's own
 * {@code com.farm2home.<svc>.config.UserPrincipal} record, so controller parameters like
 * {@code @AuthenticationPrincipal UserPrincipal principal} keep working unchanged in the
 * aggregated app.
 *
 * <p><b>Why this is needed:</b> in the microservice deployment each service's
 * {@code GatewayHeaderAuthFilter} builds <i>that service's own</i> package-private
 * {@code UserPrincipal} record from the gateway headers. In the single JVM there is one filter
 * and one principal type. Spring Security's {@code AuthenticationPrincipalArgumentResolver}
 * sees the actual principal ({@code SpikePrincipal}) is not assignable to
 * {@code customer.config.UserPrincipal} and resolves the argument to {@code null} — every
 * ownership-checked customer/inventory endpoint would then NPE. This resolver runs <b>before</b>
 * the built-in resolvers (registered via a {@code RequestMappingHandlerAdapter}
 * {@code BeanPostProcessor} in {@link WebMvcArgumentResolverConfig}) and reconstructs the exact
 * record the controller expects.
 *
 * <p><b>Safety:</b> every service {@code UserPrincipal} is a {@code record} with the identical
 * canonical constructor {@code (UUID userId, String mobile, Set<String> roles)} — verified
 * across all 12. This resolver requires exactly that constructor and fails loudly if a future
 * service deviates, rather than silently mis-mapping. No authorization data is lost: userId,
 * mobile and the full role set (which drives each record's {@code isAdmin()} /
 * {@code isDeliveryPartner()}) are all carried through from the JWT claims.
 */
public class ServiceUserPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_PRINCIPAL_SUFFIX = ".config.UserPrincipal";

    private final ConcurrentMap<Class<?>, Constructor<?>> ctorCache = new ConcurrentHashMap<>();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return type.isRecord()
                && type.getName().startsWith("com.farm2home.")
                && type.getName().endsWith(USER_PRINCIPAL_SUFFIX);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SpikePrincipal principal)) {
            // Unauthenticated/anonymous — leave null; the endpoint's own auth rules reject it.
            return null;
        }
        Constructor<?> ctor = ctorCache.computeIfAbsent(parameter.getParameterType(), this::canonicalConstructor);
        try {
            return ctor.newInstance(principal.userId(), principal.mobile(), principal.roles());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Failed to adapt SpikePrincipal to " + parameter.getParameterType().getName(), ex);
        }
    }

    private Constructor<?> canonicalConstructor(Class<?> recordType) {
        try {
            Constructor<?> c = recordType.getDeclaredConstructor(UUID.class, String.class, Set.class);
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(recordType.getName()
                    + " does not have the expected (UUID, String, Set) canonical constructor — "
                    + "the single-JVM principal adapter must be updated for it", e);
        }
    }
}
