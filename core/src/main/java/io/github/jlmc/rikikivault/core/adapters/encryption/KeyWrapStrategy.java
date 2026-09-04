package io.github.jlmc.rikikivault.core.adapters.encryption;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Adapter-internal collaborator: how a single file's symmetric key gets wrapped for one
 * recipient. Deliberately NOT a port — {@code EncryptionPort} is the only crypto abstraction
 * the application layer is allowed to know about; this interface exists so
 * {@link JceHybridEncryptionAdapter} is open to a different wrap algorithm (e.g. RSA-OAEP)
 * without any change above the adapter (Open/Closed).
 */
interface KeyWrapStrategy {

    byte[] wrap(byte[] keyToWrap, PublicKey recipientPublicKey);

    byte[] unwrap(byte[] wrappedKeyBlob, PrivateKey recipientPrivateKey);
}
