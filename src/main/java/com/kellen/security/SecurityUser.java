package com.kellen.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Getter
@AllArgsConstructor
public class SecurityUser implements Serializable {

    private String userId;
    private String username;
    private String tenantId;
    private List<String> authorities;
}
