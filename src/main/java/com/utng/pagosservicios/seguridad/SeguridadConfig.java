package com.utng.pagosservicios.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * API sin estado: cada peticion trae su JWT; no hay sesion ni cookies. Por eso
 * CSRF se desactiva (ese ataque depende de credenciales que el navegador envia
 * solo, como cookies de sesion, y aqui el token va explicito en Authorization).
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, DelegadorErroresSeguridad errores) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token", "/api/v1/auth/registro").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                                .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/api/v1/pagos", "/api/v1/pagos/**")
                                .hasAuthority("SCOPE_" + EmisorTokens.SCOPE_PAGOS)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(recurso -> recurso
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores))
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores));
        return http.build();
    }

    @Bean
    public SecretKey claveJwt(SeguridadProperties propiedades) {
        return new SecretKeySpec(propiedades.jwt().secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey claveJwt, SeguridadProperties propiedades) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(claveJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Firma, expiracion y emisor: un token de otro emisor se rechaza aunque la firma fuera valida.
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(propiedades.jwt().emisor()));
        return decodificador;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
