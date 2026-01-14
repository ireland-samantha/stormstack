package ca.samanthaireland.engine.util;

import java.util.UUID;

public class IdGeneratorV2 {
    public static long newId() {
        return Math.abs(UUID.randomUUID().getLeastSignificantBits());
    }
}
