package alp.alpinsense.webauthn.models;

import alp.alpinsense.webauthn.exceptions.VirgilException;

public abstract class AuthenticatorResult {
    public abstract byte[] asCBOR() throws VirgilException;
}
