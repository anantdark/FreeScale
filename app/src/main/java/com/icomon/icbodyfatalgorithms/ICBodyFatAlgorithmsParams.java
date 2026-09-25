package com.icomon.icbodyfatalgorithms;

import java.util.List;

/* JADX INFO: loaded from: classes8.dex */
public class ICBodyFatAlgorithmsParams {
    public int age;
    public ICBodyFatAlgorithmsType algType;
    public int height;
    public double imp1;
    public double imp2;
    public double imp3;
    public double imp4;
    public double imp5;
    public List<Double> imps;
    public double weight;
    public ICBodyFatAlgorithmsSex sex = ICBodyFatAlgorithmsSex.Male;
    public ICBodyFatAlgorithmsStandard standard = ICBodyFatAlgorithmsStandard.ICBodyFatAlgorithmsStandard1;
    public boolean enableGirth = false;
    public ICBodyFatAlgorithmsPeopleType peopleType = ICBodyFatAlgorithmsPeopleType.ICBodyFatAlgorithmsPeopleTypeNormal;

    public double getWeight() {
        return this.weight;
    }

    public void setWeight(double d) {
        this.weight = d;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int i) {
        this.height = i;
    }

    public ICBodyFatAlgorithmsSex getSex() {
        return this.sex;
    }

    public void setSex(ICBodyFatAlgorithmsSex iCBodyFatAlgorithmsSex) {
        this.sex = iCBodyFatAlgorithmsSex;
    }

    public int getAge() {
        return this.age;
    }

    public void setAge(int i) {
        this.age = i;
    }

    public ICBodyFatAlgorithmsType getAlgType() {
        return this.algType;
    }

    public void setAlgType(ICBodyFatAlgorithmsType iCBodyFatAlgorithmsType) {
        this.algType = iCBodyFatAlgorithmsType;
    }

    public ICBodyFatAlgorithmsPeopleType getPeopleType() {
        return this.peopleType;
    }

    public void setPeopleType(ICBodyFatAlgorithmsPeopleType iCBodyFatAlgorithmsPeopleType) {
        this.peopleType = iCBodyFatAlgorithmsPeopleType;
    }

    public double getImp1() {
        return this.imp1;
    }

    public void setImp1(double d) {
        this.imp1 = d;
    }

    public double getImp2() {
        return this.imp2;
    }

    public void setImp2(double d) {
        this.imp2 = d;
    }

    public double getImp3() {
        return this.imp3;
    }

    public void setImp3(double d) {
        this.imp3 = d;
    }

    public double getImp4() {
        return this.imp4;
    }

    public void setImp4(double d) {
        this.imp4 = d;
    }

    public double getImp5() {
        return this.imp5;
    }

    public void setImp5(double d) {
        this.imp5 = d;
    }

    public ICBodyFatAlgorithmsStandard getStandard() {
        return this.standard;
    }

    public void setStandard(ICBodyFatAlgorithmsStandard iCBodyFatAlgorithmsStandard) {
        this.standard = iCBodyFatAlgorithmsStandard;
    }

    public boolean isEnableGirth() {
        return this.enableGirth;
    }

    public void setEnableGirth(boolean z) {
        this.enableGirth = z;
    }

    public List<Double> getImps() {
        return this.imps;
    }

    public void setImps(List<Double> list) {
        this.imps = list;
    }
}
