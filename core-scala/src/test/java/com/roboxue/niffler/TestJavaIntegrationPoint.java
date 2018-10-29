package com.roboxue.niffler;

import java.util.ArrayList;
import java.util.List;

public class TestJavaIntegrationPoint {
    public static List<String> duplicateArrayJavaImpl(int times, String word) {
        ArrayList<String> r = new ArrayList<>();
        for (int i = 0; i < times; i ++) {
            r.add(word);
        }
        return r;
    }

    public List<String> duplicateArrayJavaImpl2(int times, String word) {
        ArrayList<String> r = new ArrayList<>();
        for (int i = 0; i < times; i ++) {
            r.add(word);
        }
        return r;
    }
}
