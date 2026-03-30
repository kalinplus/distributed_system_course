package com.course.ecommerce.config;

public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceNames> CONTEXT = new ThreadLocal<>();

    public static void setDataSource(DataSourceNames name) {
        CONTEXT.set(name);
    }

    public static DataSourceNames getDataSource() {
        DataSourceNames name = CONTEXT.get();
        return name == null ? DataSourceNames.MASTER : name;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
