package com.icomon.icbodyfatalgorithms;

/* JADX INFO: loaded from: classes8.dex */
public enum ICBodyFatAlgorithmsType {
    ICBodyFatAlgorithmsTypeWLA02(1),
    ICBodyFatAlgorithmsTypeWLA03(2),
    ICBodyFatAlgorithmsTypeWLA04(3),
    ICBodyFatAlgorithmsTypeWLA07(6),
    ICBodyFatAlgorithmsTypeWLA25(24),
    ICBodyFatAlgorithmsTypeWLA34(33),
    ICBodyFatAlgorithmsTypeWLA35(34),
    ICBodyFatAlgorithmsTypeWLA36(35),
    ICBodyFatAlgorithmsTypeWLA37(36),
    ICBodyFatAlgorithmsTypeRev(99);

    private final int value;

    ICBodyFatAlgorithmsType(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }

    public static ICBodyFatAlgorithmsType valueOf(int i) {
        if (i == 1) {
            return ICBodyFatAlgorithmsTypeWLA02;
        }
        if (i == 2) {
            return ICBodyFatAlgorithmsTypeWLA03;
        }
        if (i == 3) {
            return ICBodyFatAlgorithmsTypeWLA04;
        }
        if (i == 6) {
            return ICBodyFatAlgorithmsTypeWLA07;
        }
        if (i == 24) {
            return ICBodyFatAlgorithmsTypeWLA25;
        }
        if (i != 99) {
            switch (i) {
                case 33:
                    return ICBodyFatAlgorithmsTypeWLA34;
                case 34:
                    return ICBodyFatAlgorithmsTypeWLA35;
                case 35:
                    return ICBodyFatAlgorithmsTypeWLA36;
                case 36:
                    return ICBodyFatAlgorithmsTypeWLA37;
                default:
                    return null;
            }
        }
        return ICBodyFatAlgorithmsTypeRev;
    }
}
