package com.ayurveda.platform.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration.
 *
 * Registers the Hibernate6 module as a Spring bean so Spring Boot's Jackson
 * auto-configuration picks it up and applies it to the shared ObjectMapper.
 *
 * This is a global safety net: any endpoint that returns a JPA entity with an
 * uninitialized lazy association (a Hibernate/ByteBuddy proxy) will serialize
 * that association as {@code null} instead of throwing
 * "No serializer found for ... ByteBuddyInterceptor".
 *
 * FORCE_LAZY_LOADING is intentionally left disabled so we never trigger extra
 * database queries during serialization; uninitialized associations stay null.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        return new Hibernate6Module();
    }
}
