package com.icomon.icbodyfatalgorithms;

/* JADX INFO: loaded from: classes8.dex */
public enum ICBodyFatAlgorithmsPeopleType {
    ICBodyFatAlgorithmsPeopleTypeNormal(0),
    ICBodyFatAlgorithmsPeopleTypeSportsMan(1);

    private final int value;

    ICBodyFatAlgorithmsPeopleType(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }

    public static ICBodyFatAlgorithmsPeopleType valueOf(int i) {
        if (i == 0) {
            return ICBodyFatAlgorithmsPeopleTypeNormal;
        }
        if (i != 1) {
            return null;
        }
        return ICBodyFatAlgorithmsPeopleTypeSportsMan;
    }
}
