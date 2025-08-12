package com.lightningfirefly.engine.core.command;

public interface CommandQueue {
    void enqueue(EngineCommand engineCommand, CommandPayload payload);
}
