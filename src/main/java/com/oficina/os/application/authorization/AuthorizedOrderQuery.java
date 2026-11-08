package com.oficina.os.application.authorization;

import com.oficina.os.application.port.out.OrdemServicoSearchCriteria;
import com.oficina.os.application.port.out.PageResult;
import com.oficina.os.domain.OrdemServico;

public interface AuthorizedOrderQuery {
    PageResult<OrdemServico> search(OrdemServicoSearchCriteria criteria, Actor actor);
}
