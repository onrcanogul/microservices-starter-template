package com.template.security.jwt;


import com.template.security.property.SecurityProperties;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.*;
import java.util.stream.Collectors;

class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final SecurityProperties.Jwt props;

    JwtRoleConverter(SecurityProperties.Jwt props) {
        this.props = props;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();

        if (props.isAddRealmRoles()) {
            roles.addAll(extractRealmRoles(jwt));
        }
        if (props.isAddResourceRoles() && props.getResourceId() != null && !props.getResourceId().isBlank()) {
            roles.addAll(extractResourceRoles(jwt, props.getResourceId()));
        }

        Collection<GrantedAuthority> scopes = List.of();
        if (props.isAddScopeAuthorities()) {
            JwtGrantedAuthoritiesConverter def = new JwtGrantedAuthoritiesConverter();
            scopes = def.convert(jwt);
        }

        String prefix = props.getAuthorityPrefix();
        Collection<GrantedAuthority> roleAuthorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority(prefix + r.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toList());

        List<GrantedAuthority> result = new ArrayList<>(roleAuthorities);
        result.addAll(scopes);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> extractRealmRoles(Jwt jwt) {
        Object ra = jwt.getClaim("realm_access");
        if (ra instanceof Map<?,?> m) {
            Object rl = m.get("roles");
            if (rl instanceof Collection<?> c) {
                return c.stream().map(Object::toString).collect(Collectors.toSet());
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> extractResourceRoles(Jwt jwt, String resourceId) {
        Object ra = jwt.getClaim("resource_access");
        if (ra instanceof Map<?,?> m) {
            Object client = m.get(resourceId);
            if (client instanceof Map<?,?> cm) {
                Object rl = cm.get("roles");
                if (rl instanceof Collection<?> c) {
                    return c.stream().map(Object::toString).collect(Collectors.toSet());
                }
            }
        }
        return List.of();
    }
}

