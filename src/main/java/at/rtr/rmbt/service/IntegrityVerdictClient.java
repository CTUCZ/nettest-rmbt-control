package at.rtr.rmbt.service;

import at.rtr.rmbt.dto.IntegrityDecodeResult;

public interface IntegrityVerdictClient {

    /** Decodes the integrity token via Google's decodeIntegrityToken endpoint. Never throws. */
    IntegrityDecodeResult decode(String integrityToken);
}
