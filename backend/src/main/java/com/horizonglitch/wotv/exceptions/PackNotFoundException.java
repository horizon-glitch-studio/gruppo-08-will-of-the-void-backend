package com.horizonglitch.wotv.exceptions;

public class PackNotFoundException extends RuntimeException {
    public PackNotFoundException(int packId) { super("Pack " + packId + " was not found"); }
}
