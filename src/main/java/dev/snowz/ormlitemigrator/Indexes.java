package dev.snowz.ormlitemigrator;

import java.util.ArrayList;
import java.util.List;

public class Indexes {

    private final String name;
    private List<String> columns;
    private Boolean isUnique;
    private String key_name;

    public Indexes(final String name) {
        this.name = name;
        columns = new ArrayList<>();
    }

    public void setColumns(final List<String> columns) {
        this.columns = columns;
    }

    public void setUnique(final Boolean unique) {
        isUnique = unique;
    }

    public String getName() {
        return name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public Boolean getUnique() {
        return isUnique;
    }

    @Override
    public String toString() {
        return "Indexes{" +
            "name='" + name + '\'' +
            ", columns=" + columns +
            ", isUnique=" + isUnique +
            '}';
    }
}
