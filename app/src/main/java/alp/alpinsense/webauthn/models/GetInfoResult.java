package alp.alpinsense.webauthn.models;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import alp.alpinsense.webauthn.exceptions.VirgilException;
import alp.alpinsense.webauthn.fido.ctap2.CtapSuccessOutputStream;
import alp.alpinsense.webauthn.fido.ctap2.Messages;

public class GetInfoResult extends AuthenticatorResult {

    public boolean clientPin;
    public byte[] aaguid;

    public GetInfoResult(boolean clientPin, byte[] aaguid) {
        this.clientPin = clientPin;
        this.aaguid = aaguid;
    }

    @Override
    public byte[] asCBOR() throws VirgilException {
        CtapSuccessOutputStream baos = new CtapSuccessOutputStream();
        try {
            MapBuilder<CborBuilder> builder = new CborBuilder().addMap();

            builder.putArray(Messages.GET_INFO_RESPONSE_VERSIONS)
                .add("FIDO_2_0")
                .add("FIDO_2_1")
                .add("U2F_V2")
                .end();

            ArrayBuilder<MapBuilder<CborBuilder>> arrayBuilder = builder.putArray(Messages.GET_INFO_RESPONSE_EXTENSIONS);
            for (String extension: AuthenticatorExtension.supportedExtension) {
                arrayBuilder.add(extension);
            }
            builder = arrayBuilder.end();

            builder.put(Messages.GET_INFO_RESPONSE_AAGUID, aaguid);

            builder.putMap(Messages.GET_INFO_RESPONSE_OPTIONS)
                    .put("rk", true)
                    .put("up", true)
                    .put("uv", true)
                    .put("plat", false)
                    .put("clientPin", clientPin)
                .end();

            builder.put(Messages.GET_INFO_RESPONSE_MAX_MSG_SIZE, Messages.MAX_CBOR_MSG_SIZE);

            arrayBuilder = builder.putArray(Messages.GET_INFO_RESPONSE_PIN_PROTOCOLS);
            for (Long pinProtocol: ClientPINOptions.supportedProtocols) {
                arrayBuilder.add(pinProtocol);
            }
            builder = arrayBuilder.end();

            builder.put(Messages.GET_INFO_RESPONSE_MAX_CREDENTIAL_COUNT_IN_LIST, 5L);
            builder.put(Messages.GET_INFO_RESPONSE_MAX_CREDENTIAL_ID_LENGTH, 32 + 32 + 1 + 3 * 64);

            builder.putArray(Messages.GET_INFO_RESPONSE_TRANSPORTS)
                    .add("ble")
                    .add("usb")
                .end();

            builder.putArray(Messages.GET_INFO_RESPONSE_ALGORITHMS)
                    .addMap()
                    .put("alg", -7L)
                    .put("type", "public-key")
                    .end()
                .end();

            new CborEncoder(baos).encode(builder.end().build());

        } catch (CborException e) {
            throw new VirgilException("couldn't serialize to cbor", e);
        }
        return baos.toByteArray();
    }
}
