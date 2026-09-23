package com.ftxeven.aircore.model;

// shared by anything that persists a world location
public record Position(String world, double x, double y, double z, float yaw, float pitch) {}