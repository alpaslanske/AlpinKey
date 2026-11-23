package alp.alpinsense.webauthn.util;

import java.util.List;

import alp.alpinsense.webauthn.models.PublicKeyCredentialSource;

public interface CredentialSelector {
    PublicKeyCredentialSource selectFrom(List<PublicKeyCredentialSource> credentialList);
}
