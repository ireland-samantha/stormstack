package com.lightningfirefly.engine.api.resource.adapter;

import java.io.IOException;

public class Demo {
    static void main() throws IOException {
        ResourceAdapter resourceAdapter = new ResourceAdapter.HttpResourceAdapter("http://localhost:8080");
        resourceAdapter.listResources();
    }
}
