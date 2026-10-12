package com.oficina.os.application.port.in;

public record ClienteCommand(String nome, String cpfCnpj, String email, String telefone) {
}
