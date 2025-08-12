package com.lightningfirefly.engine.core.command;

import java.io.Serializable;
import java.util.Map;

public interface CommandPayload extends Serializable {
    Map<String, Object> getPayload();
}
