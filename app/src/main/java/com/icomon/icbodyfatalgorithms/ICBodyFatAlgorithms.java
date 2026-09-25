package com.icomon.icbodyfatalgorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes8.dex */
public class ICBodyFatAlgorithms {
    public static final Integer lock = 0;
    public static boolean isLoaded = false;

    private static native HashMap<String, Object> native_calc(HashMap<String, Object> map);

    private static native HashMap<String, Object> native_calc_other(HashMap<String, Object> map);

    private static native double native_getBMI(double d, int i, int i2, int i3);

    private static native int native_getBMR(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native double native_getBodyFatPercent(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native double native_getBoneMass(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native double native_getMoisturePercent(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native double native_getMusclePercent(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native int native_getPhysicalAge(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native double native_getProtein(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native double native_getSkeletalMuscle(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native double native_getSubcutaneousFatPercent(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static native double native_getVisceralFat(double d, int i, int i2, double d2, double d3, int i3, int i4, int i5);

    private static void load() {
        synchronized (lock) {
            if (!isLoaded) {
                System.loadLibrary("ICBodyFatAlgorithms");
                isLoaded = true;
            }
        }
    }

    public static double getBMI(double d, int i, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getBMI(d, i, iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static double getBodyFatPercent(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getBodyFatPercent(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static double getSubcutaneousFatPercent(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getSubcutaneousFatPercent(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static double getVisceralFat(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getVisceralFat(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static double getMusclePercent(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getMusclePercent(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static int getBMR(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getBMR(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static double getBoneMass(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getBoneMass(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static double getMoisturePercent(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getMoisturePercent(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static int getPhysicalAge(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getPhysicalAge(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static double getProtein(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getProtein(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static double getSkeletalMuscle(double d, int i, int i2, double d2, double d3, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType, ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        load();
        return native_getSkeletalMuscle(d, i, i2, d2, d3, iCBodyFatAlgorithmsSex.getValue(), iCBodyFatAlgorithmsType.getValue(), iCBodyFatAlgorithmsPeopleType.getValue());
    }

    public static ICBodyFatAlgorithmsResult calc(ICBodyFatAlgorithmsParams iCBodyFatAlgorithmsParams) {
        load();
        int value = iCBodyFatAlgorithmsParams.algType.getValue();
        HashMap map = new HashMap();
        map.put("weight", Double.valueOf(iCBodyFatAlgorithmsParams.weight));
        map.put("height", Integer.valueOf(iCBodyFatAlgorithmsParams.height));
        map.put("age", Integer.valueOf(iCBodyFatAlgorithmsParams.age));
        map.put("standard", Integer.valueOf(iCBodyFatAlgorithmsParams.standard.ordinal()));
        map.put("sex", Integer.valueOf(iCBodyFatAlgorithmsParams.sex.getValue()));
        map.put("algType", Integer.valueOf(value));
        map.put("peopleType", Integer.valueOf(iCBodyFatAlgorithmsParams.peopleType.getValue()));
        map.put("imp1", Double.valueOf(iCBodyFatAlgorithmsParams.imp1));
        map.put("imp2", Double.valueOf(iCBodyFatAlgorithmsParams.imp2));
        map.put("imp3", Double.valueOf(iCBodyFatAlgorithmsParams.imp3));
        map.put("imp4", Double.valueOf(iCBodyFatAlgorithmsParams.imp4));
        map.put("imp5", Double.valueOf(iCBodyFatAlgorithmsParams.imp5));
        map.put("enableGirth", Integer.valueOf(iCBodyFatAlgorithmsParams.enableGirth ? 1 : 0));
        if (iCBodyFatAlgorithmsParams.imps == null || iCBodyFatAlgorithmsParams.imps.size() == 0) {
            iCBodyFatAlgorithmsParams.imps = new ArrayList();
            iCBodyFatAlgorithmsParams.imps.add(Double.valueOf(iCBodyFatAlgorithmsParams.imp1));
            iCBodyFatAlgorithmsParams.imps.add(Double.valueOf(iCBodyFatAlgorithmsParams.imp2));
            iCBodyFatAlgorithmsParams.imps.add(Double.valueOf(iCBodyFatAlgorithmsParams.imp3));
            iCBodyFatAlgorithmsParams.imps.add(Double.valueOf(iCBodyFatAlgorithmsParams.imp4));
            iCBodyFatAlgorithmsParams.imps.add(Double.valueOf(iCBodyFatAlgorithmsParams.imp5));
            map.put("impCount", Integer.valueOf(iCBodyFatAlgorithmsParams.imps.size()));
            map.put("imps", iCBodyFatAlgorithmsParams.imps);
        } else {
            map.put("impCount", Integer.valueOf(iCBodyFatAlgorithmsParams.imps.size()));
            map.put("imps", iCBodyFatAlgorithmsParams.imps);
            if (iCBodyFatAlgorithmsParams.imps.size() >= 1) {
                map.put("imp1", iCBodyFatAlgorithmsParams.imps.get(0));
            }
            if (iCBodyFatAlgorithmsParams.imps.size() >= 2) {
                map.put("imp2", iCBodyFatAlgorithmsParams.imps.get(1));
            }
            if (iCBodyFatAlgorithmsParams.imps.size() >= 3) {
                map.put("imp3", iCBodyFatAlgorithmsParams.imps.get(2));
            }
            if (iCBodyFatAlgorithmsParams.imps.size() >= 4) {
                map.put("imp4", iCBodyFatAlgorithmsParams.imps.get(3));
            }
            if (iCBodyFatAlgorithmsParams.imps.size() >= 5) {
                map.put("imp5", iCBodyFatAlgorithmsParams.imps.get(4));
            }
        }
        HashMap<String, Object> mapNative_calc = native_calc(map);
        ICBodyFatAlgorithmsResult iCBodyFatAlgorithmsResult = new ICBodyFatAlgorithmsResult();
        iCBodyFatAlgorithmsResult.bmi = ((Double) mapNative_calc.get("bmi")).doubleValue();
        iCBodyFatAlgorithmsResult.bfr = ((Double) mapNative_calc.get("bfr")).doubleValue();
        iCBodyFatAlgorithmsResult.muscle = ((Double) mapNative_calc.get("muscle")).doubleValue();
        iCBodyFatAlgorithmsResult.subcutfat = ((Double) mapNative_calc.get("subcutfat")).doubleValue();
        iCBodyFatAlgorithmsResult.vfal = ((Double) mapNative_calc.get("vfal")).doubleValue();
        iCBodyFatAlgorithmsResult.bone = ((Double) mapNative_calc.get("bone")).doubleValue();
        iCBodyFatAlgorithmsResult.water = ((Double) mapNative_calc.get("water")).doubleValue();
        iCBodyFatAlgorithmsResult.protein = ((Double) mapNative_calc.get("protein")).doubleValue();
        iCBodyFatAlgorithmsResult.sm = ((Double) mapNative_calc.get("sm")).doubleValue();
        iCBodyFatAlgorithmsResult.bmr = ((Integer) mapNative_calc.get("bmr")).intValue();
        iCBodyFatAlgorithmsResult.age = ((Integer) mapNative_calc.get("age")).intValue();
        iCBodyFatAlgorithmsResult.leftArmMuscle = ((Double) mapNative_calc.get("leftArmMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.leftArmMuscleMass = ((Double) mapNative_calc.get("leftArmMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.leftArmBodyfatPercentage = ((Double) mapNative_calc.get("leftArmBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.leftArmBodyfatMass = ((Double) mapNative_calc.get("leftArmBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.leftLegMuscle = ((Double) mapNative_calc.get("leftLegMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.leftLegMuscleMass = ((Double) mapNative_calc.get("leftLegMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.leftLegBodyfatPercentage = ((Double) mapNative_calc.get("leftLegBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.leftLegBodyfatMass = ((Double) mapNative_calc.get("leftLegBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.rightArmMuscle = ((Double) mapNative_calc.get("rightArmMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.rightArmMuscleMass = ((Double) mapNative_calc.get("rightArmMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.rightArmBodyfatPercentage = ((Double) mapNative_calc.get("rightArmBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.rightArmBodyfatMass = ((Double) mapNative_calc.get("rightArmBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.rightLegMuscle = ((Double) mapNative_calc.get("rightLegMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.rightLegMuscleMass = ((Double) mapNative_calc.get("rightLegMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.rightLegBodyfatPercentage = ((Double) mapNative_calc.get("rightLegBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.rightLegBodyfatMass = ((Double) mapNative_calc.get("rightLegBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.trunkMuscle = ((Double) mapNative_calc.get("trunkMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.trunkMuscleMass = ((Double) mapNative_calc.get("trunkMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.trunkBodyfatPercentage = ((Double) mapNative_calc.get("trunkBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.trunkBodyfatMass = ((Double) mapNative_calc.get("trunkBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.bodyScore = ((Double) mapNative_calc.get("bodyScore")).doubleValue();
        iCBodyFatAlgorithmsResult.bodyType = ((Integer) mapNative_calc.get("bodyType")).intValue();
        iCBodyFatAlgorithmsResult.bfmControl = ((Double) mapNative_calc.get("bfmControl")).doubleValue();
        iCBodyFatAlgorithmsResult.ffmControl = ((Double) mapNative_calc.get("ffmControl")).doubleValue();
        iCBodyFatAlgorithmsResult.weightControl = ((Double) mapNative_calc.get("weightControl")).doubleValue();
        iCBodyFatAlgorithmsResult.weightTarget = ((Double) mapNative_calc.get("weightTarget")).doubleValue();
        iCBodyFatAlgorithmsResult.bfmStandard = ((Double) mapNative_calc.get("bfmStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.bfpStandard = ((Double) mapNative_calc.get("bfpStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.bmiStandard = ((Double) mapNative_calc.get("bmiStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.bmrStandard = ((Integer) mapNative_calc.get("bmrStandard")).intValue();
        iCBodyFatAlgorithmsResult.weightStandard = ((Double) mapNative_calc.get("weightStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.ffmStandard = ((Double) mapNative_calc.get("ffmStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.smmStandard = ((Double) mapNative_calc.get("smmStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.smi = ((Double) mapNative_calc.get("smi")).doubleValue();
        iCBodyFatAlgorithmsResult.obesityDegree = ((Integer) mapNative_calc.get("obesityDegree")).intValue();
        iCBodyFatAlgorithmsResult.whr = ((Double) mapNative_calc.get("whr")).doubleValue();
        iCBodyFatAlgorithmsResult.waist = ((Double) mapNative_calc.get("waist")).doubleValue();
        iCBodyFatAlgorithmsResult.chest = ((Double) mapNative_calc.get("chest")).doubleValue();
        iCBodyFatAlgorithmsResult.hip = ((Double) mapNative_calc.get("hip")).doubleValue();
        iCBodyFatAlgorithmsResult.arm = ((Double) mapNative_calc.get("arm")).doubleValue();
        iCBodyFatAlgorithmsResult.thigh = ((Double) mapNative_calc.get("thigh")).doubleValue();
        iCBodyFatAlgorithmsResult.neck = ((Double) mapNative_calc.get("neck")).doubleValue();
        iCBodyFatAlgorithmsResult.bmiMax = ((Double) mapNative_calc.get("bmi_max")).doubleValue();
        iCBodyFatAlgorithmsResult.bmiMin = ((Double) mapNative_calc.get("bmi_min")).doubleValue();
        iCBodyFatAlgorithmsResult.bfmMax = ((Double) mapNative_calc.get("bfm_max")).doubleValue();
        iCBodyFatAlgorithmsResult.bfmMin = ((Double) mapNative_calc.get("bfm_min")).doubleValue();
        iCBodyFatAlgorithmsResult.bfpMax = ((Double) mapNative_calc.get("bfp_max")).doubleValue();
        iCBodyFatAlgorithmsResult.bfpMin = ((Double) mapNative_calc.get("bfp_min")).doubleValue();
        iCBodyFatAlgorithmsResult.weightMax = ((Double) mapNative_calc.get("weight_max")).doubleValue();
        iCBodyFatAlgorithmsResult.weightMin = ((Double) mapNative_calc.get("weight_min")).doubleValue();
        iCBodyFatAlgorithmsResult.smmMax = ((Double) mapNative_calc.get("smm_max")).doubleValue();
        iCBodyFatAlgorithmsResult.smmMin = ((Double) mapNative_calc.get("smm_min")).doubleValue();
        iCBodyFatAlgorithmsResult.boneMax = ((Double) mapNative_calc.get("bone_max")).doubleValue();
        iCBodyFatAlgorithmsResult.boneMin = ((Double) mapNative_calc.get("bone_min")).doubleValue();
        iCBodyFatAlgorithmsResult.waterMassMax = ((Double) mapNative_calc.get("waterMass_max")).doubleValue();
        iCBodyFatAlgorithmsResult.waterMassMin = ((Double) mapNative_calc.get("waterMass_min")).doubleValue();
        iCBodyFatAlgorithmsResult.proteinMassMax = ((Double) mapNative_calc.get("proteinMass_max")).doubleValue();
        iCBodyFatAlgorithmsResult.proteinMassMin = ((Double) mapNative_calc.get("proteinMass_min")).doubleValue();
        iCBodyFatAlgorithmsResult.muscleMassMax = ((Double) mapNative_calc.get("muscleMass_max")).doubleValue();
        iCBodyFatAlgorithmsResult.muscleMassMin = ((Double) mapNative_calc.get("muscleMass_min")).doubleValue();
        iCBodyFatAlgorithmsResult.bmrMax = ((Integer) mapNative_calc.get("bmr_max")).intValue();
        iCBodyFatAlgorithmsResult.bmrMin = ((Integer) mapNative_calc.get("bmr_min")).intValue();
        return iCBodyFatAlgorithmsResult;
    }

    public static ICBodyFatAlgorithmsResult calc_other(double d, int i, ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex, int i2, ICBodyFatAlgorithmsStandard iCBodyFatAlgorithmsStandard, double d2, Boolean bool) {
        load();
        HashMap map = new HashMap();
        map.put("weight", Double.valueOf(d));
        map.put("height", Integer.valueOf(i));
        map.put("age", Integer.valueOf(i2));
        map.put("standard", Integer.valueOf(iCBodyFatAlgorithmsStandard.ordinal()));
        map.put("sex", Integer.valueOf(iCBodyFatAlgorithmsSex.getValue()));
        map.put("bfr", Double.valueOf(d2));
        map.put("enableGirth", Integer.valueOf(bool.booleanValue() ? 1 : 0));
        HashMap<String, Object> mapNative_calc_other = native_calc_other(map);
        ICBodyFatAlgorithmsResult iCBodyFatAlgorithmsResult = new ICBodyFatAlgorithmsResult();
        iCBodyFatAlgorithmsResult.bmi = ((Double) mapNative_calc_other.get("bmi")).doubleValue();
        iCBodyFatAlgorithmsResult.bfr = ((Double) mapNative_calc_other.get("bfr")).doubleValue();
        iCBodyFatAlgorithmsResult.muscle = ((Double) mapNative_calc_other.get("muscle")).doubleValue();
        iCBodyFatAlgorithmsResult.subcutfat = ((Double) mapNative_calc_other.get("subcutfat")).doubleValue();
        iCBodyFatAlgorithmsResult.vfal = ((Double) mapNative_calc_other.get("vfal")).doubleValue();
        iCBodyFatAlgorithmsResult.bone = ((Double) mapNative_calc_other.get("bone")).doubleValue();
        iCBodyFatAlgorithmsResult.water = ((Double) mapNative_calc_other.get("water")).doubleValue();
        iCBodyFatAlgorithmsResult.protein = ((Double) mapNative_calc_other.get("protein")).doubleValue();
        iCBodyFatAlgorithmsResult.sm = ((Double) mapNative_calc_other.get("sm")).doubleValue();
        iCBodyFatAlgorithmsResult.bmr = ((Integer) mapNative_calc_other.get("bmr")).intValue();
        iCBodyFatAlgorithmsResult.age = ((Integer) mapNative_calc_other.get("age")).intValue();
        iCBodyFatAlgorithmsResult.leftArmMuscle = ((Double) mapNative_calc_other.get("leftArmMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.leftArmMuscleMass = ((Double) mapNative_calc_other.get("leftArmMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.leftArmBodyfatPercentage = ((Double) mapNative_calc_other.get("leftArmBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.leftArmBodyfatMass = ((Double) mapNative_calc_other.get("leftArmBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.leftLegMuscle = ((Double) mapNative_calc_other.get("leftLegMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.leftLegMuscleMass = ((Double) mapNative_calc_other.get("leftLegMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.leftLegBodyfatPercentage = ((Double) mapNative_calc_other.get("leftLegBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.leftLegBodyfatMass = ((Double) mapNative_calc_other.get("leftLegBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.rightArmMuscle = ((Double) mapNative_calc_other.get("rightArmMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.rightArmMuscleMass = ((Double) mapNative_calc_other.get("rightArmMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.rightArmBodyfatPercentage = ((Double) mapNative_calc_other.get("rightArmBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.rightArmBodyfatMass = ((Double) mapNative_calc_other.get("rightArmBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.rightLegMuscle = ((Double) mapNative_calc_other.get("rightLegMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.rightLegMuscleMass = ((Double) mapNative_calc_other.get("rightLegMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.rightLegBodyfatPercentage = ((Double) mapNative_calc_other.get("rightLegBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.rightLegBodyfatMass = ((Double) mapNative_calc_other.get("rightLegBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.trunkMuscle = ((Double) mapNative_calc_other.get("trunkMuscle")).doubleValue();
        iCBodyFatAlgorithmsResult.trunkMuscleMass = ((Double) mapNative_calc_other.get("trunkMuscleMass")).doubleValue();
        iCBodyFatAlgorithmsResult.trunkBodyfatPercentage = ((Double) mapNative_calc_other.get("trunkBodyfatPercentage")).doubleValue();
        iCBodyFatAlgorithmsResult.trunkBodyfatMass = ((Double) mapNative_calc_other.get("trunkBodyfatMass")).doubleValue();
        iCBodyFatAlgorithmsResult.bodyScore = ((Double) mapNative_calc_other.get("bodyScore")).doubleValue();
        iCBodyFatAlgorithmsResult.bodyType = ((Integer) mapNative_calc_other.get("bodyType")).intValue();
        iCBodyFatAlgorithmsResult.bfmControl = ((Double) mapNative_calc_other.get("bfmControl")).doubleValue();
        iCBodyFatAlgorithmsResult.ffmControl = ((Double) mapNative_calc_other.get("ffmControl")).doubleValue();
        iCBodyFatAlgorithmsResult.weightControl = ((Double) mapNative_calc_other.get("weightControl")).doubleValue();
        iCBodyFatAlgorithmsResult.weightTarget = ((Double) mapNative_calc_other.get("weightTarget")).doubleValue();
        iCBodyFatAlgorithmsResult.bfmStandard = ((Double) mapNative_calc_other.get("bfmStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.bfpStandard = ((Double) mapNative_calc_other.get("bfpStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.bmiStandard = ((Double) mapNative_calc_other.get("bmiStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.bmrStandard = ((Integer) mapNative_calc_other.get("bmrStandard")).intValue();
        iCBodyFatAlgorithmsResult.weightStandard = ((Double) mapNative_calc_other.get("weightStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.ffmStandard = ((Double) mapNative_calc_other.get("ffmStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.smmStandard = ((Double) mapNative_calc_other.get("smmStandard")).doubleValue();
        iCBodyFatAlgorithmsResult.smi = ((Double) mapNative_calc_other.get("smi")).doubleValue();
        iCBodyFatAlgorithmsResult.obesityDegree = ((Integer) mapNative_calc_other.get("obesityDegree")).intValue();
        iCBodyFatAlgorithmsResult.whr = ((Double) mapNative_calc_other.get("whr")).doubleValue();
        iCBodyFatAlgorithmsResult.armBalance = ((Integer) mapNative_calc_other.get("armBalance")).intValue();
        iCBodyFatAlgorithmsResult.legBalance = ((Integer) mapNative_calc_other.get("legBalance")).intValue();
        iCBodyFatAlgorithmsResult.armAndLegBalance = ((Integer) mapNative_calc_other.get("armAndLegBalance")).intValue();
        iCBodyFatAlgorithmsResult.waist = ((Double) mapNative_calc_other.get("waist")).doubleValue();
        iCBodyFatAlgorithmsResult.chest = ((Double) mapNative_calc_other.get("chest")).doubleValue();
        iCBodyFatAlgorithmsResult.hip = ((Double) mapNative_calc_other.get("hip")).doubleValue();
        iCBodyFatAlgorithmsResult.arm = ((Double) mapNative_calc_other.get("arm")).doubleValue();
        iCBodyFatAlgorithmsResult.thigh = ((Double) mapNative_calc_other.get("thigh")).doubleValue();
        iCBodyFatAlgorithmsResult.neck = ((Double) mapNative_calc_other.get("neck")).doubleValue();
        iCBodyFatAlgorithmsResult.bmiMax = ((Double) mapNative_calc_other.get("bmi_max")).doubleValue();
        iCBodyFatAlgorithmsResult.bmiMin = ((Double) mapNative_calc_other.get("bmi_min")).doubleValue();
        iCBodyFatAlgorithmsResult.bfmMax = ((Double) mapNative_calc_other.get("bfm_max")).doubleValue();
        iCBodyFatAlgorithmsResult.bfmMin = ((Double) mapNative_calc_other.get("bfm_min")).doubleValue();
        iCBodyFatAlgorithmsResult.bfpMax = ((Double) mapNative_calc_other.get("bfp_max")).doubleValue();
        iCBodyFatAlgorithmsResult.bfpMin = ((Double) mapNative_calc_other.get("bfp_min")).doubleValue();
        iCBodyFatAlgorithmsResult.weightMax = ((Double) mapNative_calc_other.get("weight_max")).doubleValue();
        iCBodyFatAlgorithmsResult.weightMin = ((Double) mapNative_calc_other.get("weight_min")).doubleValue();
        iCBodyFatAlgorithmsResult.smmMax = ((Double) mapNative_calc_other.get("smm_max")).doubleValue();
        iCBodyFatAlgorithmsResult.smmMin = ((Double) mapNative_calc_other.get("smm_min")).doubleValue();
        iCBodyFatAlgorithmsResult.boneMax = ((Double) mapNative_calc_other.get("bone_max")).doubleValue();
        iCBodyFatAlgorithmsResult.boneMin = ((Double) mapNative_calc_other.get("bone_min")).doubleValue();
        iCBodyFatAlgorithmsResult.waterMassMax = ((Double) mapNative_calc_other.get("waterMass_max")).doubleValue();
        iCBodyFatAlgorithmsResult.waterMassMin = ((Double) mapNative_calc_other.get("waterMass_min")).doubleValue();
        iCBodyFatAlgorithmsResult.proteinMassMax = ((Double) mapNative_calc_other.get("proteinMass_max")).doubleValue();
        iCBodyFatAlgorithmsResult.proteinMassMin = ((Double) mapNative_calc_other.get("proteinMass_min")).doubleValue();
        iCBodyFatAlgorithmsResult.muscleMassMax = ((Double) mapNative_calc_other.get("muscleMass_max")).doubleValue();
        iCBodyFatAlgorithmsResult.muscleMassMin = ((Double) mapNative_calc_other.get("muscleMass_min")).doubleValue();
        iCBodyFatAlgorithmsResult.bmrMax = ((Integer) mapNative_calc_other.get("bmr_max")).intValue();
        iCBodyFatAlgorithmsResult.bmrMin = ((Integer) mapNative_calc_other.get("bmr_min")).intValue();
        return iCBodyFatAlgorithmsResult;
    }

    public static String version() {
        return "1.3.0_build_1351_86e59a68_20250623093038";
    }

    public static List<String> getKeysFromMap(Map<String, Object> map) {
        ArrayList arrayList = new ArrayList();
        Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            arrayList.add(it.next().getKey());
        }
        return arrayList;
    }

    public static int getObjectType(Object obj) {
        if (obj instanceof Integer) {
            return 1;
        }
        if (obj instanceof Double) {
            return 2;
        }
        if (obj instanceof String) {
            return 3;
        }
        if (obj instanceof List) {
            return 4;
        }
        if (obj instanceof Map) {
            return 5;
        }
        if (obj instanceof byte[]) {
            return 6;
        }
        if (obj instanceof Long) {
            return 7;
        }
        if (obj instanceof Float) {
            return 8;
        }
        if (obj instanceof Byte) {
            return 9;
        }
        return obj instanceof Short ? 10 : 0;
    }
}
