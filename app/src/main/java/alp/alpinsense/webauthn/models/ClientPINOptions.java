package alp.alpinsense.webauthn.models;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.UnsignedInteger;
import alp.alpinsense.webauthn.fido.ctap2.Messages;
import alp.alpinsense.webauthn.fido.ctap2.Messages.RequestCommandCTAP2;
import alp.alpinsense.webauthn.exceptions.CtapException;
import alp.alpinsense.webauthn.exceptions.CtapException.CtapError;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class ClientPINOptions extends AuthenticatorOptions {
    public Long pinProtocol = null;
    public Long subCommand = null;
    public PublicKey keyAgreement = null;
    public byte[] pinAuth = null;
    public byte[] newPinEnc = null;
    public byte[] pinHashEnc = null;
    private ECParameterSpec agreementParams;

    static final List<Long> supportedProtocols = new ArrayList<>(Arrays.asList(1L));

    public ClientPINOptions() {
        super(RequestCommandCTAP2.ClientPIN);
    }

    public void areWellFormed() throws CtapException {
        if (pinProtocol == null || subCommand == null) {
            throw new CtapException(CtapError.MISSING_PARAMETER,
                    "ClientPIN requires pinProtocol and subCommand");
        }

        // Currently there is only one pinProtocol version
        if (!pinProtocolSupported(pinProtocol)) {
            throw new CtapException(CtapError.PIN_AUTH_INVALID, "Unsupported pin protocol: "
                    + pinProtocol );
        }

        // Invalid subcommand
        if (subCommand < 1 || subCommand > 5) {
            throw new CtapException(CtapError.INVALID_PARAMETER);
        }

        if (pinAuth != null) {
            if (pinAuth.length != 16) {
                throw new CtapException(CtapError.INVALID_LENGTH,
                        "Received pinAuth length is not 16: " + pinAuth.length);
            }
        }

        if (newPinEnc != null) {
            if (newPinEnc.length < 64) {
                throw new CtapException(CtapError.INVALID_LENGTH,
                        "Received newPinEnc length is less than 64: " + newPinEnc.length);
            }
        }

        if (pinHashEnc != null) {
            if (pinHashEnc.length != 16) {
                throw new CtapException(CtapError.INVALID_LENGTH,
                        "Received pinHashEnc length is not 16: " + pinHashEnc.length);
            }
        }

        if ((subCommand == Messages.CLIENT_PIN_SUB_COMMAND_SET_PIN
                || subCommand == Messages.CLIENT_PIN_SUB_COMMAND_CHANGE_PIN
                || subCommand == Messages.CLIENT_PIN_SUB_COMMAND_GET_PIN_TOKEN)
                && keyAgreement == null) {
            throw new CtapException(CtapError.MISSING_PARAMETER,
                    "ClientPIN subCommand requires keyAgreement");
        }
        if (subCommand == Messages.CLIENT_PIN_SUB_COMMAND_SET_PIN
                && (pinAuth == null || newPinEnc == null)) {
            throw new CtapException(CtapError.MISSING_PARAMETER,
                    "setPIN requires pinAuth and newPinEnc");
        }
        if (subCommand == Messages.CLIENT_PIN_SUB_COMMAND_CHANGE_PIN
                && (pinAuth == null || newPinEnc == null || pinHashEnc == null)) {
            throw new CtapException(CtapError.MISSING_PARAMETER,
                    "changePIN requires pinAuth, newPinEnc, and pinHashEnc");
        }
        if (subCommand == Messages.CLIENT_PIN_SUB_COMMAND_GET_PIN_TOKEN
                && pinHashEnc == null) {
            throw new CtapException(CtapError.MISSING_PARAMETER,
                    "getPINToken requires pinHashEnc");
        }
    }

    static boolean pinProtocolSupported(Long pinProtocol) {
        return supportedProtocols.contains(pinProtocol);
    }

    static void pinOptionsWellFormed(Long pinProtocol, byte[] pinAuth) throws CtapException {
        if (pinAuth != null) {
            if (pinAuth.length != 16) {
                throw new CtapException(CtapError.INVALID_LENGTH,
                                        "Received pinAuth length is not 16: " + pinAuth.length);
            }
            if (pinProtocol == null) {
                throw new CtapException(CtapError.MISSING_PARAMETER,
                                        "pinAuth provided but pinProtocol is missing!");
            }
            if (!pinProtocolSupported(pinProtocol)) {
                throw new CtapException(CtapError.PIN_AUTH_INVALID,
                                        "Unsupported pin protocol: " + pinProtocol );
            }
        } else {
            if (pinProtocol != null) {
                throw new CtapException(CtapError.MISSING_PARAMETER,
                                        "pinProtocol provided byt pinAuth missing!");
            }
        }
    }

    @Override
    public ClientPINOptions fromCBor(Map inputMap) {
        pinProtocol = getUnsignedField(inputMap, Messages.CLIENT_PIN_PIN_PROTOCOL);
        subCommand = getUnsignedField(inputMap, Messages.CLIENT_PIN_SUB_COMMAND);
        pinAuth = getByteStringField(inputMap, Messages.CLIENT_PIN_PIN_AUTH);
        newPinEnc = getByteStringField(inputMap, Messages.CLIENT_PIN_NEW_PIN_ENC);
        pinHashEnc = getByteStringField(inputMap, Messages.CLIENT_PIN_PIN_HASH_ENC);

        DataItem keyAgreementItem = inputMap.get(
                new UnsignedInteger(Messages.CLIENT_PIN_KEY_AGREEMENT));
        if (keyAgreementItem != null) {
            if (!(keyAgreementItem instanceof Map)) {
                throw new IllegalArgumentException("keyAgreement must be a COSE_Key map");
            }
            keyAgreement = parseKeyAgreement((Map) keyAgreementItem);
        }

        return this;
    }

    private Long getUnsignedField(Map map, long key) {
        DataItem item = map.get(new UnsignedInteger(key));
        if (item == null) {
            return null;
        }
        if (!(item instanceof UnsignedInteger)) {
            throw new IllegalArgumentException("ClientPIN field " + key + " must be unsigned");
        }
        return ((UnsignedInteger) item).getValue().longValue();
    }

    private byte[] getByteStringField(Map map, long key) {
        DataItem item = map.get(new UnsignedInteger(key));
        if (item == null) {
            return null;
        }
        if (!(item instanceof ByteString)) {
            throw new IllegalArgumentException("ClientPIN field " + key + " must be a byte string");
        }
        return ((ByteString) item).getBytes();
    }

    private PublicKey parseKeyAgreement(Map coseKey) {
        DataItem keyType = coseKey.get(new UnsignedInteger(1));
        DataItem algorithm = coseKey.get(new UnsignedInteger(3));
        DataItem curve = coseKey.get(new NegativeInteger(-1));
        DataItem x = coseKey.get(new NegativeInteger(Messages.COSE_KEY_EC256_X));
        DataItem y = coseKey.get(new NegativeInteger(Messages.COSE_KEY_EC256_Y));

        if (!(keyType instanceof UnsignedInteger)
                || ((UnsignedInteger) keyType).getValue().intValue() != 2
                || !(algorithm instanceof NegativeInteger)
                || !((NegativeInteger) algorithm).getValue()
                        .equals(BigInteger.valueOf(Messages.COSE_ID_ECDH))
                || !(curve instanceof UnsignedInteger)
                || ((UnsignedInteger) curve).getValue().intValue() != 1
                || !(x instanceof ByteString)
                || !(y instanceof ByteString)) {
            throw new IllegalArgumentException("Invalid P-256 ECDH COSE_Key");
        }

        if (agreementParams == null) {
            throw new IllegalArgumentException("EC agreement parameters are unavailable");
        }

        byte[] rawX = ((ByteString) x).getBytes();
        byte[] rawY = ((ByteString) y).getBytes();
        ECPoint point = new ECPoint(new BigInteger(1, rawX), new BigInteger(1, rawY));
        try {
            ECPublicKeySpec publicSpec = new ECPublicKeySpec(point, agreementParams);
            return KeyFactory.getInstance("EC").generatePublic(publicSpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid P-256 keyAgreement point", e);
        }
    }

    public ClientPINOptions fromCBor(Map inputMap, ECParameterSpec agreementParams) {
        this.agreementParams = agreementParams;
        return fromCBor(inputMap);
    }
}
