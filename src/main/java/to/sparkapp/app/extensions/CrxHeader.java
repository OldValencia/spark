package to.sparkapp.app.extensions;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * The signed header of a Chrome extension package. Only the extension's store ID is read; the
 * package is installed unpacked, so signatures are not verified here.
 */
record CrxHeader(String storeId) {
    private static final int MAGIC = 0x43723234;
    private static final long MAX_HEADER_BYTES = 16L * 1024 * 1024;
    private static final int SIGNED_HEADER_DATA_FIELD = 10_000;
    private static final int CRX_ID_FIELD = 1;
    private static final int ID_BYTES = 16;
    private static final int WIRE_VARINT = 0;
    private static final int WIRE_FIXED64 = 1;
    private static final int WIRE_BYTES = 2;
    private static final int WIRE_FIXED32 = 5;

    /** Reads the header when the stream is a CRX; otherwise leaves it untouched and returns null. */
    static CrxHeader read(DataInputStream input) throws IOException {
        input.mark(4);
        if (input.readInt() != MAGIC) {
            input.reset();
            return null;
        }

        var version = readLittleEndian(input);
        return switch (version) {
            case 3 -> new CrxHeader(crx3StoreId(input.readNBytes(headerSize(readLittleEndian(input)))));
            case 2 -> crx2(input);
            default -> throw new IOException("Unsupported CRX format");
        };
    }

    private static CrxHeader crx2(DataInputStream input) throws IOException {
        var keyLength = headerSize(readLittleEndian(input));
        var signatureLength = headerSize(readLittleEndian(input));
        var key = input.readNBytes(keyLength);
        input.skipNBytes(signatureLength);

        return new CrxHeader(idFromKey(key));
    }

    /** CRX3 stores the ID in SignedData.crx_id inside CrxFileHeader.signed_header_data. */
    private static String crx3StoreId(byte[] header) throws IOException {
        var signedData = field(header, SIGNED_HEADER_DATA_FIELD);
        var crxId = signedData == null ? null : field(signedData, CRX_ID_FIELD);
        if (crxId == null || crxId.length != ID_BYTES) {
            return null;
        }

        return letters(crxId);
    }

    /** Returns the first length-delimited protobuf field with the given number, or null. */
    private static byte[] field(byte[] message, int number) throws IOException {
        var input = new DataInputStream(new ByteArrayInputStream(message));
        while (input.available() > 0) {
            var tag = varint(input);
            var wireType = (int) (tag & 7);
            var length = switch (wireType) {
                case WIRE_VARINT -> {
                    varint(input);
                    yield 0L;
                }
                case WIRE_FIXED64 -> 8L;
                case WIRE_BYTES -> varint(input);
                case WIRE_FIXED32 -> 4L;
                default -> throw new IOException("The CRX header is malformed");
            };
            if (tag >>> 3 == number && wireType == WIRE_BYTES) {
                return input.readNBytes(headerSize(length));
            }
            input.skipNBytes(length);
        }

        return null;
    }

    private static long varint(DataInputStream input) throws IOException {
        var value = 0L;
        for (var shift = 0; shift < 64; shift += 7) {
            var next = input.readUnsignedByte();
            value |= (long) (next & 0x7F) << shift;
            if ((next & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("The CRX header is malformed");
    }

    private static String idFromKey(byte[] key) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(key);
            return letters(Arrays.copyOf(digest, ID_BYTES));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Chrome writes IDs as hexadecimal digits mapped to the letters a to p. */
    private static String letters(byte[] bytes) {
        var id = new StringBuilder(bytes.length * 2);
        for (var value : bytes) {
            id.append((char) ('a' + ((value >> 4) & 0xF)));
            id.append((char) ('a' + (value & 0xF)));
        }

        return id.toString();
    }

    private static int readLittleEndian(DataInputStream input) throws IOException {
        return Integer.reverseBytes(input.readInt());
    }

    private static int headerSize(long size) throws IOException {
        var unsigned = size < 0 ? Integer.toUnsignedLong((int) size) : size;
        if (unsigned > MAX_HEADER_BYTES) {
            throw new IOException("The CRX header is too large");
        }

        return (int) unsigned;
    }
}
