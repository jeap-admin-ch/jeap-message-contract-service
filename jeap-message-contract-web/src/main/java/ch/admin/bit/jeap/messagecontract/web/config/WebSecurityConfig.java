package ch.admin.bit.jeap.messagecontract.web.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
class WebSecurityConfig {

    private static final String WRITE_ROLE = "messagecontract-write";
    private static final String UPLOAD_CONTRACT_ROLE = "messagecontract-contract-upload";

    private final WebSecurityProperties webSecurityProperties;

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**", "/error")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(withDefaults())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/contracts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/deployments").permitAll()
                        .anyRequest().hasAnyRole(WRITE_ROLE, UPLOAD_CONTRACT_ROLE));
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {

        List<UserDetails> users = new ArrayList<>();

        if (webSecurityProperties.getWriteUsers() != null) {
            users.addAll(webSecurityProperties.getWriteUsers()
                    .stream().map(user -> User
                            .withUsername(user.getUsername())
                            .password(user.getPassword())
                            .roles(WRITE_ROLE)
                            .build()).toList());
        }

        if (webSecurityProperties.getUploadContractUsers() != null) {
            users.addAll(webSecurityProperties.getUploadContractUsers()
                    .stream().map(user -> User
                            .withUsername(user.getUsername())
                            .password(user.getPassword())
                            .roles(UPLOAD_CONTRACT_ROLE)
                            .build()).toList());
        }

        return new InMemoryUserDetailsManager(users);
    }
}
