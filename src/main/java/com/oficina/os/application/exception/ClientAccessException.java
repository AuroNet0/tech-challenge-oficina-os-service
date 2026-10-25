package com.oficina.os.application.exception;

public final class ClientAccessException extends RuntimeException {
    public enum Motivo { AUTENTICACAO, SERVICO_NAO_AUTORIZADO, INDISPONIVEL, RESPOSTA_INVALIDA }
    private final Motivo motivo;

    public ClientAccessException(Motivo motivo) {
        super("Verificacao de vinculo indisponivel: " + motivo.name());
        this.motivo = motivo;
    }

    public Motivo motivo() { return motivo; }
}
