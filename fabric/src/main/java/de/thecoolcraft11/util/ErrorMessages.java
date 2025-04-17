package de.thecoolcraft11.util;


import java.util.HashMap;
import java.util.Map;

public class ErrorMessages {
    private static final Map<String, String> ERROR_KEYS = new HashMap<>();

    static {
        ERROR_KEYS.put("CONNECTION_REFUSED", "message.screenshot_uploader.error.reason.CONNECTION_REFUSED");
        ERROR_KEYS.put("TIMEOUT", "message.screenshot_uploader.error.reason.TIMEOUT");
        ERROR_KEYS.put("HOST_UNREACHABLE", "message.screenshot_uploader.error.reason.HOST_UNREACHABLE");
        ERROR_KEYS.put("BAD_REQUEST", "message.screenshot_uploader.error.reason.BAD_REQUEST");
        ERROR_KEYS.put("UNAUTHORIZED", "message.screenshot_uploader.error.reason.UNAUTHORIZED");
        ERROR_KEYS.put("FORBIDDEN", "message.screenshot_uploader.error.reason.FORBIDDEN");
        ERROR_KEYS.put("NOT_FOUND", "message.screenshot_uploader.error.reason.NOT_FOUND");
        ERROR_KEYS.put("INTERNAL_SERVER_ERROR", "message.screenshot_uploader.error.reason.INTERNAL_SERVER_ERROR");
        ERROR_KEYS.put("SERVICE_UNAVAILABLE", "message.screenshot_uploader.error.reason.SERVICE_UNAVAILABLE");
        ERROR_KEYS.put("SOCKETTIMEOUTEXCEPTION", "message.screenshot_uploader.error.reason.SOCKETTIMEOUTEXCEPTION");
        ERROR_KEYS.put("UNKNOWNHOSTEXCEPTION", "message.screenshot_uploader.error.reason.UNKNOWNHOSTEXCEPTION");
        ERROR_KEYS.put("EOFEXCEPTION", "message.screenshot_uploader.error.reason.EOFEXCEPTION");
        ERROR_KEYS.put("SSLHANDSHAKEEXCEPTION", "message.screenshot_uploader.error.reason.SSLHANDSHAKEEXCEPTION");
        ERROR_KEYS.put("PROTOCOL_EXCEPTION", "message.screenshot_uploader.error.reason.PROTOCOL_EXCEPTION");
        ERROR_KEYS.put("FILE_NOT_FOUND_EXCEPTION", "message.screenshot_uploader.error.reason.FILE_NOT_FOUND_EXCEPTION");
        ERROR_KEYS.put("IOEXCEPTION", "message.screenshot_uploader.error.reason.IOEXCEPTION");
        ERROR_KEYS.put("BIND_EXCEPTION", "message.screenshot_uploader.error.reason.BIND_EXCEPTION");
        ERROR_KEYS.put("HTTP_CLIENT_TIMEOUT_EXCEPTION", "message.screenshot_uploader.error.reason.HTTP_CLIENT_TIMEOUT_EXCEPTION");
        ERROR_KEYS.put("OUTOFMEMORYERROR", "message.screenshot_uploader.error.reason.OUTOFMEMORYERROR");
        ERROR_KEYS.put("INVALID_PARAMETER", "message.screenshot_uploader.error.reason.INVALID_PARAMETER");
        ERROR_KEYS.put("MALFORMEDURLEXCEPTION", "message.screenshot_uploader.error.reason.MALFORMEDURLEXCEPTION");
        ERROR_KEYS.put("ERROR_WRITING_TO_SERVER", "message.screenshot_uploader.error.reason.ERROR_WRITING_TO_SERVER");
    }

    public static String getErrorDescription(String errorMessage) {
        String normalizedMessage = errorMessage.trim().replace(" ", "_").toUpperCase();

        for (String errorCode : ERROR_KEYS.keySet()) {
            if (normalizedMessage.contains(errorCode)) {
                return ERROR_KEYS.get(errorCode);
            }
        }

        return "message.screenshot_uploader.error.reason.DEFAULT";
    }
}