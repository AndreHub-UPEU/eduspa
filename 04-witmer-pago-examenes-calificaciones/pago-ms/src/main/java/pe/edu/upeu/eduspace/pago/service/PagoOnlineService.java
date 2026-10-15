package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.dto.PagoResponse;
import pe.edu.upeu.eduspace.pago.dto.RegistrarPagoRequest;

@Service
@RequiredArgsConstructor
public class PagoOnlineService {

    private final PagoService pagoService;

    @Transactional
    public PagoResponse pagarOnlineSimulado(RegistrarPagoRequest req, Long apoderadoUsuarioId) {
        req.setMedio("ONLINE_SIMULADO");
        if (req.getReferencia() == null || req.getReferencia().isBlank()) {
            req.setReferencia("SIM-TX-" + System.currentTimeMillis());
        }
        return pagoService.registrarPago(req, apoderadoUsuarioId);
    }
}

