package alp.alpinsense.webauthn.models;

import co.nstant.in.cbor.model.Map;
import alp.alpinsense.webauthn.fido.ctap2.Messages.RequestCommandCTAP2;
import alp.alpinsense.webauthn.exceptions.CtapException;

public abstract class AuthenticatorOptions {
    public abstract AuthenticatorOptions fromCBor(Map inputMap);
    public abstract void areWellFormed() throws CtapException;
    public final RequestCommandCTAP2 action;

    public AuthenticatorOptions(RequestCommandCTAP2 action) {
        this.action = action;
    }
}
