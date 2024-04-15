package com.dissertation.tmwt;

import java.util.UUID;

public class CharacteristicInfo {
    public UUID uuid;
    public String name;

    public CharacteristicInfo(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}