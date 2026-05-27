package com.kellen.config.actuator;

import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointMapping;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Collection;
import java.util.List;

public class CustomWebMvcEndpointHandlerMapping extends WebMvcEndpointHandlerMapping {

    /**
     * Creates a new {@code WebMvcEndpointHandlerMapping} instance that provides mappings
     * for the given endpoints.
     *
     * @param endpointMapping            the base mapping for all endpoints
     * @param endpoints                  the web endpoints
     * @param endpointMediaTypes         media types consumed and produced by the endpoints
     * @param corsConfiguration          the CORS configuration for the endpoints or {@code null}
     * @param linksResolver              resolver for determining links to available endpoints
     * @param shouldRegisterLinksMapping whether the links endpoint should be registered
     */
    public CustomWebMvcEndpointHandlerMapping(EndpointMapping endpointMapping, Collection<ExposableWebEndpoint> endpoints, EndpointMediaTypes endpointMediaTypes, CorsConfiguration corsConfiguration, EndpointLinksResolver linksResolver, boolean shouldRegisterLinksMapping) {
        super(endpointMapping, endpoints, endpointMediaTypes, corsConfiguration, linksResolver, shouldRegisterLinksMapping);
    }

    @Override
    protected void extendInterceptors(List<Object> interceptors) {
        super.extendInterceptors(interceptors);
        interceptors.add(new ActuatorInterceptor());
    }
}
