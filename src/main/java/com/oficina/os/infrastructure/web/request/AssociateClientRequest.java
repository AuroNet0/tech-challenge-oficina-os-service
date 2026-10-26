package com.oficina.os.infrastructure.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssociateClientRequest(@NotNull UUID filialOrigemId, @NotBlank @Size(max = 200) String motivo) { }
