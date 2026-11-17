package com.horizonglitch.wotv.exceptions;

public class PackUnavailableException extends RuntimeException {
    public PackUnavailableException(int packId) { super("Pack " + packId + " is not available"); }
}
