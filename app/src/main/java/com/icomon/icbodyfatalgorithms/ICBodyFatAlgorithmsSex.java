package com.icomon.icbodyfatalgorithms;

/* JADX INFO: loaded from: classes8.dex */
public enum ICBodyFatAlgorithmsSex {
    Male(1),
    Female(2);

    private final int value;

    ICBodyFatAlgorithmsSex(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }

    public static ICBodyFatAlgorithmsSex valueOf(int i) {
        if (i == 2) {
            return Female;
        }
        return Male;
    }
}
