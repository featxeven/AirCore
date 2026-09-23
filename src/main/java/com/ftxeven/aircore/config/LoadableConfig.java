package com.ftxeven.aircore.config;

public interface LoadableConfig {

    Runnable prepare();

    String fileName();
}