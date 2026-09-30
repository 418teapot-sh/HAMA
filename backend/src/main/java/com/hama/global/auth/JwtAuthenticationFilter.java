package com.hama.global.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <액세스 토큰>} 을 읽어 SecurityContext 에 {@link AuthUser} 를 넣습니다.
 *
 * <p>토큰이 없거나 틀려도 여기서 막지 않고 그냥 통과시킵니다. 막는 건 뒤쪽 인가 단계의 몫이고,
 * 거기서 {@link JwtAuthenticationEntryPoint} 가 401 ApiResponse 를 내보냅니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final JwtTokenProvider jwtTokenProvider;

    // Spring Framework 7 은 JSpecify 로 null 불가가 기본이라 파라미터에 @NonNull 을 달지 않습니다.
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        // 여기서 예외가 나가면 GlobalExceptionHandler 가 못 잡습니다. @RestControllerAdvice 는
        // DispatcherServlet 안에서만 동작하고 필터는 그 바깥이라, 컨테이너 기본 에러 페이지(HTML)가
        // 나갑니다. 프론트는 { success, data, error, traceId } 를 기대하는데 파싱할 수 없는 응답을 받고,
        // 401 로 복구 가능한 상황이 500 이 됩니다. parseAccessUser 는 예외를 던지지 않지만,
        // 혹시 모를 런타임 예외까지 여기서 끊습니다.
        try {
            if (token != null) {
                jwtTokenProvider.parseAccessUser(token).ifPresent(authUser -> {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(authUser, null, AUTHORITIES);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            log.warn("토큰을 해석하지 못해 인증 없이 진행합니다: {} {} ({})",
                    request.getMethod(), request.getRequestURI(), e.toString());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
