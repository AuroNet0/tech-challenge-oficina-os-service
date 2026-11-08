package com.oficina.os.application.authorization;

public final class AuthorizationDenied extends RuntimeException {
    public AuthorizationDenied() { super("Access denied"); }
}
