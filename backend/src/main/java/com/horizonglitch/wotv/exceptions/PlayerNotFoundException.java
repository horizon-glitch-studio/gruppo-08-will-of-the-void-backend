package com.horizonglitch.wotv.exceptions;

public class PlayerNotFoundException extends RuntimeException {
    public PlayerNotFoundException() { super("Authenticated player was not found"); }
}
