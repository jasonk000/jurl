package com.anthonynsimon.url;

import com.anthonynsimon.url.exceptions.InvalidHexException;
import com.anthonynsimon.url.exceptions.MalformedURLException;

import java.io.UnsupportedEncodingException;

/**
 * PercentEscaper is a utility class that handles the escaping and unescaping of characters in URLs.
 * It escapes character based on the context (part of the URL) that is being dealt with.
 *
 * Supports UTF-8 escaping and unescaping.
 */
class PercentEscaper {

    /**
     * Reserved characters, allowed in certain parts of the URL. Must be escaped in most cases.
     */
    private static final char[] reservedChars = {'!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=', ':', '[', ']', '<', '>', '"'};

    /**
     * Unreserved characters do not need to be escaped.
     */
    private static final char[] unreservedChars = {'-', '_', '.', '~'};

    /**
     * Byte masks to aid in the decoding of UTF-8 byte arrays.
     */
    private static final short[] utf8Masks = new short[]{0b00000000, 0b11000000, 0b11100000, 0b11110000};


    /**
     * Returns true if escaping is required based on the character and encode zone provided.
     */
    private static boolean shouldEscape(char c, EncodeZone zone) {
        if ('A' <= c && c <= 'Z' || 'a' <= c && c <= 'z' || '0' <= c && c <= '9') {
            return false;
        }

        if (zone == EncodeZone.HOST || zone == EncodeZone.PATH) {
            if (c == '%') {
                return true;
            }
            for (char reserved : reservedChars) {
                if (reserved == c) {
                    return false;
                }
            }
        }

        for (char unreserved : unreservedChars) {
            if (unreserved == c) {
                return false;
            }
        }

        for (char reserved : new char[]{'$', '&', '+', ',', '/', ':', ';', '=', '?', '@'}) {
            if (reserved == c) {
                switch (zone) {
                    case PATH:
                        return c == '?';
                    case CREDENTIALS:
                        return c == '@' || c == '/' || c == '?' || c == ':';
                    case QUERY:
                        return true;
                    case FRAGMENT:
                        return false;
                }
            }
        }

        return true;
    }

    /**
     * Returns a percent-escaped string. Each character will be evaluated in case it needs to be escaped
     * based on the provided EncodeZone.
     */
    public static String escape(String str, EncodeZone zone) {
        byte[] bytes;
        try {
            bytes = str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        int i = 0;
        String result = "";
        while (i < bytes.length) {
            int readBytes = 0;
            for (short mask : utf8Masks) {
                if ((bytes[i] & mask) == mask) {
                    readBytes++;
                } else {
                    break;
                }
            }
            for (int j = 0; j < readBytes; j++) {
                char c = (char) bytes[i];
                if (shouldEscape(c, zone)) {
                    result += "%" + "0123456789ABCDEF".charAt((bytes[i] & 0xFF) >> 4) + "0123456789ABCDEF".charAt((bytes[i] & 0xFF) & 15);
                } else {
                    result += c;
                }
                i++;
            }
        }

        return result;
    }

    /**
     * Returns an unescaped string.
     *
     * @throws MalformedURLException if an invalid escape sequence is found.
     */
    public static String unescape(String str) throws MalformedURLException {
        char[] chars = str.toCharArray();
        String result = "";
        int len = str.length();
        int i = 0;
        while (i < chars.length) {
            char c = chars[i];
            if (c != '%') {
                result += c;
                i++;
            } else {
                if (i + 2 >= len) {
                    throw new MalformedURLException("invalid escape sequence");
                }
                byte code;
                try {
                    code = unhex(str.substring(i + 1, i + 3).toCharArray());
                } catch (InvalidHexException e) {
                    throw new MalformedURLException(e.getMessage());
                }
                int readBytes = 0;
                for (short mask : utf8Masks) {
                    if ((code & mask) == mask) {
                        readBytes++;
                    } else {
                        break;
                    }
                }
                byte[] buffer = new byte[readBytes];
                for (int j = 0; j < readBytes; j++) {
                    try {
                        buffer[j] = unhex(str.substring(i + 1, i + 3).toCharArray());
                    } catch (InvalidHexException e) {
                        throw new MalformedURLException(e.getMessage());
                    }
                    i += 3;
                }
                result += new String(buffer);
            }
        }
        return result;
    }

    /**
     * Returns a byte representation of a parsed array of hex chars.
     *
     * @throws InvalidHexException if the provided array of hex characters is invalid.
     */
    private static byte unhex(char[] hex) throws InvalidHexException {
        int result = 0;
        for (int i = 0; i < hex.length; i++) {
            char c = hex[hex.length - i - 1];
            int index = -1;
            if ('0' <= c && c <= '9') {
                index = c - '0';
            } else if ('a' <= c && c <= 'f') {
                index = c - 'a' + 10;
            } else if ('A' <= c && c <= 'F') {
                index = c - 'A' + 10;
            }
            if (index < 0 || index >= 16) {
                throw new InvalidHexException("not a valid hex char: " + c);
            }
            result += index * pow(16, i);
        }
        return (byte) result;
    }

    private static int pow(int base, int exp) {
        int result = 1;
        while (exp > 0) {
            result *= base;
            exp--;
        }
        return result;
    }

    /**
     * EncodeZone is used to distinguish between the parts of the url when encoding/decoding.
     */
    protected enum EncodeZone {
        CREDENTIALS,
        HOST,
        PATH,
        QUERY,
        FRAGMENT,
        ENCODE_ZONE,
    }
}