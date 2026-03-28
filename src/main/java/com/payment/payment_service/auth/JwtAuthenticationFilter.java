package com.payment.payment_service.auth;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.payment.payment_service.auth.service.JwtService;
import com.payment.payment_service.config.AuthenticatedUser;
import com.payment.payment_service.user.type.UserType;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain filterChain) 
    throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = header.substring(7);

        try{
            Claims claims = jwtService.validateToken(token);

            String jti = claims.getId();

            if(Boolean.TRUE.equals(stringRedisTemplate.hasKey("blacklist:" + jti))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token has been revoked\"}");
                return;
            }

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String type = claims.get("type", String.class);
            UserType userType = UserType.valueOf(type);
            

            AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, email, userType);
            
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + userType.name()));
            
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities);
            
            SecurityContextHolder.getContext().setAuthentication(authentication);


        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        } catch (Exception e) {
            // Redis down, erro inesperado — fail closed
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Service temporarily unavailable\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
