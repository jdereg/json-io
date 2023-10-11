package com.cedarsoftware.util.io;

public enum ExternalEnum {
    ONE(1), TWO(2), THREE(3);

    ExternalEnum(int num) {
        this.num = num;
    }

    private int num;

    public int priority;

    public int getNum() {
        return this.num;
    }

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
